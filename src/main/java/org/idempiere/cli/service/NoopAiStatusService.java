package org.idempiere.cli.service;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Core fallback when experimental AI validation is not enabled in the build.
 */
@ApplicationScoped
@DefaultBean
public class NoopAiStatusService implements AiStatusService {

    @Override
    public ValidationResult validateConfiguredProvider() {
        return ValidationResult.unsupported("experimental build (-Pexp)");
    }
}
