package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Shared error builders for scaffold workflows.
 */
@ApplicationScoped
public class ScaffoldErrorService {

    public ScaffoldResult directoryExistsError(Path dir) {
        String message = "Directory '" + dir + "' already exists.";
        System.err.println("Error: " + message);
        return ScaffoldResult.error("DIRECTORY_EXISTS", message);
    }

    public ScaffoldResult ioError(String context, IOException e, boolean withStackTrace) {
        String message = context + ": " + e.getMessage();
        System.err.println(message);
        if (withStackTrace) {
            e.printStackTrace();
        }
        return ScaffoldResult.error("IO_ERROR", message);
    }
}
