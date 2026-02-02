package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.DoctorService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "doctor",
        description = "Check required tools and environment prerequisites",
        mixinStandardHelpOptions = true
)
public class DoctorCommand implements Runnable {

    @Option(names = {"--fix"}, description = "Attempt to auto-fix missing dependencies")
    boolean fix;

    @Option(names = {"--dir"}, description = "Validate plugin structure in the given directory")
    String dir;

    @Inject
    DoctorService doctorService;

    @Override
    public void run() {
        if (dir != null) {
            doctorService.checkPlugin(java.nio.file.Path.of(dir));
        } else {
            doctorService.checkEnvironment(fix);
        }
    }
}
