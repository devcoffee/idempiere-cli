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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@ApplicationScoped
public class ModelGeneratorService {

    public boolean generate(String tableName, Path srcDir, String pluginId, DbConfig dbConfig) {
        System.out.println("  Connecting to database: " + dbConfig.getJdbcUrl());

        Properties props = new Properties();
        props.setProperty("user", dbConfig.user());
        props.setProperty("password", dbConfig.password());

        try (Connection conn = DriverManager.getConnection(dbConfig.getJdbcUrl(), props)) {
            // Get table info from AD_Table
            int tableId = -1;
            String accessLevel = "3"; // default: Client+Org
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT AD_Table_ID, AccessLevel FROM AD_Table WHERE TableName = ? AND IsActive = 'Y'")) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("  Error: Table '" + tableName + "' not found in AD_Table.");
                        return false;
                    }
                    tableId = rs.getInt("AD_Table_ID");
                    accessLevel = rs.getString("AccessLevel");
                }
            }

            // Get columns from AD_Column
            List<ColumnInfo> columns = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT c.ColumnName, c.AD_Reference_ID, c.IsMandatory, c.FieldLength, " +
                    "c.DefaultValue, c.IsKey, c.ColumnSQL, c.IsIdentifier, c.SeqNo, " +
                    "c.AD_Reference_Value_ID, c.ValueMin, c.ValueMax, c.IsUpdateable " +
                    "FROM AD_Column c " +
                    "WHERE c.AD_Table_ID = ? AND c.IsActive = 'Y' " +
                    "ORDER BY c.ColumnName")) {
                ps.setInt(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        columns.add(new ColumnInfo(
                                rs.getString("ColumnName"),
                                rs.getInt("AD_Reference_ID"),
                                "Y".equals(rs.getString("IsMandatory")),
                                rs.getInt("FieldLength"),
                                rs.getString("DefaultValue"),
                                "Y".equals(rs.getString("IsKey")),
                                rs.getString("ColumnSQL"),
                                "Y".equals(rs.getString("IsIdentifier")),
                                rs.getInt("SeqNo"),
                                rs.getInt("AD_Reference_Value_ID"),
                                "Y".equals(rs.getString("IsUpdateable"))
                        ));
                    }
                }
            }

            if (columns.isEmpty()) {
                System.err.println("  Error: No columns found for table '" + tableName + "'.");
                return false;
            }

            System.out.println("  Found " + columns.size() + " columns for table " + tableName);

            // Generate files
            Files.createDirectories(srcDir);

            generateInterface(srcDir, pluginId, tableName, tableId, columns);
            generateXClass(srcDir, pluginId, tableName, tableId, accessLevel, columns);
            generateMClass(srcDir, pluginId, tableName);

            return true;
        } catch (SQLException e) {
            System.err.println("  Database error: " + e.getMessage());
            return false;
        } catch (IOException e) {
            System.err.println("  File error: " + e.getMessage());
            return false;
        }
    }

    private void generateInterface(Path srcDir, String packageName, String tableName, int tableId,
                                   List<ColumnInfo> columns) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.sql.Timestamp;\n");
        sb.append("import org.compiere.model.MTable;\n");
        sb.append("import org.compiere.util.KeyNamePair;\n\n");

        sb.append("public interface I_").append(tableName).append(" {\n\n");
        sb.append("    String Table_Name = \"").append(tableName).append("\";\n");
        sb.append("    int Table_ID = MTable.getTable_ID(Table_Name);\n\n");

        for (ColumnInfo col : columns) {
            String colName = col.columnName();
            sb.append("    String COLUMNNAME_").append(colName).append(" = \"").append(colName).append("\";\n");
        }
        sb.append("\n");

        for (ColumnInfo col : columns) {
            if (col.columnSQL() != null && !col.columnSQL().isBlank()) continue; // virtual column
            String javaType = mapReferenceToJavaType(col.referenceId());
            String colName = col.columnName();

            // Setter
            if (col.isUpdateable()) {
                sb.append("    void set").append(colName).append("(").append(javaType).append(" ").append(colName).append(");\n");
            }
            // Getter
            sb.append("    ").append(javaType).append(" get").append(colName).append("();\n\n");
        }

        sb.append("}\n");

        Path file = srcDir.resolve("I_" + tableName + ".java");
        Files.writeString(file, sb.toString());
        System.out.println("  Created: " + file);
    }

    private void generateXClass(Path srcDir, String packageName, String tableName, int tableId,
                                String accessLevel, List<ColumnInfo> columns) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.sql.ResultSet;\n");
        sb.append("import java.sql.Timestamp;\n");
        sb.append("import java.util.Properties;\n");
        sb.append("import org.compiere.model.PO;\n");
        sb.append("import org.compiere.util.KeyNamePair;\n\n");

        sb.append("public class X_").append(tableName).append(" extends PO implements I_").append(tableName).append(" {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");

        // Constructor with context
        sb.append("    public X_").append(tableName).append("(Properties ctx, int ").append(tableName).append("_ID, String trxName) {\n");
        sb.append("        super(ctx, ").append(tableName).append("_ID, trxName);\n");
        sb.append("    }\n\n");

        // Constructor with ResultSet
        sb.append("    public X_").append(tableName).append("(Properties ctx, ResultSet rs, String trxName) {\n");
        sb.append("        super(ctx, rs, trxName);\n");
        sb.append("    }\n\n");

        // initPO
        sb.append("    @Override\n");
        sb.append("    protected int get_AccessLevel() {\n");
        sb.append("        return Integer.parseInt(\"").append(accessLevel).append("\");\n");
        sb.append("    }\n\n");

        // Table info
        sb.append("    @Override\n");
        sb.append("    public String toString() {\n");
        sb.append("        return \"").append(tableName).append("[\" + get_ID() + \"]\";\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    protected POInfo initPO(Properties ctx) {\n");
        sb.append("        POInfo poi = POInfo.getPOInfo(ctx, Table_ID, get_TrxName());\n");
        sb.append("        return poi;\n");
        sb.append("    }\n\n");

        // Getters and setters
        for (ColumnInfo col : columns) {
            if (col.columnSQL() != null && !col.columnSQL().isBlank()) continue;
            String javaType = mapReferenceToJavaType(col.referenceId());
            String colName = col.columnName();

            // Setter
            if (col.isUpdateable()) {
                sb.append("    @Override\n");
                sb.append("    public void set").append(colName).append("(").append(javaType).append(" ").append(colName).append(") {\n");
                sb.append("        set_Value(COLUMNNAME_").append(colName).append(", ").append(colName).append(");\n");
                sb.append("    }\n\n");
            }

            // Getter
            sb.append("    @Override\n");
            sb.append("    public ").append(javaType).append(" get").append(colName).append("() {\n");
            switch (javaType) {
                case "int" -> sb.append("        Integer ii = (Integer) get_Value(COLUMNNAME_").append(colName).append(");\n")
                        .append("        if (ii == null) return 0;\n")
                        .append("        return ii;\n");
                case "boolean" -> sb.append("        Object oo = get_Value(COLUMNNAME_").append(colName).append(");\n")
                        .append("        if (oo != null) {\n")
                        .append("            if (oo instanceof Boolean) return (Boolean) oo;\n")
                        .append("            return \"Y\".equals(oo);\n")
                        .append("        }\n")
                        .append("        return false;\n");
                case "BigDecimal" -> sb.append("        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_").append(colName).append(");\n")
                        .append("        if (bd == null) return BigDecimal.ZERO;\n")
                        .append("        return bd;\n");
                default -> sb.append("        return (").append(javaType).append(") get_Value(COLUMNNAME_").append(colName).append(");\n");
            }
            sb.append("    }\n\n");
        }

        sb.append("}\n");

        Path file = srcDir.resolve("X_" + tableName + ".java");
        Files.writeString(file, sb.toString());
        System.out.println("  Created: " + file);
    }

    private void generateMClass(Path srcDir, String packageName, String tableName) throws IOException {
        Path file = srcDir.resolve("M" + tableName + ".java");
        if (Files.exists(file)) {
            System.out.println("  Skipped: " + file + " (already exists)");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import java.sql.ResultSet;\n");
        sb.append("import java.util.Properties;\n\n");

        sb.append("public class M").append(tableName).append(" extends X_").append(tableName).append(" {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");

        sb.append("    public M").append(tableName).append("(Properties ctx, int ").append(tableName).append("_ID, String trxName) {\n");
        sb.append("        super(ctx, ").append(tableName).append("_ID, trxName);\n");
        sb.append("    }\n\n");

        sb.append("    public M").append(tableName).append("(Properties ctx, ResultSet rs, String trxName) {\n");
        sb.append("        super(ctx, rs, trxName);\n");
        sb.append("    }\n");

        sb.append("}\n");

        Files.writeString(file, sb.toString());
        System.out.println("  Created: " + file);
    }

    static String mapReferenceToJavaType(int referenceId) {
        return switch (referenceId) {
            case 10, 14, 34, 36, 40 -> "String";    // String, Text, Memo, URL, FileName
            case 11, 13, 18, 19, 21, 25, 30, 31, 35 -> "int"; // Integer, ID, Table, TableDir, Location, Account, Search, Locator, List
            case 12, 22, 29, 37 -> "BigDecimal";     // Amount, Number, Quantity, CostPrice
            case 15, 16, 24 -> "Timestamp";           // Date, DateTime, Time
            case 20, 28 -> "boolean";                  // YesNo, Button
            case 23 -> "byte[]";                       // Binary
            default -> "Object";
        };
    }

    private record ColumnInfo(
            String columnName,
            int referenceId,
            boolean isMandatory,
            int fieldLength,
            String defaultValue,
            boolean isKey,
            String columnSQL,
            boolean isIdentifier,
            int seqNo,
            int referenceValueId,
            boolean isUpdateable
    ) {}

    public DbConfig resolveDbConfig(String host, int port, String name, String user, String password, String configPath) {
        // Try config file first
        if (configPath != null) {
            DbConfig fromFile = readFromEnvProperties(Path.of(configPath));
            if (fromFile != null) return fromFile;
        }

        // Try IDEMPIERE_HOME env variable
        String idempiereHome = System.getenv("IDEMPIERE_HOME");
        if (idempiereHome != null && host.equals("localhost") && port == 5432 && name.equals("idempiere")) {
            Path envProps = Path.of(idempiereHome, "idempiereEnv.properties");
            DbConfig fromEnv = readFromEnvProperties(envProps);
            if (fromEnv != null) return fromEnv;
        }

        return new DbConfig(host, port, name, user, password);
    }

    private DbConfig readFromEnvProperties(Path propsFile) {
        if (!Files.exists(propsFile)) return null;

        try {
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(propsFile)) {
                props.load(reader);
            }

            String host = props.getProperty("ADEMPIERE_DB_SERVER", "localhost");
            String portStr = props.getProperty("ADEMPIERE_DB_PORT", "5432");
            String dbName = props.getProperty("ADEMPIERE_DB_NAME", "idempiere");
            String user = props.getProperty("ADEMPIERE_DB_USER", "adempiere");
            String password = props.getProperty("ADEMPIERE_DB_PASSWORD", "adempiere");

            return new DbConfig(host, Integer.parseInt(portStr), dbName, user, password);
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }
}
