package de.javakaffee.web.msm;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import static de.javakaffee.web.msm.TranscoderService.decodeNum;
import static de.javakaffee.web.msm.TranscoderService.encodeNum;

public class SessionValidityInfo {

    @SuppressWarnings( "unused" )
    private static final Log LOG = LogFactory.getLog( SessionValidityInfo.class );

    private final int _maxInactiveInterval;
    private final long _lastAccessedTime;
    private final long _thisAccessedTime;

    public SessionValidityInfo( final int maxInactiveInterval, final long lastAccessedTime, final long thisAccessedTime ) {
        _maxInactiveInterval = maxInactiveInterval;
        _lastAccessedTime = lastAccessedTime;
        _thisAccessedTime = thisAccessedTime;
    }

    /**
     * Encode the given information to a byte[], that can be decoded later via {@link #decode(byte[])}.
     */
    public static byte[] encode( final long maxInactiveInterval, final long lastAccessedTime, final long thisAccessedTime ) {
        int idx = 0;
        final byte[] data = new byte[ 4 + 2 * 8 ];
        encodeNum( maxInactiveInterval, data, idx, 4 );
        encodeNum( lastAccessedTime, data, idx += 4, 8 );
        encodeNum( thisAccessedTime, data, idx += 8, 8 );
        return data;
    }

    /**
     * Decode the given byte[] that previously was created via {@link #encode(long, long, long)}.
     */
    public static SessionValidityInfo decode( final byte[] data ) {
        int idx = 0;
        final int maxInactiveInterval = (int) decodeNum( data, idx, 4 );
        final long lastAccessedTime = decodeNum( data, idx += 4, 8 );
        final long thisAccessedTime = decodeNum( data, idx += 8, 8 );
        return new SessionValidityInfo( maxInactiveInterval, lastAccessedTime, thisAccessedTime );
    }

    public int getMaxInactiveInterval() {
        return _maxInactiveInterval;
    }

    public long getLastAccessedTime() {
        return _lastAccessedTime;
    }

    public long getThisAccessedTime() {
        return _thisAccessedTime;
    }

    public boolean isValid() {
        final long timeNow = System.currentTimeMillis();
        final int timeIdle = (int) ((timeNow - _thisAccessedTime) / 1000L);
        // if tomcat session inactivity is negative or 0, session
        // should not expire
        return _maxInactiveInterval <= 0 || timeIdle < _maxInactiveInterval;
    }

}
