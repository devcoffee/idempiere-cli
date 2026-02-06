package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates pom.xml file in an iDempiere plugin.
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
            List<String> issues = new ArrayList<>();

            if (!content.contains("tycho-maven-plugin")) {
                issues.add("tycho-maven-plugin not found");
            }
            if (!content.contains("<packaging>bundle</packaging>")) {
                issues.add("packaging is not 'bundle'");
            }

            if (issues.isEmpty()) {
                return new CheckResult(checkName(), CheckResult.Status.OK, "Tycho plugin and bundle packaging present");
            } else {
                return new CheckResult(checkName(), CheckResult.Status.FAIL, String.join(", ", issues));
            }
        } catch (IOException e) {
            return new CheckResult(checkName(), CheckResult.Status.FAIL, "Error reading: " + e.getMessage());
        }
    }
}
