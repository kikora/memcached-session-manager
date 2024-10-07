package de.javakaffee.web.msm;

import de.javakaffee.web.msm.MemcachedSessionService.LockStatus;
import de.javakaffee.web.msm.storage.StorageClient;

import java.util.concurrent.ExecutionException;

public class LockingStrategyAll extends LockingStrategy {

    public LockingStrategyAll(  final MemcachedSessionService manager,
                                final MemcachedNodesManager memcachedNodesManager,
                                final StorageClient memcached,
                                final LRUCache<String, Boolean> missingSessionsCache,
                               final boolean storeSecondaryBackup,
                                final Statistics stats,
                                final CurrentRequest currentRequest ) {
        super( manager, memcachedNodesManager, memcached, missingSessionsCache, storeSecondaryBackup, stats, currentRequest );
    }

    @Override
    protected LockStatus onBeforeLoadFromMemcached(final String sessionId ) throws InterruptedException, ExecutionException {
        return lock( sessionId );
    }

}
