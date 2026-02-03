package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.Console;
import java.util.Scanner;

/**
 * Handles interactive user prompts for CLI commands.
 */
@ApplicationScoped
public class InteractivePromptService {

    private Console console;
    private Scanner scanner;

    public String prompt(String message, String defaultValue) {
        if (defaultValue != null && !defaultValue.isEmpty()) {
            System.out.print(message + " [" + defaultValue + "]: ");
        } else {
            System.out.print(message + ": ");
        }
        String input = readLine();
        if (input == null || input.isBlank()) {
            return defaultValue != null ? defaultValue : "";
        }
        return input.trim();
    }

    public boolean confirm(String message) {
        System.out.print(message + " (y/N): ");
        String input = readLine();
        return input != null && (input.trim().equalsIgnoreCase("y") || input.trim().equalsIgnoreCase("yes"));
    }

    private String readLine() {
        if (console == null) {
            console = System.console();
        }
        if (console != null) {
            return console.readLine();
        }
        if (scanner == null) {
            scanner = new Scanner(System.in);
        }
        if (scanner.hasNextLine()) {
            return scanner.nextLine();
        }
        return "";
    }
}
