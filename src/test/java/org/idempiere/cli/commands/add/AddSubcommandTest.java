package org.idempiere.cli.commands.add;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for all add subcommands.
 * Primarily tests the help output and option parsing for each subcommand.
 */
@QuarkusMainTest
class AddSubcommandTest {

    @Test
    @Launch({"add", "event-handler", "--help"})
    void testAddEventHandlerHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("event handler"));
    }

    @Test
    @Launch({"add", "process", "--help"})
    void testAddProcessHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("process"));
    }

    @Test
    @Launch({"add", "zk-form", "--help"})
    void testAddZkFormHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("ZK form"));
    }

    @Test
    @Launch({"add", "report", "--help"})
    void testAddReportHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
    }

    @Test
    @Launch({"add", "window-validator", "--help"})
    void testAddWindowValidatorHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("window validator"));
    }

    @Test
    @Launch({"add", "facts-validator", "--help"})
    void testAddFactsValidatorHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("facts validator"));
    }

    @Test
    @Launch({"add", "rest-extension", "--help"})
    void testAddRestExtensionHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("REST") || output.contains("rest"));
    }

    @Test
    @Launch({"add", "process-mapped", "--help"})
    void testAddProcessMappedHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
    }

    @Test
    @Launch({"add", "zk-form-zul", "--help"})
    void testAddZkFormZulHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("ZUL") || output.contains("zul"));
    }

    @Test
    @Launch({"add", "listbox-group", "--help"})
    void testAddListboxGroupHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
    }

    @Test
    @Launch({"add", "wlistbox-editor", "--help"})
    void testAddWListboxEditorHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
    }

    @Test
    @Launch({"add", "jasper-report", "--help"})
    void testAddJasperReportHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("Jasper") || output.contains("jasper"));
    }

    @Test
    @Launch({"add", "base-test", "--help"})
    void testAddBaseTestHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("test"));
    }
}
