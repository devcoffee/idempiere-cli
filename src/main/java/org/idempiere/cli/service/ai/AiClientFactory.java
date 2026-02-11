package org.idempiere.cli.service.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;
import org.idempiere.cli.service.CliConfigService;

import java.util.Optional;

/**
 * Resolves the correct AiClient based on configuration.
 * Returns empty when AI is not configured (provider=none or not set).
 */
@ApplicationScoped
public class AiClientFactory {

    @Inject
    Instance<AiClient> clients;

    @Inject
    CliConfigService configService;

    /**
     * Returns the configured AiClient, or empty if AI is disabled.
     */
    public Optional<AiClient> getClient() {
        CliConfig config = configService.loadConfig();
        CliConfig.AiConfig aiConfig = config.getAi();

        if (!aiConfig.isEnabled()) {
            return Optional.empty();
        }

        String provider = aiConfig.getProvider();

        return clients.stream()
                .filter(c -> c.providerName().equals(provider))
                .filter(AiClient::isConfigured)
                .findFirst();
    }
}
