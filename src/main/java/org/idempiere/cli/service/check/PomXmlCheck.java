package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates pom.xml file in an iDempiere plugin.
 *
 * <p>Accepts both packagings used in the wild:
 * <ul>
 *   <li>{@code eclipse-plugin} — the idiomatic Tycho packaging. In a multi-module
 *       project the {@code tycho-maven-plugin} declaration lives in the parent pom,
 *       so we cannot require it in this submodule's pom.</li>
 *   <li>{@code bundle} — the legacy {@code maven-bundle-plugin} packaging.</li>
 * </ul>
 */
@ApplicationScoped
public class PomXmlCheck implements PluginCheck {

    @Override
    public String checkName() {
        return "pom.xml";
    }

    @Override
    public CheckResult check(Path pluginDir) {
        Path pom = pluginDir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return new CheckResult(checkName(), CheckResult.Status.FAIL, "Not found");
        }

        try {
            String content = Files.readString(pom);

            if (content.contains("<packaging>eclipse-plugin</packaging>")) {
                return new CheckResult(checkName(), CheckResult.Status.OK, "Tycho 'eclipse-plugin' packaging");
            }
            if (content.contains("<packaging>bundle</packaging>")) {
                return new CheckResult(checkName(), CheckResult.Status.OK, "OSGi 'bundle' packaging");
            }

            return new CheckResult(checkName(), CheckResult.Status.FAIL,
                    "packaging must be 'eclipse-plugin' (Tycho) or 'bundle' (maven-bundle-plugin)");
        } catch (IOException e) {
            return new CheckResult(checkName(), CheckResult.Status.FAIL, "Error reading: " + e.getMessage());
        }
    }
}
