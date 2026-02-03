package org.idempiere.cli.commands;

import picocli.AutoComplete;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "generate-completion",
        description = "Generate shell completion script for bash or zsh"
)
public class GenerateCompletionCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Option(names = {"-n", "--name"}, description = "Command name (default: idempiere-cli)", defaultValue = "idempiere-cli")
    String commandName;

    @Override
    public void run() {
        String script = AutoComplete.bash(commandName, spec.root().commandLine());
        System.out.println(script);
        System.err.println();
        System.err.println("# To enable completion, run:");
        System.err.println("#   " + commandName + " generate-completion > ~/." + commandName + "-completion.bash");
        System.err.println("#   echo 'source ~/." + commandName + "-completion.bash' >> ~/.bashrc");
        System.err.println("#");
        System.err.println("# For zsh, add to ~/.zshrc:");
        System.err.println("#   autoload -U +X compinit && compinit");
        System.err.println("#   autoload -U +X bashcompinit && bashcompinit");
        System.err.println("#   source ~/." + commandName + "-completion.bash");
    }
}
