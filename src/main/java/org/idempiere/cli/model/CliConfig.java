package org.idempiere.cli.model;

/**
 * Configuration model for .idempiere-cli.yaml.
 *
 * <p>This class represents the user's configuration file that can be placed at:
 * <ul>
 *   <li>{@code ~/.idempiere-cli.yaml} - Global user configuration</li>
 *   <li>{@code ./.idempiere-cli.yaml} - Project-specific configuration</li>
 * </ul>
 *
 * <p>Project configuration takes precedence over global configuration.
 *
 * <h2>Example Configuration</h2>
 * <pre>
 * # .idempiere-cli.yaml
 * defaults:
 *   vendor: "My Company Inc."
 *   idempiere-version: 12
 * </pre>
 *
 * @see org.idempiere.cli.service.CliConfigService
 */
public class CliConfig {

    private Defaults defaults = new Defaults();

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults;
    }

    /**
     * Default values for plugin scaffolding and other commands.
     */
    public static class Defaults {
        private String vendor;
        private Integer idempiereVersion;

        public String getVendor() {
            return vendor != null ? vendor : "";
        }

        public void setVendor(String vendor) {
            this.vendor = vendor;
        }

        public int getIdempiereVersion() {
            return idempiereVersion != null ? idempiereVersion : 13;
        }

        public void setIdempiereVersion(int idempiereVersion) {
            this.idempiereVersion = idempiereVersion;
        }

        public void setIdempiereVersion(Integer idempiereVersion) {
            this.idempiereVersion = idempiereVersion;
        }

        /**
         * Checks if vendor was explicitly set in config.
         * @return true if vendor is set
         */
        public boolean hasVendor() {
            return vendor != null && !vendor.isEmpty();
        }

        /**
         * Checks if idempiereVersion was explicitly set in config.
         * @return true if idempiereVersion is set
         */
        public boolean hasIdempiereVersion() {
            return idempiereVersion != null;
        }

        /**
         * Merges another Defaults instance into this one.
         * Only explicitly set values from the other instance override this instance.
         *
         * @param other the other Defaults to merge from (project config)
         */
        public void mergeFrom(Defaults other) {
            if (other.hasVendor()) {
                this.vendor = other.vendor;
            }
            if (other.hasIdempiereVersion()) {
                this.idempiereVersion = other.idempiereVersion;
            }
        }
    }

    /**
     * Merges another CliConfig instance into this one.
     * Values from the other instance override this instance.
     *
     * @param other the other config to merge from (project config)
     */
    public void mergeFrom(CliConfig other) {
        if (other.defaults != null) {
            this.defaults.mergeFrom(other.defaults);
        }
    }
}
