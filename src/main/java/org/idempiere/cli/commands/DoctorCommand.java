package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.DoctorService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Validates the development environment and plugin structure.
 *
 * <p>Performs comprehensive checks for:
 * <ul>
 *   <li>Java JDK (version and JAVA_HOME)</li>
 *   <li>Maven installation</li>
 *   <li>Git installation</li>
 *   <li>Docker (optional, for database containers)</li>
 *   <li>PostgreSQL client tools</li>
 * </ul>
 *
 * <h2>Plugin Validation</h2>
 * <p>When used with {@code --dir}, validates plugin structure:
 * <ul>
 *   <li>MANIFEST.MF syntax and required headers</li>
 *   <li>build.properties configuration</li>
 *   <li>Require-Bundle consistency</li>
 * </ul>
 *
 * <h2>Auto-fix</h2>
 * <p>Use {@code --fix} to automatically install missing tools using
 * the system package manager (Homebrew on macOS, apt on Linux).
 *
 * @see DoctorService#checkEnvironment(boolean, boolean)
 * @see DoctorService#checkPlugin(java.nio.file.Path)
 */
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
