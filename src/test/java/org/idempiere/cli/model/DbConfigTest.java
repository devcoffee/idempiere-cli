package org.idempiere.cli.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DbConfig.
 */
class DbConfigTest {

    @Test
    void testRecordCreation() {
        DbConfig config = new DbConfig("localhost", 5432, "idempiere", "adempiere", "secret");

        assertEquals("localhost", config.host());
        assertEquals(5432, config.port());
        assertEquals("idempiere", config.name());
        assertEquals("adempiere", config.user());
        assertEquals("secret", config.password());
    }

    @Test
    void testGetJdbcUrl() {
        DbConfig config = new DbConfig("dbhost", 5433, "mydb", "user", "pass");

        String url = config.getJdbcUrl();
        assertEquals("jdbc:postgresql://dbhost:5433/mydb", url);
    }

    @Test
    void testEquality() {
        DbConfig config1 = new DbConfig("localhost", 5432, "idempiere", "user", "pass");
        DbConfig config2 = new DbConfig("localhost", 5432, "idempiere", "user", "pass");
        DbConfig config3 = new DbConfig("otherhost", 5432, "idempiere", "user", "pass");

        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
    }

    @Test
    void testHashCode() {
        DbConfig config1 = new DbConfig("localhost", 5432, "idempiere", "user", "pass");
        DbConfig config2 = new DbConfig("localhost", 5432, "idempiere", "user", "pass");

        assertEquals(config1.hashCode(), config2.hashCode());
    }
}
