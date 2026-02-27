package org.idempiere.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import org.idempiere.cli.service.PluginInfoService;
import org.idempiere.cli.service.PluginInfoService.BuildArtifact;
import org.idempiere.cli.service.PluginInfoService.ExtensionRegistration;
import org.idempiere.cli.service.PluginInfoService.ModuleSummary;
import org.idempiere.cli.service.PluginInfoService.MultiModuleInfo;
import org.idempiere.cli.service.PluginInfoService.PluginInfo;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.util.ExitCodes;
import org.idempiere.cli.util.JsonOutput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Displays plugin metadata and detected components.
 */
@Command(
        name = "info",
        description = "Show plugin metadata and components",
        mixinStandardHelpOptions = true
)
public class InfoCommand implements Callable<Integer> {

    @Option(names = {"--dir"}, description = "Plugin directory or multi-module root (default: current directory)", defaultValue = ".")
    String dir;

    @Option(names = {"--json"}, description = "Output results as JSON")
    boolean json;

    @Option(names = {"--verbose"}, description = "Show detailed OSGi/build/component sections")
    boolean verbose;

    @Inject
    PluginInfoService pluginInfoService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public Integer call() {
        Path inputDir = Path.of(dir).toAbsolutePath().normalize();

        if (projectDetector.isIdempierePlugin(inputDir)) {
            PluginInfo info = pluginInfoService.getInfo(inputDir);
            if (json) {
                return printPluginJson(info, inputDir);
            }
            pluginInfoService.printInfo(inputDir, verbose);
            return ExitCodes.SUCCESS;
        }

        if (projectDetector.isMultiModuleRoot(inputDir)) {
            MultiModuleInfo info = pluginInfoService.getMultiModuleInfo(inputDir);
            if (json) {
                return printMultiModuleJson(info, inputDir);
            }
            pluginInfoService.printMultiModuleInfo(inputDir, verbose);
            return ExitCodes.SUCCESS;
        }

        if (json) {
            return JsonOutput.printError("NOT_PLUGIN",
                    "Not an iDempiere plugin or multi-module root in " + inputDir,
                    ExitCodes.STATE_ERROR);
        }

        System.err.println("Error: Not an iDempiere plugin or multi-module root in " + inputDir);
        System.err.println("Make sure you are inside a plugin directory or use --dir to specify one.");
        return ExitCodes.STATE_ERROR;
    }

    private Integer printPluginJson(PluginInfo info, Path inputDir) {
        if (info == null) {
            return JsonOutput.printError("INFO_READ_FAILED",
                    "Failed to read plugin info from " + inputDir,
                    ExitCodes.IO_ERROR);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("projectType", "plugin");
            root.put("pluginId", info.pluginId());
            root.put("version", info.version());
            root.put("vendor", info.vendor());
            if (info.fragmentHost() != null) {
                root.put("fragmentHost", info.fragmentHost());
            }
            if (info.javaSe() != null) {
                root.put("javaSe", info.javaSe());
            }
            if (info.idempiereVersion() != null) {
                root.put("idempiereVersion", info.idempiereVersion());
            }

            ArrayNode required = root.putArray("requiredBundles");
            info.requiredBundles().forEach(required::add);

            ArrayNode imports = root.putArray("importPackages");
            info.importPackages().forEach(imports::add);

            ArrayNode exports = root.putArray("exportPackages");
            info.exportPackages().forEach(exports::add);

            ArrayNode ds = root.putArray("dsComponents");
            info.dsComponents().forEach(ds::add);

            ArrayNode components = root.putArray("components");
            info.components().forEach(components::add);

            ArrayNode extensions = root.putArray("extensions");
            for (ExtensionRegistration ext : info.extensions()) {
                ObjectNode node = extensions.addObject();
                node.put("type", ext.type());
                node.put("point", ext.point());
                if (ext.className() != null) {
                    node.put("className", ext.className());
                }
                if (ext.target() != null) {
                    node.put("target", ext.target());
                }
            }

            BuildArtifact artifact = info.buildArtifact();
            if (artifact != null) {
                ObjectNode build = root.putObject("buildArtifact");
                build.put("path", artifact.path());
                build.put("sizeBytes", artifact.sizeBytes());
                build.put("modifiedAt", artifact.modifiedAt().toString());
            }

            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            return ExitCodes.SUCCESS;
        } catch (Exception e) {
            return JsonOutput.printError("JSON_SERIALIZATION", "Failed to serialize JSON", ExitCodes.IO_ERROR);
        }
    }

    private Integer printMultiModuleJson(MultiModuleInfo info, Path inputDir) {
        if (info == null) {
            return JsonOutput.printError("INFO_READ_FAILED",
                    "Failed to read multi-module info from " + inputDir,
                    ExitCodes.IO_ERROR);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("projectType", "multi-module");
            root.put("projectName", info.projectName());
            if (info.idempiereVersion() != null) {
                root.put("idempiereVersion", info.idempiereVersion());
            }
            if (info.javaSe() != null) {
                root.put("javaSe", info.javaSe());
            }
            if (info.baseModule() != null) {
                root.put("baseModule", info.baseModule());
            }

            ArrayNode modules = root.putArray("modules");
            for (ModuleSummary module : info.modules()) {
                ObjectNode node = modules.addObject();
                node.put("name", module.name());
                node.put("type", module.type());
                node.put("pluginModule", module.pluginModule());
            }

            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            return ExitCodes.SUCCESS;
        } catch (Exception e) {
            return JsonOutput.printError("JSON_SERIALIZATION", "Failed to serialize JSON", ExitCodes.IO_ERROR);
        }
    }
}
