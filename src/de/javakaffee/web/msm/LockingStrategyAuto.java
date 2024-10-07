package de.javakaffee.web.msm;

import de.javakaffee.web.msm.BackupSessionService.SimpleFuture;
import de.javakaffee.web.msm.BackupSessionTask.BackupResult;
import de.javakaffee.web.msm.MemcachedSessionService.LockStatus;
import de.javakaffee.web.msm.storage.StorageClient;
import org.apache.catalina.connector.Request;

import java.util.concurrent.*;

public class LockingStrategyAuto extends LockingStrategy {

    private final ExecutorService _requestPatternDetectionExecutor;
    private final ReadOnlyRequestsCache _readOnlyRequestCache;

    public LockingStrategyAuto(  final MemcachedSessionService manager,
                                 final MemcachedNodesManager memcachedNodesManager,
                                 final StorageClient storage,
                                 final LRUCache<String, Boolean> missingSessionsCache,
                                final boolean storeSecondaryBackup,
                                 final Statistics stats,
                                 final CurrentRequest currentRequest ) {
        super( manager, memcachedNodesManager, storage, missingSessionsCache, storeSecondaryBackup, stats, currentRequest );
        _requestPatternDetectionExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("msm-req-pattern-detector"));
        _readOnlyRequestCache = new ReadOnlyRequestsCache();
    }

    @Override
    public void registerReadonlyRequest(final String requestId) {
        _readOnlyRequestCache.readOnlyRequest( requestId );
    }

    @Override
    protected void onBackupWithoutLoadedSession(  final String sessionId,  final String requestId,
                                                  final BackupSessionService backupSessionService ) {

        if ( !_sessionIdFormat.isValid( sessionId ) ) {
            return;
        }

        super.onBackupWithoutLoadedSession( sessionId, requestId, backupSessionService );

        _readOnlyRequestCache.readOnlyRequest( requestId );
    }

    @Override
    protected void onAfterBackupSession( final MemcachedBackupSession session, final boolean backupWasForced,
                                         final Future<BackupResult> result,
                                         final String requestId,
                                         final BackupSessionService backupSessionService ) {

        if ( !_sessionIdFormat.isValid( session.getIdInternal() ) ) {
            return;
        }

        super.onAfterBackupSession( session, backupWasForced, result, requestId, backupSessionService );

        final Callable<Void> task = new Callable<Void>() {

            @Override
            public Void call() {
                try {
                    if ( result.get().getStatus() == BackupResultStatus.SKIPPED ) {
                        _readOnlyRequestCache.readOnlyRequest( requestId );
                    } else {
                        _readOnlyRequestCache.modifyingRequest( requestId );
                    }
                } catch ( final Exception e ) {
                    _readOnlyRequestCache.modifyingRequest( requestId );
                }
                return null;
            }

        };
        /* A simple future does not need to go through the executor, but we can process the result right now.
         */
        if ( result instanceof SimpleFuture) {
            try {
                task.call();
            } catch ( final Exception e ) { /* caught in the callable */ }
        }
        else {
            _requestPatternDetectionExecutor.submit( task );
        }
    }

    @Override
    protected LockStatus onBeforeLoadFromMemcached(final String sessionId ) throws InterruptedException,
            ExecutionException {

        final Request request = _currentRequest.get();

        if ( request == null ) {
            throw new RuntimeException( "There's no request set, this indicates that this findSession" +
                    "was triggered by the container which should already be handled in findSession." );
        }

        /* lets see if we can skip the locking as we consider this beeing a readonly request
         */
        if ( _readOnlyRequestCache.isReadOnlyRequest( RequestTrackingHostValve.getURIWithQueryString( request ) ) ) {
            if ( _log.isDebugEnabled() ) {
                _log.debug( "Not getting lock for readonly request " + RequestTrackingHostValve.getURIWithQueryString( request ) );
            }
            _stats.nonStickySessionsReadOnlyRequest();
            return LockStatus.LOCK_NOT_REQUIRED;
        }

        return lock( sessionId );

    }

}
