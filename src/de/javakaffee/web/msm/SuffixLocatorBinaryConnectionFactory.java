package de.javakaffee.web.msm;

import net.spy.memcached.*;
import net.spy.memcached.auth.AuthDescriptor;
import net.spy.memcached.protocol.binary.BinaryMemcachedNodeImpl;
import net.spy.memcached.protocol.binary.BinaryOperationFactory;
import net.spy.memcached.transcoders.SerializingTranscoder;
import net.spy.memcached.transcoders.Transcoder;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.List;

public final class SuffixLocatorBinaryConnectionFactory extends DefaultConnectionFactory {

    private final MemcachedNodesManager _memcachedNodesManager;
    private final SessionIdFormat _sessionIdFormat;
    private final Statistics _statistics;
    private final long _operationTimeout;
    private final long _maxReconnectDelay;
    private final AuthDescriptor _authDescriptor;

    /**
     * Creates a new instance passing an auth descriptor.
     * @param memcachedNodesManager
     *            the memcached nodes manager holding list of nodeIds
     * @param sessionIdFormat
     *            the {@link SessionIdFormat}
     */
    public SuffixLocatorBinaryConnectionFactory( final MemcachedNodesManager memcachedNodesManager, final SessionIdFormat sessionIdFormat,
                                                 final Statistics statistics, final long operationTimeout, final long maxReconnectDelay, final AuthDescriptor authDescriptor) {
        _memcachedNodesManager = memcachedNodesManager;
        _sessionIdFormat = sessionIdFormat;
        _statistics = statistics;
        _operationTimeout = operationTimeout;
        _maxReconnectDelay = maxReconnectDelay;
        _authDescriptor = authDescriptor;
    }

    /**
     * Creates a new instance.
     * @param memcachedNodesManager
     *            the memcached nodes manager holding list of nodeIds
     * @param sessionIdFormat
     *            the {@link SessionIdFormat}
     */
    public SuffixLocatorBinaryConnectionFactory( final MemcachedNodesManager memcachedNodesManager, final SessionIdFormat sessionIdFormat,
                                                 final Statistics statistics, final long operationTimeout, final long maxReconnectDelay ) {
        this(memcachedNodesManager, sessionIdFormat, statistics, operationTimeout, maxReconnectDelay, null);
    }

    /**
     * We don't want to try another memcached node and we also don't want to wait
     * until the failed node becomes available again.
     * @return
     */
    @Override
    public FailureMode getFailureMode() {
        return FailureMode.Cancel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeLocator createLocator(final List<MemcachedNode> nodes ) {
        return new SuffixBasedNodeLocator( nodes, _memcachedNodesManager, _sessionIdFormat );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transcoder<Object> getDefaultTranscoder() {
        final SerializingTranscoder transcoder = new SerializingTranscoder();
        transcoder.setCompressionThreshold( SerializingTranscoder.DEFAULT_COMPRESSION_THRESHOLD );
        return new TranscoderWrapperStatisticsSupport( _statistics, transcoder );
    }

    @Override
    /**
     * TODO = Should defaultOperationTimeout be set from the session Timeout.
     */
    public MemcachedNode createMemcachedNode(final SocketAddress sa,
                                             final SocketChannel c, final int bufSize) {
        final boolean doAuth = _authDescriptor != null;
        final long defaultOpTimeout = getOperationTimeout();
        return new BinaryMemcachedNodeImpl(sa, c, bufSize,
                createReadOperationQueue(),
                createWriteOperationQueue(),
                createOperationQueue(),
                getOpQueueMaxBlockTime(),
                doAuth, defaultOpTimeout,
                getAuthWaitTime(), this);
    }

    @Override
    public OperationFactory getOperationFactory() {
        return new BinaryOperationFactory();
    }

    @Override
    public long getOperationTimeout() {
        return _operationTimeout;
    }

    @Override
    public long getMaxReconnectDelay() {
        return _maxReconnectDelay;
    }

    @Override
    public AuthDescriptor getAuthDescriptor() {
        return _authDescriptor;
    }

}
