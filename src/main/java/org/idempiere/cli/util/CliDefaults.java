package org.idempiere.cli.util;

/**
 * Centralized default values for the iDempiere CLI.
 * These are configurable defaults that can be overridden via command-line options.
 */
public final class CliDefaults {

    private CliDefaults() {
        // Utility class
    }

    // ========== Git Configuration ==========
    public static final String GIT_BRANCH = "master";
    public static final String IDEMPIERE_REPO_URL = "https://github.com/idempiere/idempiere.git";
    public static final String IDEMPIERE_REST_REPO_URL = "https://github.com/idempiere/idempiere-rest.git";

    // ========== Database Configuration ==========
    public static final String DB_TYPE = "postgresql";
    public static final String DB_HOST = "localhost";
    public static final int DB_PORT = 5432;
    public static final String DB_NAME = "idempiere";
    public static final String DB_USER = "adempiere";
    public static final String DB_PASSWORD = "adempiere";
    public static final String DB_ADMIN_PASSWORD = "postgres";

    // ========== Docker Configuration ==========
    public static final String DOCKER_CONTAINER_NAME = "idempiere-postgres";
    public static final String DOCKER_POSTGRES_VERSION = "15.3";

    // ========== Oracle Docker Configuration ==========
    public static final String DOCKER_ORACLE_CONTAINER = "idempiere-oracle";
    public static final String DOCKER_ORACLE_IMAGE = "gvenzl/oracle-xe:21-slim";
    public static final String DOCKER_ORACLE_HOME = "/opt/oracle";
    public static final int DOCKER_ORACLE_PORT = 1521;

    // ========== HTTP/HTTPS Configuration ==========
    public static final int HTTP_PORT = 8080;
    public static final int HTTPS_PORT = 8443;
    public static final String HTTP_BIND_ADDRESS = "0.0.0.0";

    // ========== OSGi Console Configuration ==========
    public static final int OSGI_PORT = 12612;
    public static final int OSGI_SOCKET_TIMEOUT_MS = 5000;
    public static final int OSGI_RESPONSE_DELAY_MS = 500;
    public static final int OSGI_COMMAND_DELAY_MS = 1000;

    // ========== Database Wait Configuration ==========
    public static final int DATABASE_WAIT_MAX_RETRIES = 30;
    public static final int DATABASE_WAIT_RETRY_DELAY_MS = 2000;

    // ========== Console Setup Configuration ==========
    public static final String JAVA_HEAP_SIZE = "-Xmx2048M";
    public static final String KEYSTORE_PASSWORD = "myPassword";
    public static final String KEYSTORE_ORG_NAME = "idempiere.org";
    public static final String KEYSTORE_ORG_UNIT = "iDempiere";
    public static final String KEYSTORE_LOCATION = "myTown";
    public static final String KEYSTORE_STATE = "CA";
    public static final String KEYSTORE_COUNTRY = "US";

    // ========== Mail Configuration ==========
    public static final String MAIL_USER = "info";
    public static final String MAIL_PASSWORD = "info";
    public static final String MAIL_ADMIN_ADDRESS = "info@idempiere";

    // ========== Session Logging ==========
    public static final int SESSION_LOGS_KEEP_COUNT = 20;
    public static final String SESSION_LOGS_DIR = ".idempiere-cli/logs";

    // ========== Jython Configuration ==========
    public static final String JYTHON_VERSION = "2.7.4";
    public static final String JYTHON_MAVEN_BASE_URL = "https://repo1.maven.org/maven2/org/python/jython-standalone/";

    // ========== Eclipse Configuration ==========
    public static final String ECLIPSE_VERSION = "2025-09";
    public static final String ECLIPSE_RELEASE = "R";
}
