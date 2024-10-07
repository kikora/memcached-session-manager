package de.javakaffee.web.msm;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.util.regex.Pattern;

public class SessionIdFormat {

    private static final String BACKUP_PREFIX = "bak:";

    private static final Log LOG = LogFactory.getLog( SessionIdFormat.class );

    /**
     * The pattern for the session id.
     */
    private final Pattern _pattern = Pattern.compile( "[^-.]+-[^.]+(\\.[^.]+)?" );

    private final StorageKeyFormat _storageKeyFormat;

    public SessionIdFormat() {
        this(StorageKeyFormat.EMPTY);
    }

    public SessionIdFormat(final StorageKeyFormat storageKeyFormat) {
        _storageKeyFormat = storageKeyFormat;
    }

    /**
     * Create a session id including the provided memcachedId.
     *
     * @param sessionId
     *            the original session id, it might contain the jvm route
     * @param memcachedId
     *            the memcached id to encode in the session id, may be <code>null</code>.
     * @return the sessionId which now contains the memcachedId if one was provided, otherwise
     *  the sessionId unmodified.
     */

    public String createSessionId( final String sessionId,  final String memcachedId) {
        if ( LOG.isDebugEnabled() ) {
            LOG.debug( "Creating new session id with orig id '" + sessionId + "' and memcached id '" + memcachedId + "'." );
        }
        if ( memcachedId == null ) {
            return sessionId;
        }
        final int idx = sessionId.indexOf( '.' );
        if ( idx < 0 ) {
            return sessionId + "-" + memcachedId;
        } else {
            return sessionId.substring( 0, idx ) + "-" + memcachedId + sessionId.substring( idx );
        }
    }

    /**
     * Change the provided session id (optionally already including a memcachedId) so that it
     * contains the provided newMemcachedId.
     *
     * @param sessionId
     *            the session id that may contain a former memcachedId.
     * @param newMemcachedId
     *            the new memcached id.
     * @return the sessionId which now contains the new memcachedId instead the
     *         former one.
     */

    public String createNewSessionId(  final String sessionId,  final String newMemcachedId) {
        final int idxDot = sessionId.indexOf( '.' );
        if ( idxDot != -1 ) {
            final String plainSessionId = sessionId.substring( 0, idxDot );
            final String jvmRouteWithDot = sessionId.substring( idxDot );
            return appendOrReplaceMemcachedId( plainSessionId, newMemcachedId ) + jvmRouteWithDot;
        }
        else {
            return appendOrReplaceMemcachedId( sessionId, newMemcachedId );
        }
    }


    private String appendOrReplaceMemcachedId(  final String sessionId,  final String newMemcachedId ) {
        final int idxDash = sessionId.indexOf( '-' );
        if ( idxDash < 0 ) {
            return sessionId + "-" + newMemcachedId;
        } else {
            return sessionId.substring( 0, idxDash + 1 ) + newMemcachedId;
        }
    }

    /**
     * Change the provided session id (optionally already including a jvmRoute) so that it
     * contains the provided newJvmRoute.
     *
     * @param sessionId
     *            the session id that may contain a former jvmRoute.
     * @param newJvmRoute
     *            the new jvm route.
     * @return the sessionId which now contains the new jvmRoute instead the
     *         former one.
     */

    public String changeJvmRoute(  final String sessionId,  final String newJvmRoute ) {
        return stripJvmRoute( sessionId ) + "." + newJvmRoute;
    }

    /**
     * Checks if the given session id matches the pattern
     * <code>[^-.]+-[^.]+(\.[\w]+)?</code>.
     *
     * @param sessionId
     *            the session id
     * @return true if matching, otherwise false.
     */
    public boolean isValid( final String sessionId ) {
        return sessionId != null && _pattern.matcher( sessionId ).matches();
    }

    /**
     * Extract the memcached id from the given session id.
     *
     * @param sessionId
     *            the session id including the memcached id and eventually the
     *            jvmRoute.
     * @return the memcached id or null if the session id didn't contain any
     *         memcached id.
     */
    public String extractMemcachedId( final String sessionId ) {
        final int idxDash = sessionId.indexOf( '-' );
        if ( idxDash < 0 ) {
            return null;
        }
        final int idxDot = sessionId.indexOf( '.' );
        if ( idxDot < 0 ) {
            return sessionId.substring( idxDash + 1 );
        } else if ( idxDot < idxDash ) /* The dash was part of the jvmRoute */ {
            return null;
        } else {
            return sessionId.substring( idxDash + 1, idxDot );
        }
    }

    /**
     * Extract the jvm route from the given session id if existing.
     *
     * @param sessionId
     *            the session id possibly including the memcached id and eventually the
     *            jvmRoute.
     * @return the jvm route or null if the session id didn't contain any.
     */
    public String extractJvmRoute( final String sessionId ) {
        final int idxDot = sessionId.indexOf( '.' );
        return idxDot < 0 ? null : sessionId.substring( idxDot + 1 );
    }

    /**
     * Remove the jvm route from the given session id if existing.
     *
     * @param sessionId
     *            the session id possibly including the memcached id and eventually the
     *            jvmRoute.
     * @return the session id without the jvm route.
     */
    public String stripJvmRoute( final String sessionId ) {
        final int idxDot = sessionId.indexOf( '.' );
        return idxDot < 0 ? sessionId : sessionId.substring( 0, idxDot );
    }

    /**
     * Creates the name/key that can be used for the lock stored in memcached.
     * @param sessionId the session id for that a lock key shall be created.
     * @return a String.
     */
    public String createLockName( final String sessionId ) {
        if ( sessionId == null ) {
            throw new IllegalArgumentException( "The sessionId must not be null." );
        }
        return "lock:" + _storageKeyFormat.format(sessionId);
    }

    /**
     * Creates the name/key that can be used for storing the encoded session validity information.
     * @param origKey the session id (or validity info key) for that a key shall be created.
     * @return a String.
     */

    public String createValidityInfoKeyName( final String origKey ) {
        if ( origKey == null ) {
            throw new IllegalArgumentException( "The sessionId must not be null." );
        }
        return "validity:" + _storageKeyFormat.format(origKey);
    }

    /**
     * Creates the name/key that is used for the data (session or validity info)
     * that is additionally stored in a secondary memcached node for non-sticky sessions.
     * @param origKey the session id (or validity info key) for that a key shall be created.
     * @return a String.
     */
    public String createBackupKey( final String origKey ) {
        if ( origKey == null ) {
            throw new IllegalArgumentException( "The origKey must not be null." );
        }
        return BACKUP_PREFIX + _storageKeyFormat.format(origKey);
    }

    /**
     * Determines, if the given key is a backup key, if it was created via {@link #createBackupKey(String)}.
     */
    public boolean isBackupKey( final String key ) {
        return key.startsWith( BACKUP_PREFIX );
    }

}
