package de.javakaffee.web.msm;

class MemcachedUtil {

    private static final int THIRTY_DAYS = 60*60*24*30;

    static int toMemcachedExpiration(final int expirationInSeconds) {
        return expirationInSeconds <= THIRTY_DAYS ? expirationInSeconds : (int)(System.currentTimeMillis() / 1000) + expirationInSeconds;
    }
}
