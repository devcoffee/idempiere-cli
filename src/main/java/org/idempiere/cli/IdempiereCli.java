package org.idempiere.cli;

/**
 * Main entry point for the iDempiere CLI.
 * Registers all available subcommands for plugin development tasks.
 */
import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.idempiere.cli.commands.AddCommand;
import org.idempiere.cli.commands.BuildCommand;
import org.idempiere.cli.commands.ConfigCommand;
import org.idempiere.cli.commands.DeployCommand;
import org.idempiere.cli.commands.DepsCommand;
import org.idempiere.cli.commands.DiffSchemaCommand;
import org.idempiere.cli.commands.DoctorCommand;
import org.idempiere.cli.commands.GenerateCompletionCommand;
import org.idempiere.cli.commands.InfoCommand;
import org.idempiere.cli.commands.InitCommand;
import org.idempiere.cli.commands.MigrateCommand;
import org.idempiere.cli.commands.PackageCommand;
import org.idempiere.cli.commands.SetupDevEnvCommand;
import org.idempiere.cli.commands.SkillsCommand;
import org.idempiere.cli.commands.UpgradeCommand;
import org.idempiere.cli.commands.ValidateCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(
        name = "idempiere-cli",
        description = "A Developer CLI for iDempiere plugin development",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        subcommands = {
                DoctorCommand.class,
                SetupDevEnvCommand.class,
                InitCommand.class,
                AddCommand.class,
                InfoCommand.class,
                ValidateCommand.class,
                BuildCommand.class,
                DeployCommand.class,
                MigrateCommand.class,
                DepsCommand.class,
                PackageCommand.class,
                DiffSchemaCommand.class,
                ConfigCommand.class,
                SkillsCommand.class,
                GenerateCompletionCommand.class,
                UpgradeCommand.class
        }
)
public class IdempiereCli {
}
