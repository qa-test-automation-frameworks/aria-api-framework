package com.aria.framework.reporting;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Registers sanitized per-test diagnostic logs with each Allure test result.
 */
public final class AllureTestDiagnosticsExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        AllureTestDiagnostics.start(context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        AllureTestDiagnostics.attachAndClear(context.getExecutionException());
    }
}
