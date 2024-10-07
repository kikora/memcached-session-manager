package de.javakaffee.web.msm;

import de.javakaffee.web.msm.storage.MemcachedStorageClient;
import de.javakaffee.web.msm.storage.RedisStorageClient;
import de.javakaffee.web.msm.storage.StorageClient;
import net.spy.memcached.*;
import net.spy.memcached.auth.AuthDescriptor;
import net.spy.memcached.auth.PlainCallbackHandler;

public class StorageClientFactory {

    public static final String PROTOCOL_BINARY = "binary";

    static interface CouchbaseClientFactory {
        MemcachedClient createCouchbaseClient(MemcachedNodesManager memcachedNodesManager,
                                              String memcachedProtocol, String username, String password, long operationTimeout,
                                              long maxReconnectDelay, Statistics statistics );
    }

    protected StorageClient createStorageClient(final MemcachedNodesManager memcachedNodesManager,
                                                final String memcachedProtocol, final String username, final String password, final long operationTimeout,
                                                final long maxReconnectDelay, final Statistics statistics ) {
        try {
            if (memcachedNodesManager.isRedisConfig()) {
                return new RedisStorageClient(memcachedNodesManager.getMemcachedNodes(), operationTimeout);
            }
            final ConnectionType connectionType = ConnectionType.valueOf(memcachedNodesManager.isCouchbaseBucketConfig(), username, password);
            if (connectionType.isCouchbaseBucketConfig()) {
                return new MemcachedStorageClient(MemcachedHelper.createCouchbaseClient(memcachedNodesManager, memcachedProtocol, username, password,
                        operationTimeout, maxReconnectDelay, statistics));
            }
            final ConnectionFactory connectionFactory = MemcachedHelper.createConnectionFactory(memcachedNodesManager, connectionType, memcachedProtocol,
                    username, password, operationTimeout, maxReconnectDelay, statistics);
            return new MemcachedStorageClient(new MemcachedClient(connectionFactory, memcachedNodesManager.getAllMemcachedAddresses()));
        } catch (final Exception e) {
            throw new RuntimeException("Could not create memcached client", e);
        }
    }

    // keep memcached stuff in it's own class, so that classes from spymemcached are not loaded necessarily and don't
    // cause CNFE if spymemcached is not in the classpath.
    static class MemcachedHelper {

        static MemcachedClient createCouchbaseClient(final MemcachedNodesManager memcachedNodesManager,
                                                     final String memcachedProtocol, final String username, final String password, final long operationTimeout,
                                                     final long maxReconnectDelay, final Statistics statistics) {
            try {
                final CouchbaseClientFactory factory = Class.forName("de.javakaffee.web.msm.CouchbaseClientFactory").asSubclass(CouchbaseClientFactory.class).newInstance();
                return factory.createCouchbaseClient(memcachedNodesManager, memcachedProtocol, username, password, operationTimeout, maxReconnectDelay, statistics);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        static ConnectionFactory createConnectionFactory(final MemcachedNodesManager memcachedNodesManager,
                                                         final ConnectionType connectionType, final String memcachedProtocol, final String username, final String password, final long operationTimeout,
                                                         final long maxReconnectDelay, final Statistics statistics ) {
            if (PROTOCOL_BINARY.equals( memcachedProtocol )) {
                if (connectionType.isSASL()) {
                    final AuthDescriptor authDescriptor = new AuthDescriptor(new String[]{"PLAIN"}, new PlainCallbackHandler(username, password));
                    return memcachedNodesManager.isEncodeNodeIdInSessionId()
                            ? new SuffixLocatorBinaryConnectionFactory( memcachedNodesManager,
                            memcachedNodesManager.getSessionIdFormat(), statistics, operationTimeout, maxReconnectDelay,
                            authDescriptor)
                            : new ConnectionFactoryBuilder().setProtocol(ConnectionFactoryBuilder.Protocol.BINARY)
                            .setAuthDescriptor(authDescriptor)
                            .setOpTimeout(operationTimeout)
                            .setMaxReconnectDelay(maxReconnectDelay)
                            .build();
                }
                else {
                    return memcachedNodesManager.isEncodeNodeIdInSessionId() ? new SuffixLocatorBinaryConnectionFactory( memcachedNodesManager,
                            memcachedNodesManager.getSessionIdFormat(),
                            statistics, operationTimeout, maxReconnectDelay ) : new BinaryConnectionFactory() {
                        @Override
                        public long getOperationTimeout() {
                            return operationTimeout;
                        }
                        @Override
                        public long getMaxReconnectDelay() {
                            return maxReconnectDelay;
                        }
                    };
                }
            }
            return memcachedNodesManager.isEncodeNodeIdInSessionId()
                    ? new SuffixLocatorConnectionFactory( memcachedNodesManager, memcachedNodesManager.getSessionIdFormat(), statistics, operationTimeout, maxReconnectDelay )
                    : new DefaultConnectionFactory() {
                @Override
                public long getOperationTimeout() {
                    return operationTimeout;
                }
                @Override
                public long getMaxReconnectDelay() {
                    return maxReconnectDelay;
                }
            };
        }

    }

    static class ConnectionType {

        private final boolean couchbaseBucketConfig;
        private final String username;
        private final String password;
        public ConnectionType(final boolean couchbaseBucketConfig, final String username, final String password) {
            this.couchbaseBucketConfig = couchbaseBucketConfig;
            this.username = username;
            this.password = password;
        }
        public static ConnectionType valueOf(final boolean couchbaseBucketConfig, final String username, final String password) {
            return new ConnectionType(couchbaseBucketConfig, username, password);
        }
        boolean isCouchbaseBucketConfig() {
            return couchbaseBucketConfig;
        }
        boolean isSASL() {
            return !couchbaseBucketConfig && !isBlank(username) && !isBlank(password);
        }
        boolean isDefault() {
            return !isCouchbaseBucketConfig() && !isSASL();
        }

        boolean isBlank(final String value) {
            return value == null || value.trim().length() == 0;
        }
    }

}
