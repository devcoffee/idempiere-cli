package org.idempiere.cli;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.IVersionProvider;

/**
 * Provides the application version from Quarkus config (sourced from Maven pom.xml).
 * Used by Picocli's --version flag and the upgrade command.
 */
@ApplicationScoped
public class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[]{ getApplicationVersion() };
    }

    public static String getApplicationVersion() {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.application.version", String.class)
                .orElse("dev");
    }
}
