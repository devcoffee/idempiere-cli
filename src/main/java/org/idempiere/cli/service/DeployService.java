package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.util.CliDefaults;
import org.idempiere.cli.util.PluginUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@ApplicationScoped
public class DeployService {

    public boolean copyDeploy(Path jarFile, Path idempiereHome) {
        Path pluginsDir = idempiereHome.resolve("plugins");
        if (!Files.exists(pluginsDir)) {
            System.err.println("  Error: plugins/ directory not found at " + idempiereHome.toAbsolutePath());
            return false;
        }

        try {
            Path target = pluginsDir.resolve(jarFile.getFileName());
            Files.copy(jarFile, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  Deployed to: " + target.toAbsolutePath());
            System.out.println();
            System.out.println("  Restart iDempiere to load the plugin.");
            return true;
        } catch (IOException e) {
            System.err.println("  Error deploying plugin: " + e.getMessage());
            return false;
        }
    }

    public boolean hotDeploy(Path jarFile, String host, int port) {
        String fileUri = jarFile.toAbsolutePath().toUri().toString();
        String bundleName = jarFile.getFileName().toString().replaceAll("-\\d.*\\.jar$", "").replace(".jar", "");

        System.out.println("  Connecting to OSGi console at " + host + ":" + port + "...");

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            socket.setSoTimeout(CliDefaults.OSGI_SOCKET_TIMEOUT_MS);

            // Read initial prompt
            readAvailable(in);

            // Check if bundle is already installed
            out.println("ss " + bundleName);
            out.flush();
            Thread.sleep(CliDefaults.OSGI_RESPONSE_DELAY_MS);
            String ssOutput = readAvailable(in);

            if (ssOutput.contains(bundleName)) {
                // Bundle exists — find ID and update
                String bundleId = extractBundleId(ssOutput, bundleName);
                if (bundleId != null) {
                    System.out.println("  Updating existing bundle (id=" + bundleId + ")...");
                    out.println("update " + bundleId + " " + fileUri);
                    out.flush();
                    Thread.sleep(CliDefaults.OSGI_COMMAND_DELAY_MS);
                    readAvailable(in);
                    System.out.println("  Bundle updated successfully.");
                    return true;
                }
            }

            // New bundle — install and start
            System.out.println("  Installing new bundle...");
            out.println("install " + fileUri);
            out.flush();
            Thread.sleep(CliDefaults.OSGI_COMMAND_DELAY_MS);
            String installOutput = readAvailable(in);

            String newBundleId = extractInstalledBundleId(installOutput);
            if (newBundleId != null) {
                out.println("start " + newBundleId);
                out.flush();
                Thread.sleep(CliDefaults.OSGI_RESPONSE_DELAY_MS);
                readAvailable(in);
                System.out.println("  Bundle installed and started (id=" + newBundleId + ").");
            } else {
                System.out.println("  Bundle installed. " + installOutput.trim());
            }

            // Disconnect
            out.println("disconnect");
            out.flush();

            return true;
        } catch (IOException e) {
            System.err.println("  Error connecting to OSGi console: " + e.getMessage());
            System.err.println("  Make sure iDempiere is running and the OSGi console is available on port " + port + ".");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public Optional<Path> findBuiltJar(Path pluginDir) {
        return PluginUtils.findBuiltJar(pluginDir);
    }

    private String readAvailable(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (in.ready()) {
            int ch = in.read();
            if (ch == -1) break;
            sb.append((char) ch);
        }
        return sb.toString();
    }

    private String extractBundleId(String ssOutput, String bundleName) {
        // OSGi ss output format: "123  ACTIVE  bundleName_1.0.0"
        for (String line : ssOutput.split("\n")) {
            if (line.contains(bundleName)) {
                String trimmed = line.trim();
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 1) {
                    try {
                        Integer.parseInt(parts[0]);
                        return parts[0];
                    } catch (NumberFormatException e) {
                        // not a bundle id line
                    }
                }
            }
        }
        return null;
    }

    private String extractInstalledBundleId(String output) {
        // Install output: "Bundle id is 123"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Bundle id is (\\d+)").matcher(output);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
