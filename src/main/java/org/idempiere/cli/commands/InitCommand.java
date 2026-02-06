package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;
import org.idempiere.cli.service.InteractivePromptService;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Scaffolds a new iDempiere plugin project with selected components.
 *
 * <p>Creates a complete OSGi bundle structure including:
 * <ul>
 *   <li>pom.xml with Tycho/Maven configuration</li>
 *   <li>META-INF/MANIFEST.MF with OSGi metadata and dependencies</li>
 *   <li>OSGI-INF/ for declarative services</li>
 *   <li>Selected component templates (callout, process, form, etc.)</li>
 * </ul>
 *
 * <h2>Interactive Mode</h2>
 * <p>When run in a terminal without explicit flags, prompts for component selection.
 * Use {@code --no-interactive} to disable prompts in scripts.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Interactive mode
 * idempiere-cli init org.mycompany.myplugin
 *
 * # Non-interactive with specific components
 * idempiere-cli init org.mycompany.myplugin --with-callout --with-process
 * </pre>
 *
 * @see ScaffoldService#createPlugin(PluginDescriptor)
 */
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

    @Option(names = "--with-process-mapped", description = "Include a process using MappedProcessFactory (recommended for 2Pack support)")
    boolean withProcessMapped;

    @Option(names = "--with-test", description = "Include JUnit 5 test infrastructure with AbstractTestCase")
    boolean withTest;

    @Option(names = "--with-zk-form-zul", description = "Include a ZUL-based form with separate .zul file and Controller")
    boolean withZkFormZul;

    @Option(names = "--with-listbox-group", description = "Include a form with grouped Listbox (GroupsModel)")
    boolean withListboxGroup;

    @Option(names = "--with-wlistbox-editor", description = "Include a form with custom WListbox column editors")
    boolean withWListboxEditor;

    @Option(names = "--with-jasper-report", description = "Include Jasper report with Activator and sample .jrxml")
    boolean withJasperReport;

    @Option(names = "--version", description = "Plugin version (default: 1.0.0.qualifier)", defaultValue = "1.0.0.qualifier")
    String version;

    @Option(names = "--vendor", description = "Plugin vendor name", defaultValue = "")
    String vendor;

    @Option(names = "--idempiere-version", description = "Target iDempiere version (default: 12)", defaultValue = "12")
    int idempiereVersion;

    @Option(names = "--interactive", negatable = true, description = "Enable interactive mode (default: auto-detect)")
    Boolean interactive;

    @Inject
    ScaffoldService scaffoldService;

    @Inject
    InteractivePromptService promptService;

    @Override
    public void run() {
        boolean hasExplicitFlags = withCallout || withEventHandler || withProcess || withZkForm
                || withReport || withWindowValidator || withRestExtension || withFactsValidator
                || withProcessMapped || withTest || withZkFormZul || withListboxGroup
                || withWListboxEditor || withJasperReport;

        // Determine interactive mode:
        // - If user explicitly passed --interactive: use interactive mode
        // - If user explicitly passed --no-interactive: skip interactive mode
        // - If neither (interactive == null): auto-detect based on flags and console
        boolean useInteractive;
        if (interactive != null) {
            useInteractive = interactive;
        } else {
            useInteractive = !hasExplicitFlags && System.console() != null;
        }

        if (useInteractive) {
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
        if (promptService.confirm("  Include process (with own factory)?")) withProcess = true;
        if (promptService.confirm("  Include process (with MappedFactory for 2Pack)?")) withProcessMapped = true;
        if (promptService.confirm("  Include ZK form (programmatic)?")) withZkForm = true;
        if (promptService.confirm("  Include ZK form (ZUL-based)?")) withZkFormZul = true;
        if (promptService.confirm("  Include Listbox with groups?")) withListboxGroup = true;
        if (promptService.confirm("  Include WListbox with custom editors?")) withWListboxEditor = true;
        if (promptService.confirm("  Include report (basic)?")) withReport = true;
        if (promptService.confirm("  Include Jasper report (with Activator)?")) withJasperReport = true;
        if (promptService.confirm("  Include window validator?")) withWindowValidator = true;
        if (promptService.confirm("  Include REST extension?")) withRestExtension = true;
        if (promptService.confirm("  Include facts validator?")) withFactsValidator = true;
        if (promptService.confirm("  Include unit tests?")) withTest = true;

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
        if (withProcessMapped) descriptor.addFeature("process-mapped");
        if (withZkForm) descriptor.addFeature("zk-form");
        if (withZkFormZul) descriptor.addFeature("zk-form-zul");
        if (withListboxGroup) descriptor.addFeature("listbox-group");
        if (withWListboxEditor) descriptor.addFeature("wlistbox-editor");
        if (withReport) descriptor.addFeature("report");
        if (withJasperReport) descriptor.addFeature("jasper-report");
        if (withWindowValidator) descriptor.addFeature("window-validator");
        if (withRestExtension) descriptor.addFeature("rest-extension");
        if (withFactsValidator) descriptor.addFeature("facts-validator");
        if (withTest) descriptor.addFeature("test");

        scaffoldService.createPlugin(descriptor);
    }
}
