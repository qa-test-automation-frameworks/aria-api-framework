package com.aria.framework.base;

import com.aria.framework.reporting.AllureTestDiagnostics;
import io.qameta.allure.Step;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for live Restful Booker tests that require configured API credentials.
 */
public abstract class LiveRestfulBookerTest extends BaseTest {

    @BeforeEach
    @Step("Validate Restful Booker live credentials")
    void requireRestfulBookerCredentials() {
        AllureTestDiagnostics.step("Validate Restful Booker live credentials", CONFIG::requireBookerCredentials);
    }
}
