package org.idempiere.cli.service.check;

import java.nio.file.Path;

/**
 * Strategy interface for plugin structure validation checks.
 *
 * <p>Implementations validate specific aspects of an iDempiere plugin
 * (MANIFEST.MF, pom.xml, build.properties, etc.).
 */
public interface PluginCheck {

    /**
     * Returns the check name for display.
     */
    String checkName();

    /**
     * Performs the plugin validation check.
     *
     * @param pluginDir the plugin directory to validate
     * @return the check result with status and message
     */
    CheckResult check(Path pluginDir);
}
