package de.javakaffee.web.msm;

import de.javakaffee.web.msm.BackupSessionService.SimpleFuture;
import de.javakaffee.web.msm.BackupSessionTask.BackupResult;
import de.javakaffee.web.msm.MemcachedNodesManager.StorageClientCallback;
import de.javakaffee.web.msm.storage.StorageClient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

import static de.javakaffee.web.msm.Statistics.StatsType.*;

public class DummyMemcachedSessionService<T extends MemcachedSessionService.SessionManager> extends MemcachedSessionService {

    private final Map<String,byte[]> _sessionData = new ConcurrentHashMap<String, byte[]>();
    private final ExecutorService _executorService = Executors.newSingleThreadExecutor(new NamedThreadFactory("dummy-msm"));

    public DummyMemcachedSessionService( final T manager ) {
        super( manager );
    }

    @Override
    protected StorageClient createStorageClient(final MemcachedNodesManager memcachedNodesManager,
                                                final Statistics statistics ) {
        return null;
    }

    @Override
    protected StorageClientCallback createStorageClientCallback() {
        return new StorageClientCallback() {
            @Override
            public byte[] get(final String key) {
                return null;
            }
        };
    }

    @Override
    protected void deleteFromMemcached(final String sessionId) {
        // no memcached access
    }

    public Future<BackupResult> backupSession(final String sessionId, final boolean sessionIdChanged, final String requestId ) {

        final MemcachedBackupSession session = _manager.getSessionInternal( sessionId );

        if ( session == null ) {
            if(_log.isDebugEnabled())
                _log.debug( "No session found in session map for " + sessionId );

            return new SimpleFuture<BackupResult>( BackupResult.SKIPPED );
        }

        _log.info( "Serializing session data for session " + session.getIdInternal() );
        final long startSerialization = System.currentTimeMillis();
        final byte[] data = _transcoderService.serializeAttributes( (MemcachedBackupSession) session, ((MemcachedBackupSession) session).getAttributesFiltered() );
        _log.info( String.format( "Serializing %1$,.3f kb session data for session %2$s took %3$d ms.",
                (double)data.length / 1000, session.getIdInternal(), System.currentTimeMillis() - startSerialization ) );
        _sessionData.put( session.getIdInternal(), data );
        _statistics.registerSince( ATTRIBUTES_SERIALIZATION, startSerialization );
        _statistics.register( CACHED_DATA_SIZE, data.length );
        return new SimpleFuture<BackupResult>( new BackupResult( BackupResultStatus.SUCCESS ) );
    }

    @Override
    public MemcachedBackupSession findSession( final String id ) throws IOException {
        final MemcachedBackupSession result = super.findSession( id );
        if ( result != null ) {
            final byte[] data = _sessionData.remove( id );
            if ( data != null ) {
                _executorService.submit( new SessionDeserialization( id, data ) );
            }
        }
        return result;
    }

    @Override
    protected MemcachedBackupSession loadFromMemcachedWithCheck( final String sessionId ) {
        return null;
    }

    @Override
    protected void updateExpirationInMemcached() {
    }

    private final class SessionDeserialization implements Callable<Void> {

        private final String _id;
        private final byte[] _data;

        private SessionDeserialization( final String id, final byte[] data ) {
            _id = id;
            _data = data;
        }

        @Override
        public Void call() throws Exception {
            _log.info( String.format( "Deserializing %1$,.3f kb session data for session %2$s (asynchronously).", (double)_data.length / 1000, _id ) );
            final long startDeserialization = System.currentTimeMillis();
            try {
                _transcoderService.deserializeAttributes( _data );
            } catch( final Exception e ) {
                _log.warn( "Could not deserialize session data.", e );
            }
            _log.info( String.format( "Deserializing %1$,.3f kb session data for session %2$s took %3$d ms.",
                    (double)_data.length / 1000, _id, System.currentTimeMillis() - startDeserialization ) );
            _statistics.registerSince( LOAD_FROM_MEMCACHED, startDeserialization );
            return null;
        }
    }

}
