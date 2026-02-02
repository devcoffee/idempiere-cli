package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.model.DbConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class DiffSchemaService {

    private static final Pattern COLUMNNAME_PATTERN = Pattern.compile("String COLUMNNAME_(\\w+)\\s*=");
    private static final Pattern GETTER_PATTERN = Pattern.compile("public\\s+(\\w+(?:\\[\\])?)\\s+get(\\w+)\\s*\\(\\)");

    public void diff(String tableName, Path srcDir, String packageName, DbConfig dbConfig) {
        System.out.println();
        System.out.println("Schema diff for table: " + tableName);
        System.out.println("==========================================");
        System.out.println();

        // Get columns from database
        Map<String, String> dbColumns = fetchDbColumns(tableName, dbConfig);
        if (dbColumns == null) return;

        // Get columns from code
        Map<String, String> codeColumns = parseCodeColumns(tableName, srcDir);
        if (codeColumns == null) {
            System.err.println("  Error: No model classes found for table " + tableName);
            System.err.println("  Run 'idempiere-cli add model --table " + tableName + "' first.");
            return;
        }

        // Compare
        boolean hasDifferences = false;

        // Columns in DB but not in code
        for (var entry : dbColumns.entrySet()) {
            if (!codeColumns.containsKey(entry.getKey())) {
                System.out.println("  + " + entry.getKey() + " (" + entry.getValue() + ") -- in DB, missing from code");
                hasDifferences = true;
            }
        }

        // Columns in code but not in DB
        for (var entry : codeColumns.entrySet()) {
            if (!dbColumns.containsKey(entry.getKey())) {
                System.out.println("  - " + entry.getKey() + " (" + entry.getValue() + ") -- in code, removed from DB");
                hasDifferences = true;
            }
        }

        // Type mismatches
        for (var entry : dbColumns.entrySet()) {
            String codeType = codeColumns.get(entry.getKey());
            if (codeType != null && !codeType.equals(entry.getValue())) {
                System.out.println("  ~ " + entry.getKey() + ": code=" + codeType + ", db=" + entry.getValue());
                hasDifferences = true;
            }
        }

        if (!hasDifferences) {
            System.out.println("  No differences found. Model classes are up to date.");
        }
        System.out.println();
    }

    private Map<String, String> fetchDbColumns(String tableName, DbConfig dbConfig) {
        Properties props = new Properties();
        props.setProperty("user", dbConfig.user());
        props.setProperty("password", dbConfig.password());

        try (Connection conn = DriverManager.getConnection(dbConfig.getJdbcUrl(), props)) {
            // Get table ID
            int tableId = -1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT AD_Table_ID FROM AD_Table WHERE TableName = ? AND IsActive = 'Y'")) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("  Error: Table '" + tableName + "' not found in AD_Table.");
                        return null;
                    }
                    tableId = rs.getInt("AD_Table_ID");
                }
            }

            // Get columns
            Map<String, String> columns = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ColumnName, AD_Reference_ID, ColumnSQL " +
                    "FROM AD_Column WHERE AD_Table_ID = ? AND IsActive = 'Y' " +
                    "ORDER BY ColumnName")) {
                ps.setInt(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String columnSQL = rs.getString("ColumnSQL");
                        if (columnSQL != null && !columnSQL.isBlank()) continue; // skip virtual
                        String colName = rs.getString("ColumnName");
                        String javaType = ModelGeneratorService.mapReferenceToJavaType(rs.getInt("AD_Reference_ID"));
                        columns.put(colName, javaType);
                    }
                }
            }
            return columns;
        } catch (SQLException e) {
            System.err.println("  Database error: " + e.getMessage());
            return null;
        }
    }

    private Map<String, String> parseCodeColumns(String tableName, Path srcDir) {
        Path interfaceFile = srcDir.resolve("I_" + tableName + ".java");
        Path xClassFile = srcDir.resolve("X_" + tableName + ".java");

        if (!Files.exists(interfaceFile) && !Files.exists(xClassFile)) {
            return null;
        }

        Map<String, String> columns = new LinkedHashMap<>();

        // Parse column names from interface
        if (Files.exists(interfaceFile)) {
            try {
                String content = Files.readString(interfaceFile);
                Matcher m = COLUMNNAME_PATTERN.matcher(content);
                while (m.find()) {
                    columns.put(m.group(1), "Object"); // placeholder type
                }
            } catch (IOException ignored) {
            }
        }

        // Parse getter types from X_ class
        if (Files.exists(xClassFile)) {
            try {
                String content = Files.readString(xClassFile);
                Matcher m = GETTER_PATTERN.matcher(content);
                while (m.find()) {
                    String type = m.group(1);
                    String colName = m.group(2);
                    columns.put(colName, type);
                }
            } catch (IOException ignored) {
            }
        }

        return columns.isEmpty() ? null : columns;
    }
}
