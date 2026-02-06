package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates OSGI-INF directory presence in an iDempiere plugin.
 */
@ApplicationScoped
public class OsgiInfCheck implements PluginCheck {

    @Override
    public String checkName() {
        return "OSGI-INF";
    }

    @Override
    public CheckResult check(Path pluginDir) {
        Path osgiInf = pluginDir.resolve("OSGI-INF");
        if (Files.exists(osgiInf) && Files.isDirectory(osgiInf)) {
            return new CheckResult(checkName(), CheckResult.Status.OK, "Directory exists");
        }
        return new CheckResult(checkName(), CheckResult.Status.WARN, "Directory not found");
    }
}
