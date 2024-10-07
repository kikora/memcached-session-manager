package de.javakaffee.web.msm;

import de.javakaffee.web.msm.MemcachedSessionService.LockStatus;
import de.javakaffee.web.msm.storage.StorageClient;
import org.apache.catalina.connector.Request;

import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

public class LockingStrategyUriPattern extends LockingStrategy {

    private final Pattern _uriPattern;

    public LockingStrategyUriPattern(  final MemcachedSessionService manager,
                                       final MemcachedNodesManager memcachedNodesManager,
                                       final Pattern uriPattern,
                                       final StorageClient storage,
                                       final LRUCache<String, Boolean> missingSessionsCache,
                                      final boolean storeSecondaryBackup,
                                       final Statistics stats,
                                       final CurrentRequest currentRequest ) {
        super( manager, memcachedNodesManager, storage, missingSessionsCache, storeSecondaryBackup, stats, currentRequest );
        if ( uriPattern == null ) {
            throw new IllegalArgumentException( "The uriPattern is null" );
        }
        _uriPattern = uriPattern;
    }

    @Override
    protected LockStatus onBeforeLoadFromMemcached(final String sessionId ) throws InterruptedException,
            ExecutionException {

        final Request request = _currentRequest.get();

        if ( request == null ) {
            throw new RuntimeException( "There's no request set, this indicates that this findSession" +
                    "was triggered by the container which should already be handled in findSession." );
        }

        /* let's see if we should lock the session for this request
         */
        if ( _uriPattern.matcher( RequestTrackingHostValve.getURIWithQueryString( request ) ).matches() ) {
            if ( _log.isDebugEnabled() ) {
                _log.debug( "Lock request for request " + RequestTrackingHostValve.getURIWithQueryString( request ) );
            }
            return lock( sessionId );
        }

        if ( _log.isDebugEnabled() ) {
            _log.debug( "Not lock request for request " + RequestTrackingHostValve.getURIWithQueryString( request ) );
        }

        _stats.nonStickySessionsReadOnlyRequest();
        return LockStatus.LOCK_NOT_REQUIRED;

    }

}
