package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;
import org.idempiere.cli.service.InteractivePromptService;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "init",
        description = "Scaffold a new iDempiere plugin with selected features",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Runnable {

    @Parameters(index = "0", description = "Plugin ID (e.g., org.mycompany.myplugin)")
    String pluginId;

    @Option(names = "--with-callout", description = "Include a callout stub")
    boolean withCallout;

    @Option(names = "--with-event-handler", description = "Include an event handler stub")
    boolean withEventHandler;

    @Option(names = "--with-process", description = "Include a process stub")
    boolean withProcess;

    @Option(names = "--with-zk-form", description = "Include a ZK form stub")
    boolean withZkForm;

    @Option(names = "--with-report", description = "Include a report stub")
    boolean withReport;

    @Option(names = "--with-window-validator", description = "Include a window validator stub")
    boolean withWindowValidator;

    @Option(names = "--with-rest-extension", description = "Include a REST API resource extension stub")
    boolean withRestExtension;

    @Option(names = "--with-facts-validator", description = "Include a facts validator (accounting) stub")
    boolean withFactsValidator;

    @Option(names = "--version", description = "Plugin version (default: 1.0.0.qualifier)", defaultValue = "1.0.0.qualifier")
    String version;

    @Option(names = "--vendor", description = "Plugin vendor name", defaultValue = "")
    String vendor;

    @Option(names = "--idempiere-version", description = "Target iDempiere version (default: 12)", defaultValue = "12")
    int idempiereVersion;

    @Option(names = "--interactive", negatable = true, description = "Enable interactive mode (default: auto-detect)", defaultValue = "false")
    boolean interactive;

    @Inject
    ScaffoldService scaffoldService;

    @Inject
    InteractivePromptService promptService;

    @Override
    public void run() {
        boolean hasExplicitFlags = withCallout || withEventHandler || withProcess || withZkForm
                || withReport || withWindowValidator || withRestExtension || withFactsValidator;

        if (!hasExplicitFlags && (interactive || System.console() != null)) {
            runInteractive();
        } else {
            runWithFlags();
        }
    }

    private void runInteractive() {
        System.out.println();
        System.out.println("iDempiere Plugin Setup");
        System.out.println("==========================================");
        System.out.println();

        version = promptService.prompt("Plugin version", version);
        vendor = promptService.prompt("Vendor name", vendor.isEmpty() ? null : vendor);
        String idVer = promptService.prompt("Target iDempiere version", String.valueOf(idempiereVersion));
        try {
            idempiereVersion = Integer.parseInt(idVer);
        } catch (NumberFormatException ignored) {
        }

        System.out.println();
        System.out.println("Select components to include:");
        System.out.println();

        if (promptService.confirm("  Include callout?")) withCallout = true;
        if (promptService.confirm("  Include event handler?")) withEventHandler = true;
        if (promptService.confirm("  Include process?")) withProcess = true;
        if (promptService.confirm("  Include ZK form?")) withZkForm = true;
        if (promptService.confirm("  Include report?")) withReport = true;
        if (promptService.confirm("  Include window validator?")) withWindowValidator = true;
        if (promptService.confirm("  Include REST extension?")) withRestExtension = true;
        if (promptService.confirm("  Include facts validator?")) withFactsValidator = true;

        System.out.println();
        runWithFlags();
    }

    private void runWithFlags() {
        PluginDescriptor descriptor = new PluginDescriptor(pluginId);
        descriptor.setVersion(version);
        descriptor.setVendor(vendor);
        descriptor.setPlatformVersion(PlatformVersion.of(idempiereVersion));

        if (withCallout) descriptor.addFeature("callout");
        if (withEventHandler) descriptor.addFeature("event-handler");
        if (withProcess) descriptor.addFeature("process");
        if (withZkForm) descriptor.addFeature("zk-form");
        if (withReport) descriptor.addFeature("report");
        if (withWindowValidator) descriptor.addFeature("window-validator");
        if (withRestExtension) descriptor.addFeature("rest-extension");
        if (withFactsValidator) descriptor.addFeature("facts-validator");

        scaffoldService.createPlugin(descriptor);
    }
}
