package com.aria.framework.utils;

import com.aria.framework.clients.BaseApiClient;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.exceptions.RetryDeadlineExceededException;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real loopback transport: no mocked HTTP responses or timeout filters. */
class RetryTransportDeadlineTest {
    @BeforeAll
    static void warmRuntimeBeforeMeasuringNetworkDeadline() throws Exception {
        try (HttpFixture server = new HttpFixture(Mode.HEALTHY)) {
            assertThat(new ProbeClient(server.url(), 10_000).get().statusCode()).isEqualTo(200);
            assertThat(server.peerClosed.await(2, TimeUnit.SECONDS)).isTrue();
            server.assertHealthyFixture();
        }
    }

    @Test
    void stalledHeadersTerminateAndCloseActualConnection() throws Exception {
        verifyDeadline(Mode.STALLED_HEADERS, false);
    }

    @Test
    void continuouslyTricklingBodyCannotExtendDeadline() throws Exception {
        verifyDeadline(Mode.TRICKLING_BODY, true);
    }

    @Test
    void healthyBufferedBodyRemainsReadableAfterTransportCleanup() throws Exception {
        try (HttpFixture server = new HttpFixture(Mode.HEALTHY)) {
            Response response = new ProbeClient(server.url(), 2_000).get();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("{\"ok\":true}");
            assertThat(server.requests).hasValue(1);
            assertThat(server.peerClosed.await(2, TimeUnit.SECONDS)).isTrue();
            server.assertHealthyFixture();
        }
    }

    private static void verifyDeadline(Mode mode, boolean prebuilt) throws Exception {
        try (HttpFixture server = new HttpFixture(mode)) {
            ProbeClient client = new ProbeClient(server.url(), 600);
            RequestSpecification spec = prebuilt ? client.specification() : null;
            AtomicReference<Thread> worker = new AtomicReference<>();
            AtomicInteger dispatchedTimeout = new AtomicInteger();
            client.addFilter((request, response, context) -> {
                worker.set(Thread.currentThread());
                return context.next(request, response);
            });
            // A prebuilt spec needs its capturing filter before dispatch too.
            if (spec != null) spec.filter((request, response, context) -> {
                worker.set(Thread.currentThread());
                dispatchedTimeout.set((Integer) request.getConfig().getHttpClientConfig().params().get("http.socket.timeout"));
                return context.next(request, response);
            });
            long start = System.nanoTime();
            assertThatThrownBy(() -> { if (prebuilt) client.get(spec); else client.get(); })
                .isInstanceOf(RetryDeadlineExceededException.class);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(elapsed).isBetween(450L, 2_000L);
            assertThat(server.requests).hasValue(1);
            if (prebuilt) assertThat(dispatchedTimeout.get()).isBetween(1, 600);
            assertThat(server.peerClosed.await(2, TimeUnit.SECONDS)).isTrue();
            RetryDeadlineTest.awaitWorkerExit(worker.get());
            server.assertHealthyFixture();
        }
    }

    private enum Mode { HEALTHY, STALLED_HEADERS, TRICKLING_BODY }

    private static final class ProbeClient extends BaseApiClient {
        private final String url;

        private ProbeClient(String url, long deadlineMs) {
            super(new FrameworkConfig("test", url, url, "", "", "", 5, 1_000, 3, 1, 1_000, 0, deadlineMs));
            this.url = url;
        }

        private RequestSpecification specification() { return getRequestSpec(url); }
        private Response get() { return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given().spec(specification()).get("/")); }
        private Response get(RequestSpecification spec) {
            return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given().spec(spec).get("/"));
        }
    }

    private static final class HttpFixture implements AutoCloseable {
        private final ServerSocket listener;
        private final Thread thread;
        private volatile Socket accepted;
        private final AtomicInteger requests = new AtomicInteger();
        private final CountDownLatch peerClosed = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private HttpFixture(Mode mode) throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            thread = Thread.ofPlatform().daemon(true).name("aria-deadline-fixture").start(() -> serve(mode));
        }

        private String url() { return "http://127.0.0.1:" + listener.getLocalPort(); }

        private void serve(Mode mode) {
            try (Socket socket = listener.accept()) {
                accepted = socket;
                socket.setSoTimeout(4_000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) { /* consume headers */ }
                requests.incrementAndGet();
                if (mode == Mode.STALLED_HEADERS) {
                    if (reader.read() == -1) peerClosed.countDown();
                    else throw new IOException("Unexpected extra request data");
                } else if (mode == Mode.HEALTHY) {
                    byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                        + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                    if (reader.read() == -1) peerClosed.countDown();
                    else throw new IOException("Unexpected extra request data");
                } else {
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
                    for (int index = 0; index < 1000; index++) {
                        try {
                            socket.getOutputStream().write('x');
                            socket.getOutputStream().flush();
                        } catch (IOException closed) {
                            peerClosed.countDown();
                            return;
                        }
                        Thread.sleep(40);
                    }
                    throw new IOException("Trickling body unexpectedly finished without cancellation");
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        }

        private void assertHealthyFixture() { assertThat(failure.get()).isNull(); }

        @Override
        public void close() throws Exception {
            listener.close();
            if (accepted != null) accepted.close();
            thread.interrupt();
            thread.join(2_000);
            assertThat(thread.isAlive()).isFalse();
        }
    }
}
