package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates MANIFEST.MF file in an iDempiere plugin.
 */
@ApplicationScoped
public class ManifestCheck implements PluginCheck {

    @Override
    public String checkName() {
        return "MANIFEST.MF";
    }

    @Override
    public CheckResult check(Path pluginDir) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) {
            return new CheckResult(checkName(), CheckResult.Status.FAIL, "Not found at META-INF/MANIFEST.MF");
        }

        try {
            String content = Files.readString(manifest);
            List<String> missing = new ArrayList<>();

            if (!content.contains("Bundle-SymbolicName")) missing.add("Bundle-SymbolicName");
            if (!content.contains("Bundle-Version")) missing.add("Bundle-Version");
            if (!content.contains("Bundle-RequiredExecutionEnvironment")) missing.add("Bundle-RequiredExecutionEnvironment");
            if (!content.contains("Require-Bundle") && !content.contains("Fragment-Host")) {
                missing.add("Require-Bundle or Fragment-Host");
            }

            if (missing.isEmpty()) {
                return new CheckResult(checkName(), CheckResult.Status.OK, "All required headers present");
            } else {
                return new CheckResult(checkName(), CheckResult.Status.FAIL, "Missing: " + String.join(", ", missing));
            }
        } catch (IOException e) {
            return new CheckResult(checkName(), CheckResult.Status.FAIL, "Error reading: " + e.getMessage());
        }
    }
}
