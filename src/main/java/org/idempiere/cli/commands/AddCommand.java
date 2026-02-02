package org.idempiere.cli.commands;

import org.idempiere.cli.commands.add.AddCalloutCommand;
import org.idempiere.cli.commands.add.AddEventHandlerCommand;
import org.idempiere.cli.commands.add.AddFactsValidatorCommand;
import org.idempiere.cli.commands.add.AddModelCommand;
import org.idempiere.cli.commands.add.AddProcessCommand;
import org.idempiere.cli.commands.add.AddReportCommand;
import org.idempiere.cli.commands.add.AddRestExtensionCommand;
import org.idempiere.cli.commands.add.AddTestCommand;
import org.idempiere.cli.commands.add.AddWindowValidatorCommand;
import org.idempiere.cli.commands.add.AddZkFormCommand;
import picocli.CommandLine.Command;

@Command(
        name = "add",
        description = "Add a new component to an existing plugin",
        mixinStandardHelpOptions = true,
        subcommands = {
                AddCalloutCommand.class,
                AddEventHandlerCommand.class,
                AddProcessCommand.class,
                AddZkFormCommand.class,
                AddReportCommand.class,
                AddWindowValidatorCommand.class,
                AddRestExtensionCommand.class,
                AddFactsValidatorCommand.class,
                AddModelCommand.class,
                AddTestCommand.class
        }
)
public class AddCommand {
}
