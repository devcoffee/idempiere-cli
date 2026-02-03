package org.idempiere.cli.model;

/**
 * Database connection configuration.
 */
public record DbConfig(
        String host,
        int port,
        String name,
        String user,
        String password
) {
    public String getJdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }
}
