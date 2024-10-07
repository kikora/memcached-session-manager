package de.javakaffee.web.msm.storage;

import java.util.concurrent.Future;

public interface StorageClient {
    /**
     * <p>
     * Adds an object to the cache iff it does not exist already.
     * The operation is performed asynchronously if the underlying implementation supports it.
     * </p>
     * <p>
     * The expiration is defined according to the memcached protocol as follows: The actual value sent may either be
     * Unix time (number of seconds since January 1, 1970, as a 32-bit value), or a number of seconds starting from
     * current time. In the latter case, this number of seconds may not exceed 60*60*24*30
     * (number of seconds in 30 days); if the number sent by a client is larger than that, the server will consider it
     * to be real Unix time value rather than an offset from current time.
     * </p>
     *
     * @param key object key
     * @param exp object expiration
     * @param o object bytes to store
     *
     * @return a future representing the processing of this operation.
     *         If the boolean value returned by the future is <code>true</code>, the key was added successfully.
     *         If the boolean value returned by the future is <code>false</code>, the key was already there.
     */
    Future<Boolean> add(String key, int exp, byte[] o);

    /**
     * <p>
     * Sets an object in the cache regardless of any existing value.
     * The operation is performed asynchronously if the underlying implementation supports it.
     * </p>
     * <p>
     * The expiration is defined according to the memcached protocol as follows: The actual value sent may either be
     * Unix time (number of seconds since January 1, 1970, as a 32-bit value), or a number of seconds starting from
     * current time. In the latter case, this number of seconds may not exceed 60*60*24*30
     * (number of seconds in 30 days); if the number sent by a client is larger than that, the server will consider it
     * to be real Unix time value rather than an offset from current time.
     * </p>
     *
     * @param key object key
     * @param exp object expiration
     * @param o object bytes to store
     *
     * @return a future representing the processing of this operation. The boolean value indicates whether the value
     *         was set successfully. Setting a value can fail e.g. if the memory limit of the storage was reached.
     */
    Future<Boolean> set(String key, int exp, byte[] o);

    /**
     * Gets an object by key.
     *
     * @param key object key
     *
     * @return object bytes or <code>null</code> if an object with the given key does not exist
     */
    byte[] get(String key);

    /**
     * Deletes the given key from the cache.
     * The operation is performed asynchronously if the underlying implementation supports it.
     *
     * @param key object key
     *
     * @return a future representing the processing of this operation. The boolean value indicates whether the value
     *         was deleted successfully. The main reason why <code>false</code> would be returned is if the value to
     *         delete does not exist.
     */
    Future<Boolean> delete(String key);

    /**
     * Shuts this client down immediately.
     */
    void shutdown();
}
