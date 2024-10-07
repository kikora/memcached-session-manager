package de.javakaffee.web.msm;

import de.javakaffee.web.msm.MemcachedSessionService.LockStatus;
import de.javakaffee.web.msm.storage.StorageClient;

import java.util.concurrent.ExecutionException;

public class LockingStrategyNone extends LockingStrategy {

    public LockingStrategyNone(  final MemcachedSessionService manager,
                                 final MemcachedNodesManager memcachedNodesManager,
                                 final StorageClient storage,
                                 final LRUCache<String, Boolean> missingSessionsCache,
                                final boolean storeSecondaryBackup,
                                 final Statistics stats,
                                 final CurrentRequest currentRequest ) {
        super( manager, memcachedNodesManager, storage, missingSessionsCache, storeSecondaryBackup, stats, currentRequest );
    }

    @Override
    protected LockStatus onBeforeLoadFromMemcached( final String sessionId ) throws InterruptedException, ExecutionException {
        return LockStatus.LOCK_NOT_REQUIRED;
    }

}
