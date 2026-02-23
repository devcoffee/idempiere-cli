package org.idempiere.cli.plugins.experimental.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.AiStatusService;

import java.util.Optional;

/**
 * Experimental AI provider validation used by core commands through AiStatusService.
 */
@ApplicationScoped
@IfBuildProperty(name = "idempiere.experimental.ai.enabled", stringValue = "true")
public class ExperimentalAiStatusService implements AiStatusService {

    @Inject
    AiClientFactory aiClientFactory;

    @Override
    public ValidationResult validateConfiguredProvider() {
        Optional<AiClient> client = aiClientFactory.getClient();
        if (client.isEmpty()) {
            return ValidationResult.unavailable("client not found");
        }

        AiResponse validation = client.get().validate();
        if (validation.success()) {
            return ValidationResult.ok();
        }
        return ValidationResult.failure(validation.error());
    }
}
