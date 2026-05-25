package com.aria.framework.reporting;

import io.qameta.allure.Allure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runtime Allure diagnostics for test-method level steps and sanitized method logs.
 */
public final class AllureTestDiagnostics {

    private static final ThreadLocal<TestMethodLog> CURRENT_LOG = ThreadLocal.withInitial(TestMethodLog::new);

    private AllureTestDiagnostics() {
    }

    static void start(String displayName) {
        TestMethodLog log = new TestMethodLog();
        log.displayName = displayName;
        CURRENT_LOG.set(log);
        recordSystem("START " + displayName);
    }

    static void attachAndClear(Optional<Throwable> failure) {
        try {
            failure.ifPresentOrElse(
                throwable -> {
                    recordSystem("RESULT failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                    recordStackTrace(throwable);
                },
                () -> recordSystem("RESULT completed")
            );
            TestMethodLog log = CURRENT_LOG.get();
            if (log.hasDiagnostics) {
                Allure.addAttachment(
                    "ARIA test method log",
                    "text/plain",
                    RedactionPolicy.sanitize(log.render()),
                    ".txt"
                );
            }
        } finally {
            CURRENT_LOG.remove();
        }
    }

    public static void log(String message) {
        record("INFO " + message);
    }

    public static void log(String template, Object... args) {
        record("INFO " + format(template, args));
    }

    public static void step(String name) {
        record("STEP " + name);
        Allure.step(name);
    }

    public static void step(String name, DiagnosticStep action) {
        record("STEP " + name);
        Allure.step(name, () -> action.run());
    }

    public static <T> T step(String name, DiagnosticSupplier<T> action) {
        record("STEP " + name);
        return Allure.step(name, () -> action.get());
    }

    @FunctionalInterface
    public interface DiagnosticStep {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface DiagnosticSupplier<T> {
        T get() throws Exception;
    }

    private static void record(String message) {
        TestMethodLog log = CURRENT_LOG.get();
        log.hasDiagnostics = true;
        log.entries.add(Instant.now() + " " + message);
    }

    private static void recordSystem(String message) {
        CURRENT_LOG.get().entries.add(Instant.now() + " " + message);
    }

    private static void recordStackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        record(writer.toString());
    }

    private static String format(String template, Object... args) {
        StringBuilder formatted = new StringBuilder();
        int argIndex = 0;
        for (int index = 0; index < template.length(); index++) {
            if (index + 1 < template.length()
                && template.charAt(index) == '{'
                && template.charAt(index + 1) == '}'
                && argIndex < args.length) {
                formatted.append(String.valueOf(args[argIndex++]));
                index++;
            } else {
                formatted.append(template.charAt(index));
            }
        }
        while (argIndex < args.length) {
            formatted.append(' ').append(String.valueOf(args[argIndex++]));
        }
        return formatted.toString();
    }

    private static final class TestMethodLog {
        private String displayName = "unknown test";
        private boolean hasDiagnostics;
        private final List<String> entries = new ArrayList<>();

        private String render() {
            StringBuilder output = new StringBuilder()
                .append("Test: ")
                .append(displayName)
                .append(System.lineSeparator())
                .append(System.lineSeparator());
            entries.forEach(entry -> output.append(entry).append(System.lineSeparator()));
            return output.toString();
        }
    }
}
