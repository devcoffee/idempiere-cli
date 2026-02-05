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

    @Option(names = {"--fix-optional"}, description = "Also install optional tools (e.g. Docker) when using --fix")
    boolean fixOptional;

    @Option(names = {"--dir"}, description = "Validate plugin structure in the given directory")
    String dir;

    @Inject
    DoctorService doctorService;

    @Override
    public void run() {
        if (dir != null) {
            doctorService.checkPlugin(java.nio.file.Path.of(dir));
        } else {
            // --fix-optional implies --fix
            if (fixOptional) fix = true;
            doctorService.checkEnvironment(fix, fixOptional);
        }
    }
}
