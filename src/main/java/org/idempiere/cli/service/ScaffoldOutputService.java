package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.model.PluginDescriptor;

/**
 * Handles CLI console output for scaffold workflows.
 */
@ApplicationScoped
public class ScaffoldOutputService {

    public void printMultiModuleStart(String pluginId) {
        System.out.println();
        System.out.println("Creating iDempiere multi-module project: " + pluginId);
        System.out.println("==========================================");
        System.out.println();
    }

    public void printStandaloneStart(String pluginId) {
        System.out.println();
        System.out.println("Creating iDempiere plugin: " + pluginId);
        System.out.println("==========================================");
        System.out.println();
    }

    public void printMavenWrapperCreated() {
        System.out.println("  Created: mvnw, mvnw.cmd (Maven Wrapper)");
    }

    public void printMultiModuleSuccess(PluginDescriptor descriptor) {
        System.out.println();
        System.out.println("Multi-module project created successfully!");
        System.out.println();
        System.out.println("Structure:");
        System.out.println("  " + descriptor.getProjectName() + "/");
        System.out.println("  +-- " + descriptor.getPluginId() + ".parent/    (Maven parent)");
        System.out.println("  +-- " + descriptor.getBasePluginId() + "/    (Main plugin)");
        if (descriptor.isWithTest()) {
            System.out.println("  +-- " + descriptor.getBasePluginId() + ".test/    (Tests)");
        }
        if (descriptor.isWithFragment()) {
            System.out.println("  +-- " + descriptor.getPluginId() + ".fragment/    (Fragment)");
        }
        if (descriptor.isWithFeature()) {
            System.out.println("  +-- " + descriptor.getPluginId() + ".feature/    (Feature)");
        }
        System.out.println("  +-- " + descriptor.getPluginId() + ".p2/    (P2 repository)");
        System.out.println();
        printSharedNextSteps(descriptor);
        System.out.println("To package for distribution:");
        System.out.println("  ./mvnw verify    (JAR will be in p2/target/repository/)");
        System.out.println();
        System.out.println("Tip: Use 'idempiere-cli add <component>' to add callouts, processes, forms, etc.");
        System.out.println();
    }

    public void printStandaloneSuccess(PluginDescriptor descriptor) {
        System.out.println();
        System.out.println("Plugin created successfully!");
        System.out.println();
        printSharedNextSteps(descriptor);
        System.out.println("Tip: Use 'idempiere-cli add <component>' to add callouts, processes, forms, etc.");
        System.out.println();
    }

    private void printSharedNextSteps(PluginDescriptor descriptor) {
        System.out.println("Next steps:");
        if (descriptor.isWithEclipseProject()) {
            System.out.println("  Import into Eclipse (choose one):");
            System.out.println("    a) CLI:     idempiere-cli import-workspace --dir=" + descriptor.getProjectName());
            System.out.println("    b) Manual:  File > Import > Existing Projects into Workspace");
            System.out.println("                Select " + descriptor.getProjectName() + "/ as root and click Finish");
        } else {
            System.out.println("  Import into Eclipse:");
            System.out.println("    File > Import > Maven > Existing Maven Projects");
            System.out.println("    Select " + descriptor.getProjectName() + "/ as root and click Finish");
        }
        System.out.println();
        System.out.println("To build:");
        System.out.println("  cd " + descriptor.getProjectName());
        System.out.println("  ./mvnw verify    (or mvnw.cmd on Windows)");
        System.out.println();
    }
}
