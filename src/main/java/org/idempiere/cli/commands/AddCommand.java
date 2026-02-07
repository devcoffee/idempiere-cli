package org.idempiere.cli.commands;

import org.idempiere.cli.commands.add.AddBaseTestCommand;
import org.idempiere.cli.commands.add.AddCalloutCommand;
import org.idempiere.cli.commands.add.AddEventHandlerCommand;
import org.idempiere.cli.commands.add.AddFactsValidatorCommand;
import org.idempiere.cli.commands.add.AddFeatureModuleCommand;
import org.idempiere.cli.commands.add.AddFragmentModuleCommand;
import org.idempiere.cli.commands.add.AddJasperReportCommand;
import org.idempiere.cli.commands.add.AddListboxGroupCommand;
import org.idempiere.cli.commands.add.AddMavenWrapperCommand;
import org.idempiere.cli.commands.add.AddModelCommand;
import org.idempiere.cli.commands.add.AddPluginModuleCommand;
import org.idempiere.cli.commands.add.AddProcessCommand;
import org.idempiere.cli.commands.add.AddProcessMappedCommand;
import org.idempiere.cli.commands.add.AddReportCommand;
import org.idempiere.cli.commands.add.AddRestExtensionCommand;
import org.idempiere.cli.commands.add.AddTestCommand;
import org.idempiere.cli.commands.add.AddWListboxEditorCommand;
import org.idempiere.cli.commands.add.AddWindowValidatorCommand;
import org.idempiere.cli.commands.add.AddZkFormCommand;
import org.idempiere.cli.commands.add.AddZkFormZulCommand;
import picocli.CommandLine.Command;

/**
 * Adds new modules or components to an existing iDempiere project.
 *
 * <h2>Module Commands (Multi-module projects)</h2>
 * <ul>
 *   <li><b>plugin</b> - Add a new plugin module to multi-module project</li>
 *   <li><b>fragment</b> - Add a fragment module (extends another bundle)</li>
 *   <li><b>feature</b> - Add a feature module (groups plugins for installation)</li>
 * </ul>
 *
 * <h2>Component Commands (Add code to plugins)</h2>
 * <ul>
 *   <li><b>callout</b> - Column-level business logic with @Callout annotation</li>
 *   <li><b>process</b> - Server-side batch process with own factory</li>
 *   <li><b>process-mapped</b> - Process using global MappedProcessFactory (2Pack compatible)</li>
 *   <li><b>event-handler</b> - Model lifecycle hooks (BeforeNew, AfterChange, etc.)</li>
 *   <li><b>zk-form</b> - Programmatic ZK form extending ADForm</li>
 *   <li><b>zk-form-zul</b> - Declarative ZUL-based form with controller</li>
 *   <li><b>listbox-group</b> - Form with grouped/collapsible Listbox</li>
 *   <li><b>wlistbox-editor</b> - Form with custom WListbox column editors</li>
 *   <li><b>report</b> - Basic report process</li>
 *   <li><b>jasper-report</b> - Jasper report with Activator and .jrxml</li>
 *   <li><b>window-validator</b> - Window-level event validation</li>
 *   <li><b>rest-extension</b> - REST API endpoint extension</li>
 *   <li><b>facts-validator</b> - Accounting facts validation</li>
 *   <li><b>model</b> - Generate I_/X_/M_ model classes from database</li>
 *   <li><b>test</b> - JUnit 5 test for specific component</li>
 *   <li><b>base-test</b> - Base test class using AbstractTestCase</li>
 * </ul>
 *
 * <h2>Shared Components</h2>
 * <p>Some component types share infrastructure (CalloutFactory, Activator).
 * The CLI detects existing shared components and reuses them.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Add a new plugin module to multi-module project
 * idempiere-cli add plugin org.example.myproject.reports
 *
 * # Add a fragment module
 * idempiere-cli add fragment --host=org.adempiere.ui.zk
 *
 * # Add a feature module
 * idempiere-cli add feature
 *
 * # Add components to a plugin
 * idempiere-cli add callout MyCallout --to=/path/to/plugin
 * idempiere-cli add process MyProcess
 * </pre>
 *
 * @see org.idempiere.cli.service.ScaffoldService#addComponent(String, String, Path, String)
 * @see org.idempiere.cli.service.ScaffoldService#addPluginModuleToProject(Path, String, PluginDescriptor)
 */
@Command(
        name = "add",
        description = "Add a new component or module to an existing plugin/project",
        mixinStandardHelpOptions = true,
        subcommands = {
                // Module commands (for multi-module projects)
                AddPluginModuleCommand.class,
                AddFragmentModuleCommand.class,
                AddFeatureModuleCommand.class,
                // Component commands (for adding code to plugins)
                AddCalloutCommand.class,
                AddEventHandlerCommand.class,
                AddProcessCommand.class,
                AddProcessMappedCommand.class,
                AddZkFormCommand.class,
                AddZkFormZulCommand.class,
                AddListboxGroupCommand.class,
                AddWListboxEditorCommand.class,
                AddReportCommand.class,
                AddJasperReportCommand.class,
                AddWindowValidatorCommand.class,
                AddRestExtensionCommand.class,
                AddFactsValidatorCommand.class,
                AddModelCommand.class,
                AddTestCommand.class,
                AddBaseTestCommand.class,
                // Tooling commands
                AddMavenWrapperCommand.class
        }
)
public class AddCommand {
}
