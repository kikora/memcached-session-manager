package de.javakaffee.web.msm;

import com.couchbase.client.CouchbaseClient;
import com.couchbase.client.CouchbaseConnectionFactoryBuilder;
import net.spy.memcached.FailureMode;
import net.spy.memcached.MemcachedClient;

public class CouchbaseClientFactory implements StorageClientFactory.CouchbaseClientFactory {

    @Override
    public MemcachedClient createCouchbaseClient(final MemcachedNodesManager memcachedNodesManager,
                                                 final String memcachedProtocol, final String username, String password, final long operationTimeout,
                                                 final long maxReconnectDelay, final Statistics statistics ) {
        try {
            // CouchbaseClient does not accept null for password
            if(password == null)
                password = "";

            // For membase connectivity: http://docs.couchbase.org/membase-sdk-java-api-reference/membase-sdk-java-started.html
            // And: http://code.google.com/p/spymemcached/wiki/Examples#Establishing_a_Membase_Connection
            final CouchbaseConnectionFactoryBuilder factory = newCouchbaseConnectionFactoryBuilder();
            factory.setOpTimeout(operationTimeout);
            factory.setMaxReconnectDelay(maxReconnectDelay);
            factory.setFailureMode(FailureMode.Redistribute);
            return new CouchbaseClient(factory.buildCouchbaseConnection(memcachedNodesManager.getCouchbaseBucketURIs(), username, password));
        } catch (final Exception e) {
            throw new RuntimeException("Could not create memcached client", e);
        }
    }

    protected CouchbaseConnectionFactoryBuilder newCouchbaseConnectionFactoryBuilder() {
        return new CouchbaseConnectionFactoryBuilder();
    }

}
