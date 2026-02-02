package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class AddCommandTest {

    @Test
    @Launch({"add", "--help"})
    void testAddHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("callout"));
        assertTrue(output.contains("event-handler"));
        assertTrue(output.contains("process"));
        assertTrue(output.contains("zk-form"));
        assertTrue(output.contains("report"));
        assertTrue(output.contains("model"));
        assertTrue(output.contains("test"));
    }

    @Test
    @Launch({"add", "callout", "--help"})
    void testAddCalloutHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--to"));
    }

    @Test
    @Launch({"add", "test", "--help"})
    void testAddTestHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--for"));
        assertTrue(output.contains("--dir"));
        assertTrue(output.contains("Generate test stubs"));
    }

    @Test
    @Launch({"add", "model", "--help"})
    void testAddModelHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--table"));
        assertTrue(output.contains("--db-host"));
        assertTrue(output.contains("--db-port"));
        assertTrue(output.contains("--config"));
    }
}
