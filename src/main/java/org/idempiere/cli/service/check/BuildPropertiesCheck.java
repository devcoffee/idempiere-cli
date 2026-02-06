package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates build.properties file in an iDempiere plugin.
 */
@ApplicationScoped
public class BuildPropertiesCheck implements PluginCheck {

    @Override
    public String checkName() {
        return "build.properties";
    }

    @Override
    public CheckResult check(Path pluginDir) {
        Path buildProps = pluginDir.resolve("build.properties");
        if (!Files.exists(buildProps)) {
            return new CheckResult(checkName(), CheckResult.Status.FAIL, "Not found");
        }

        try {
            String content = Files.readString(buildProps);
            List<String> missing = new ArrayList<>();

            if (!content.contains("source..")) missing.add("source..");
            if (!content.contains("output..")) missing.add("output..");
            if (!content.contains("bin.includes")) missing.add("bin.includes");

            if (missing.isEmpty()) {
                return new CheckResult(checkName(), CheckResult.Status.OK, "All required entries present");
            } else {
                return new CheckResult(checkName(), CheckResult.Status.WARN, "Missing: " + String.join(", ", missing));
            }
        } catch (IOException e) {
            return new CheckResult(checkName(), CheckResult.Status.FAIL, "Error reading: " + e.getMessage());
        }
    }
}
