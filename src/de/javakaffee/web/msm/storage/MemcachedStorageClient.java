package de.javakaffee.web.msm.storage;

import net.spy.memcached.CachedData;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.transcoders.Transcoder;

import java.util.concurrent.Future;

public class MemcachedStorageClient implements StorageClient {
    private MemcachedClient _memcached;

    /**
     * Creates a <code>MemcachedStorageClient</code> instance with the given memcached client.
     *
     * @param memcached underlying memcached client
     */
    public MemcachedStorageClient(MemcachedClient memcached) {
        if (memcached == null)
            throw new NullPointerException("Param \"memcached\" may not be null");

        _memcached = memcached;
    }

    /**
     * Get underlying memcached client instance.
     */
    public MemcachedClient getMemcachedClient() {
        return _memcached;
    }

    @Override
    public Future<Boolean> add(String key, int exp, byte[] o) {
        return _memcached.add(key, exp, o, ByteArrayTranscoder.INSTANCE);
    }

    @Override
    public Future<Boolean> set(String key, int exp, byte[] o) {
        return _memcached.set(key, exp, o, ByteArrayTranscoder.INSTANCE);
    }

    @Override
    public byte[] get(String key) {
        return _memcached.get(key, ByteArrayTranscoder.INSTANCE);
    }

    @Override
    public Future<Boolean> delete(String key) {
        return _memcached.delete(key);
    }

    @Override
    public void shutdown() {
        _memcached.shutdown();
    }

    /**
     * Transcoder used by this class to store the byte array data.
     */
    public static class ByteArrayTranscoder implements Transcoder<byte[]> {
        /**
         * Transcoder singleton instance.
         */
        public static final ByteArrayTranscoder INSTANCE = new ByteArrayTranscoder();

        @Override
        public boolean asyncDecode(CachedData d) {
            return false;
        }

        @Override
        public byte[] decode(CachedData d) {
            return d.getData();
        }

        @Override
        public CachedData encode(byte[] o) {
            return new CachedData(0, o, getMaxSize());
        }

        @Override
        public int getMaxSize() {
            return CachedData.MAX_SIZE;
        }
    }
}
