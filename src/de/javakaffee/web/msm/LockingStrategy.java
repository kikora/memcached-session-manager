package de.javakaffee.web.msm;

import de.javakaffee.web.msm.BackupSessionTask.BackupResult;
import de.javakaffee.web.msm.MemcachedSessionService.LockStatus;
import de.javakaffee.web.msm.storage.StorageClient;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.util.concurrent.*;
import java.util.regex.Pattern;

import static de.javakaffee.web.msm.MemcachedUtil.toMemcachedExpiration;
import static de.javakaffee.web.msm.SessionValidityInfo.decode;
import static de.javakaffee.web.msm.SessionValidityInfo.encode;
import static de.javakaffee.web.msm.Statistics.StatsType.*;
import static java.lang.Math.min;
import static java.lang.Thread.sleep;

public abstract class LockingStrategy {

    public static enum LockingMode {
        /** Sessions are never locked. */
        NONE,
        /** Sessions are locked for each request. */
        ALL,
        /** Readonly requests are tracked and for requests that modify the session the session is locked. */
        AUTO,
        /** The application explicitely manages locks */
        APP,
        /** The session is locked for configured request patterns **/
        URI_PATTERN
    }

    protected static final byte[] LOCK_VALUE = new byte[] { 'l', 'o', 'c', 'k', 'e', 'd' };
    protected static final byte[] BYTE_1 = new byte[] { 1 };
    protected static final int LOCK_RETRY_INTERVAL = 10;
    protected static final int LOCK_MAX_RETRY_INTERVAL = 500;

    protected final Log _log = LogFactory.getLog( getClass() );

    protected MemcachedSessionService _manager;
    protected final StorageClient _storage;
    protected LRUCache<String, Boolean> _missingSessionsCache;
    protected final SessionIdFormat _sessionIdFormat;
    private final ExecutorService _executor;
    private final boolean _storeSecondaryBackup;
    protected final Statistics _stats;
    protected final CurrentRequest _currentRequest;
    protected final StorageKeyFormat _storageKeyFormat;

    protected LockingStrategy(  final MemcachedSessionService manager,
                                final MemcachedNodesManager memcachedNodesManager,
                                final StorageClient storage,
                                final LRUCache<String, Boolean> missingSessionsCache, final boolean storeSecondaryBackup,
                                final Statistics stats,
                                final CurrentRequest currentRequest ) {
        _manager = manager;
        _storage = storage;
        _missingSessionsCache = missingSessionsCache;
        _sessionIdFormat = memcachedNodesManager.getSessionIdFormat();
        _storeSecondaryBackup = storeSecondaryBackup;
        _stats = stats;
        _currentRequest = currentRequest;
        _storageKeyFormat = memcachedNodesManager.getStorageKeyFormat();
        _executor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors(), new NamedThreadFactory("msm-2ndary-backup") );
    }

    /**
     * Creates the appropriate {@link LockingStrategy} for the given {@link LockingMode}.
     */

    public static LockingStrategy create(  final LockingMode lockingMode,  final Pattern uriPattern,
                                           final StorageClient storage,  final MemcachedSessionService manager,
                                           final MemcachedNodesManager memcachedNodesManager,
                                           final LRUCache<String, Boolean> missingSessionsCache, final boolean storeSecondaryBackup,
                                           final Statistics stats,
                                           final CurrentRequest currentRequest ) {
        if ( lockingMode == null || storage == null ) {
            return null;
        }
        switch ( lockingMode ) {
            case ALL:
                return new LockingStrategyAll( manager, memcachedNodesManager, storage, missingSessionsCache, storeSecondaryBackup, stats, currentRequest );
            case AUTO:
                return new LockingStrategyAuto( manager, memcachedNodesManager, storage, missingSessionsCache, storeSecondaryBackup, stats, currentRequest );
            case URI_PATTERN:
                return new LockingStrategyUriPattern( manager, memcachedNodesManager, uriPattern, storage, missingSessionsCache, storeSecondaryBackup,
                        stats, currentRequest );
            case NONE:
                return new LockingStrategyNone( manager, memcachedNodesManager, storage, missingSessionsCache, storeSecondaryBackup, stats, currentRequest );
            default:
                throw new IllegalArgumentException( "LockingMode not yet supported: " + lockingMode );
        }
    }

    /**
     * Shutdown this lockingStrategy, which frees all resources / releases threads.
     */
    public void shutdown() {
        _executor.shutdown();
    }

    protected LockStatus lock( final String sessionId ) {
        return lock( sessionId, _manager.getOperationTimeout(), TimeUnit.MILLISECONDS );
    }

    protected LockStatus lock( final String sessionId, final long timeout, final TimeUnit timeUnit ) {
        if ( _log.isDebugEnabled() ) {
            _log.debug( "Locking session " + sessionId );
        }
        final long start = System.currentTimeMillis();
        try {
            acquireLock( sessionId, LOCK_RETRY_INTERVAL, LOCK_MAX_RETRY_INTERVAL, timeUnit.toMillis( timeout ),
                    System.currentTimeMillis() );
            _stats.registerSince( ACQUIRE_LOCK, start );
            if ( _log.isDebugEnabled() ) {
                _log.debug( "Locked session " + sessionId );
            }
            return LockStatus.LOCKED;
        } catch ( final TimeoutException e ) {
            _log.warn( "Reached timeout when trying to aquire lock for session " + sessionId
                    + ". Will use this session without this lock." );
            _stats.registerSince( ACQUIRE_LOCK_FAILURE, start );
            return LockStatus.COULD_NOT_AQUIRE_LOCK;
        } catch ( final InterruptedException e ) {
            Thread.currentThread().interrupt();
            throw new RuntimeException( "Got interrupted while trying to lock session.", e );
        } catch ( final ExecutionException e ) {
            _log.warn( "An exception occurred when trying to aquire lock for session " + sessionId );
            _stats.registerSince( ACQUIRE_LOCK_FAILURE, start );
            return LockStatus.COULD_NOT_AQUIRE_LOCK;
        }
    }

    protected void acquireLock(  final String sessionId, final long retryInterval, final long maxRetryInterval,
                                final long timeout, final long start ) throws InterruptedException, ExecutionException, TimeoutException {
        final Future<Boolean> result = _storage.add( _sessionIdFormat.createLockName( sessionId ), _manager.getLockExpiration(), LOCK_VALUE );
        if ( result.get().booleanValue() ) {
            if ( _log.isDebugEnabled() ) {
                _log.debug( "Locked session " + sessionId );
            }
            return;
        }
        else {
            checkTimeoutAndWait( sessionId, retryInterval, timeout, start );
            acquireLock( sessionId, min( retryInterval * 2, maxRetryInterval ), maxRetryInterval, timeout, start );
        }
    }

    protected void checkTimeoutAndWait(  final String sessionId, final long timeToWait,
                                        final long timeout, final long start ) throws TimeoutException,
            InterruptedException {
        if ( System.currentTimeMillis() >= start + timeout ) {
            throw new TimeoutException( "Reached timeout when trying to aquire lock for session " + sessionId );
        }
        if ( _log.isDebugEnabled() ) {
            _log.debug( "Could not aquire lock for session " + sessionId + ", waiting " + timeToWait + " millis now..." );
        }
        sleep( timeToWait );
    }

    protected void releaseLock(  final String sessionId ) {
        try {
            if ( _log.isDebugEnabled() ) {
                _log.debug( "Releasing lock for session " + sessionId );
            }
            final long start = System.currentTimeMillis();
            _storage.delete( _sessionIdFormat.createLockName( sessionId ) ).get();
            _stats.registerSince( RELEASE_LOCK, start );
        } catch ( final Exception e ) {
            _log.warn( "Caught exception when trying to release lock for session " + sessionId, e );
        }
    }

    /**
     * Used to register the given requestId as readonly request (for mode auto), so that further
     * requests like this won't acquire the lock.
     * @param requestId the uri/id of the request for that the session backup shall be performed, used for readonly tracking.
     */
    public void registerReadonlyRequest(final String requestId) {
        // default empty
    }

    /**
     * Is invoked for the backup of a non-sticky session that was not accessed for the current request.
     */
    protected void onBackupWithoutLoadedSession(  final String sessionId,  final String requestId,
                                                  final BackupSessionService backupSessionService ) {

        if ( !_sessionIdFormat.isValid( sessionId ) ) {
            return;
        }

        try {

            final long start = System.currentTimeMillis();

            final String validityKey = _sessionIdFormat.createValidityInfoKeyName( sessionId );
            final SessionValidityInfo validityInfo = loadSessionValidityInfoForValidityKey( validityKey );
            if ( validityInfo == null ) {
                _log.warn( "Found no validity info for session id " + sessionId );
                return;
            }

            final int maxInactiveInterval = validityInfo.getMaxInactiveInterval();
            final byte[] validityData = encode( maxInactiveInterval, System.currentTimeMillis(),
                    System.currentTimeMillis() );
            // fix for #88, along with the change in session.getMemcachedExpirationTimeToSet
            final int expiration = maxInactiveInterval <= 0 ? 0 : maxInactiveInterval;
            final Future<Boolean> validityResult = _storage.set( validityKey, toMemcachedExpiration(expiration), validityData );
            if ( !_manager.isSessionBackupAsync() ) {
                validityResult.get( _manager.getSessionBackupTimeout(), TimeUnit.MILLISECONDS );
            }

            /*
             * - ping session
             * - ping session backup
             * - save validity backup
             */
            final Callable<?> backupSessionTask = new OnBackupWithoutLoadedSessionTask( sessionId,
                    _storeSecondaryBackup, validityKey, validityData, maxInactiveInterval );
            _executor.submit( backupSessionTask );

            if ( _log.isDebugEnabled() ) {
                _log.debug( "Stored session validity info for session " + sessionId );
            }

            _stats.registerSince( NON_STICKY_ON_BACKUP_WITHOUT_LOADED_SESSION, start );

        } catch( final Throwable e ) {
            _log.warn( "An error when trying to load/update validity info.", e );
        }

    }

    /**
     * Is invoked after the backup of the session is initiated, it's represented by the provided backupResult. The
     * requestId is identifying the request.
     */
    protected void onAfterBackupSession(final MemcachedBackupSession session, final boolean backupWasForced,
                                        final Future<BackupResult> result, final String requestId,
                                        final BackupSessionService backupSessionService ) {

        if ( !_sessionIdFormat.isValid( session.getIdInternal() ) ) {
            return;
        }

        try {

            final long start = System.currentTimeMillis();

            final int maxInactiveInterval = session.getMaxInactiveInterval();
            final byte[] validityData = encode( maxInactiveInterval, session.getLastAccessedTimeInternal(),
                    session.getThisAccessedTimeInternal() );
            final String validityKey = _sessionIdFormat.createValidityInfoKeyName( session.getIdInternal() );
            // fix for #88, along with the change in session.getMemcachedExpirationTimeToSet
            final int expiration = maxInactiveInterval <= 0 ? 0 : maxInactiveInterval;
            final Future<Boolean> validityResult = _storage.set( validityKey, toMemcachedExpiration(expiration), validityData );
            if ( !_manager.isSessionBackupAsync() ) {
                // TODO: together with session backup wait not longer than sessionBackupTimeout.
                // Details: Now/here we're waiting the whole session backup timeout, even if (perhaps) some time
                // was spent before when waiting for session backup result.
                // For sync session backup it would be better to set both the session data and
                // validity info and afterwards wait for both results (but in sum no longer than sessionBackupTimeout)
                validityResult.get( _manager.getSessionBackupTimeout(), TimeUnit.MILLISECONDS );
            }
            if ( _log.isDebugEnabled() ) {
                _log.debug( "Stored session validity info for session " + session.getIdInternal() );
            }

            /* The following task are performed outside of the request thread (includes waiting for the backup result):
             * - ping session if the backup was skipped (depends on the backup result)
             * - save secondary session backup if session was modified (backup not skipped)
             * - ping secondary session backup if the backup was skipped
             * - save secondary validity backup
             */
            final boolean pingSessionIfBackupWasSkipped = !backupWasForced;
            final boolean performAsyncTasks = pingSessionIfBackupWasSkipped || _storeSecondaryBackup;

            if ( performAsyncTasks ) {
                final Callable<?> backupSessionTask = new OnAfterBackupSessionTask( session, result,
                        pingSessionIfBackupWasSkipped, backupSessionService, _storeSecondaryBackup, validityKey, validityData );
                _executor.submit( backupSessionTask );
            }

            _stats.registerSince( NON_STICKY_AFTER_BACKUP, start );

        } catch( final Throwable e ) {
            _log.warn( "An error occurred during onAfterBackupSession.", e );
        }

    }


    protected SessionValidityInfo loadSessionValidityInfo(  final String sessionId ) {
        return loadSessionValidityInfoForValidityKey( _sessionIdFormat.createValidityInfoKeyName( sessionId ) );
    }


    protected SessionValidityInfo loadSessionValidityInfoForValidityKey(  final String validityInfoKey ) {
        final byte[] validityInfo = _storage.get( validityInfoKey );
        return validityInfo != null ? decode( validityInfo ) : null;
    }


    protected SessionValidityInfo loadBackupSessionValidityInfo(  final String sessionId ) {
        final String key = _sessionIdFormat.createValidityInfoKeyName( sessionId );
        final String backupKey = _sessionIdFormat.createBackupKey( key );
        return loadSessionValidityInfoForValidityKey( backupKey );
    }

    /**
     * Invoked before the session for this sessionId is loaded from memcached.
     */

    protected abstract LockStatus onBeforeLoadFromMemcached( String sessionId ) throws InterruptedException,
            ExecutionException;

    /**
     * Invoked after a non-sticky session is loaded from memcached, can be used to update some session fields based on
     * separately stored information (e.g. session validity info).
     *
     * @param lockStatus
     *            the {@link LockStatus} that was returned from {@link #onBeforeLoadFromMemcached(String)}.
     */
    protected void onAfterLoadFromMemcached(  final MemcachedBackupSession session,
                                              final LockStatus lockStatus ) {
        session.setLockStatus( lockStatus );

        final long start = System.currentTimeMillis();
        final SessionValidityInfo info = loadSessionValidityInfo( session.getIdInternal() );
        if ( info != null ) {
            _stats.registerSince( NON_STICKY_AFTER_LOAD_FROM_MEMCACHED, start );
            session.setLastAccessedTimeInternal( info.getLastAccessedTime() );
            session.setThisAccessedTimeInternal( info.getThisAccessedTime() );
        }
        else {
            _log.warn( "No validity info available for session " + session.getIdInternal() );
        }
    }

    /**
     * Invoked after a non-sticky session is removed from memcached.
     */
    protected void onAfterDeleteFromMemcached(  final String sessionId ) {
        final long start = System.currentTimeMillis();

        final String validityInfoKey = _sessionIdFormat.createValidityInfoKeyName( sessionId );
        _storage.delete( validityInfoKey );

        if (_storeSecondaryBackup) {
            try {
                _storage.delete(_sessionIdFormat.createBackupKey(sessionId));
                _storage.delete(_sessionIdFormat.createBackupKey(validityInfoKey));
            } catch (Exception e) {
                _log.info("Could not delete backup data for session " + sessionId + " (not critical, data will be evicted by memcached automatically).", e);
            }
        }

        _stats.registerSince( NON_STICKY_AFTER_DELETE_FROM_MEMCACHED, start );
    }

    private boolean pingSession(  final String sessionId ) throws InterruptedException {
        final Future<Boolean> touchResult = _storage.add( _storageKeyFormat.format(sessionId), 1, BYTE_1 );
        try {
            if ( touchResult.get() ) {
                _stats.nonStickySessionsPingFailed();
                _log.warn( "The session " + sessionId
                        + " should be touched in memcached, but it does not exist therein." );
                return false;
            }
            _log.debug( "The session was ping'ed successfully." );
            return true;
        } catch ( final ExecutionException e ) {
            _log.warn( "An exception occurred when trying to ping session " + sessionId, e );
            return false;
        }
    }

    private void pingSession(  final MemcachedBackupSession session,
                               final BackupSessionService backupSessionService ) throws InterruptedException {
        final Future<Boolean> touchResult = _storage.add( _storageKeyFormat.format(session.getIdInternal()), 5, BYTE_1 );
        try {
            if ( touchResult.get() ) {
                _stats.nonStickySessionsPingFailed();
                _log.warn( "The session " + session.getIdInternal()
                        + " should be touched in memcached, but it does not exist"
                        + " therein. Will store in memcached again." );
                updateSession( session, backupSessionService );
            }
            else
                _log.debug( "The session was ping'ed successfully." );
        } catch ( final ExecutionException e ) {
            _log.warn( "An exception occurred when trying to ping session " + session.getIdInternal(), e );
        }
    }

    private void updateSession(  final MemcachedBackupSession session,
                                 final BackupSessionService backupSessionService ) throws InterruptedException {
        final Future<BackupResult> result = backupSessionService.backupSession( session, true );
        try {
            if ( result.get().getStatus() != BackupResultStatus.SUCCESS ) {
                _log.warn( "Update for session (after unsuccessful ping) did not return SUCCESS, but " + result.get() );
            }
        } catch ( final ExecutionException e ) {
            _log.warn( "An exception occurred when trying to update session " + session.getIdInternal(), e );
        }
    }

    private final class OnAfterBackupSessionTask implements Callable<Void> {

        private final MemcachedBackupSession _session;
        private final Future<BackupResult> _result;
        private final boolean _pingSessionIfBackupWasSkipped;
        private final boolean _storeSecondaryBackup;
        private final BackupSessionService _backupSessionService;
        private final String _validityKey;
        private final byte[] _validityData;

        private OnAfterBackupSessionTask(  final MemcachedBackupSession session,  final Future<BackupResult> result,
                                          final boolean pingSessionIfBackupWasSkipped,
                                           final BackupSessionService backupSessionService,
                                          final boolean storeSecondaryBackup,
                                           final String validityKey,
                                           final byte[] validityData ) {
            _session = session;
            _result = result;
            _pingSessionIfBackupWasSkipped = pingSessionIfBackupWasSkipped;
            _storeSecondaryBackup = storeSecondaryBackup;
            _validityKey = validityKey;
            _validityData = validityData;
            _backupSessionService = backupSessionService;
        }

        @Override
        public Void call() throws Exception {

            final BackupResult backupResult = _result.get();

            if ( _pingSessionIfBackupWasSkipped ) {
                if ( backupResult.getStatus() == BackupResultStatus.SKIPPED ) {
                    pingSession( _session, _backupSessionService );
                }
            }

            /*
             * For non-sticky sessions we store a backup of the session in a secondary memcached node (under a special key
             * that's resolved by the SuffixBasedNodeLocator), but only when we have more than 1 memcached node configured...
             */
            if ( _storeSecondaryBackup ) {
                try {
                    if ( _log.isDebugEnabled() ) {
                        _log.debug( "Storing backup in secondary memcached for non-sticky session " + _session.getId() );
                    }
                    if ( backupResult.getStatus() == BackupResultStatus.SKIPPED ) {
                        pingSessionBackup( _session );
                    }
                    else {
                        saveSessionBackupFromResult( backupResult );
                    }

                    saveValidityBackup();
                } catch( final RuntimeException e ) {
                    _log.info( "Could not store secondary backup of session " + _session.getIdInternal(), e );
                }

            }

            return null;
        }

        public void saveSessionBackupFromResult( final BackupResult backupResult ) {
            final byte[] data = backupResult.getData();
            if ( data != null ) {
                final String key = _sessionIdFormat.createBackupKey( _session.getId() );
                _storage.set( key, toMemcachedExpiration(_session.getMemcachedExpirationTimeToSet()), data );
            }
            else {
                _log.warn( "No data set for backupResultStatus " + backupResult.getStatus() + " for sessionId "
                        + _session.getIdInternal() + ", skipping backup"
                        + " of non-sticky session in secondary memcached." );
            }
        }

        public void saveValidityBackup() {
            final String backupValidityKey = _sessionIdFormat.createBackupKey( _validityKey );
            final int maxInactiveInterval = _session.getMaxInactiveInterval();
            // fix for #88, along with the change in session.getMemcachedExpirationTimeToSet
            final int expiration = maxInactiveInterval <= 0 ? 0 : maxInactiveInterval;
            _storage.set( backupValidityKey, toMemcachedExpiration(expiration), _validityData );
        }

        private void pingSessionBackup(  final MemcachedBackupSession session ) throws InterruptedException {
            final String key = _sessionIdFormat.createBackupKey( session.getId() );
            final Future<Boolean> touchResultFuture = _storage.add( key, 5, BYTE_1 );
            try {
                final boolean touchResult = touchResultFuture.get(_manager.getOperationTimeout(), TimeUnit.MILLISECONDS);
                if ( touchResult ) {
                    _log.warn( "The secondary backup for session " + session.getIdInternal()
                            + " should be touched in memcached, but it seemed to be"
                            + " not existing. Will store in memcached again." );
                    saveSessionBackup( session, key );
                }
                else
                    _log.debug( "The secondary session backup was ping'ed successfully." );
            } catch ( final TimeoutException e ) {
                _log.warn( "The secondary backup for session " + session.getIdInternal()
                        + " could not be completed within " + _manager.getOperationTimeout() + " millis, was cancelled now." );
            } catch ( final ExecutionException e ) {
                _log.warn( "An exception occurred when trying to ping session " + session.getIdInternal(), e );
            }
        }

        public void saveSessionBackup(  final MemcachedBackupSession session,  final String key )
                throws InterruptedException {
            try {
                final byte[] data = _manager.serialize( session );
                final Future<Boolean> backupResult = _storage.set( key, toMemcachedExpiration(session.getMemcachedExpirationTimeToSet()), data );
                if ( !backupResult.get().booleanValue() ) {
                    _log.warn( "Update for secondary backup of session "+ session.getIdInternal() +" (after unsuccessful ping) did not return sucess." );
                }
            } catch ( final ExecutionException e ) {
                _log.warn( "An exception occurred when trying to update secondary session backup for " + session.getIdInternal(), e );
            }
        }
    }

    private final class OnBackupWithoutLoadedSessionTask implements Callable<Void> {

        private final String _sessionId;
        private final boolean _storeSecondaryBackup;
        private final String _validityKey;
        private final byte[] _validityData;
        private final int _maxInactiveInterval;

        private OnBackupWithoutLoadedSessionTask(  final String sessionId,
                                                  final boolean storeSecondaryBackup,
                                                   final String validityKey,
                                                   final byte[] validityData,
                                                  final int maxInactiveInterval ) {
            _sessionId = sessionId;
            _storeSecondaryBackup = storeSecondaryBackup;
            _validityKey = validityKey;
            _validityData = validityData;
            _maxInactiveInterval = maxInactiveInterval;
        }

        @Override
        public Void call() throws Exception {

            pingSession( _sessionId );

            /*
             * For non-sticky sessions we store/ping a backup of the session in a secondary memcached node (under a special key
             * that's resolved by the SuffixBasedNodeLocator), but only when we have more than 1 memcached node configured...
             */
            if ( _storeSecondaryBackup ) {
                try {

                    pingSessionBackup( _sessionId );

                    final String backupValidityKey = _sessionIdFormat.createBackupKey( _validityKey );
                    // fix for #88, along with the change in session.getMemcachedExpirationTimeToSet
                    final int expiration = _maxInactiveInterval <= 0 ? 0 : _maxInactiveInterval;
                    _storage.set( backupValidityKey, toMemcachedExpiration(expiration), _validityData );

                } catch( final RuntimeException e ) {
                    _log.info( "Could not store secondary backup of session " + _sessionId, e );
                }

            }

            return null;
        }

        private boolean pingSessionBackup(  final String sessionId ) throws InterruptedException {
            final String key = _sessionIdFormat.createBackupKey( sessionId );
            final Future<Boolean> touchResultFuture = _storage.add( key, 1, BYTE_1 );
            try {
                final boolean touchResult = touchResultFuture.get(200, TimeUnit.MILLISECONDS);
                if ( touchResult ) {
                    _log.warn( "The secondary backup for session " + sessionId
                            + " should be touched in memcached, but it seemed to be"
                            + " not existing." );
                    return false;
                }
                _log.debug( "The secondary session backup was ping'ed successfully." );
                return true;
            } catch ( final TimeoutException e ) {
                _log.warn( "The secondary backup for session " + sessionId
                        + " could not be completed within 200 millis, was cancelled now." );
                return false;
            } catch ( final ExecutionException e ) {
                _log.warn( "An exception occurred when trying to ping session " + sessionId, e );
                return false;
            }
        }
    }

    // ---------------- for testing


    ExecutorService getExecutorService() {
        return _executor;
    }

}
