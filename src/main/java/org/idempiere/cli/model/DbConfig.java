package org.idempiere.cli.model;

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
