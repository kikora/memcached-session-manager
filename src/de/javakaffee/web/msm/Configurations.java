package de.javakaffee.web.msm;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public final class Configurations {


    public static final String NODE_AVAILABILITY_CACHE_TTL_KEY = "msm.nodeAvailabilityCacheTTL";
    /**
     * The max reconnect delay for the MemcachedClient, in seconds.
     */
    public static final String MAX_RECONNECT_DELAY_KEY = "msm.maxReconnectDelay";

    private static final Log LOG = LogFactory.getLog(Configurations.class);

    public static int getSystemProperty(final String propName, final int defaultValue) {
        final String value = System.getProperty(propName);
        if(value != null) {
            try {
                return Integer.parseInt(value);
            } catch(final NumberFormatException e) {
                LOG.warn("Could not parse configured value for system property '" + propName + "': " + value);
            }
        }
        return defaultValue;
    }

    public static long getSystemProperty(final String propName, final long defaultValue) {
        final String value = System.getProperty(propName);
        if(value != null) {
            try {
                return Long.parseLong(value);
            } catch(final NumberFormatException e) {
                LOG.warn("Could not parse configured value for system property '" + propName + "': " + value);
            }
        }
        return defaultValue;
    }

}