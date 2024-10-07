package de.javakaffee.web.msm;

import de.javakaffee.web.msm.Statistics.StatsType;
import net.spy.memcached.CachedData;
import net.spy.memcached.transcoders.Transcoder;

public class TranscoderWrapperStatisticsSupport implements Transcoder<Object> {

    private final Statistics _statistics;
    private final Transcoder<Object> _delegate;

    public TranscoderWrapperStatisticsSupport( final Statistics statistics, final Transcoder<Object> delegate ) {
        _statistics = statistics;
        _delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    public boolean asyncDecode( final CachedData cachedData ) {
        return _delegate.asyncDecode( cachedData );
    }

    /**
     * {@inheritDoc}
     */
    public Object decode( final CachedData cachedData ) {
        return _delegate.decode( cachedData );
    }

    /**
     * {@inheritDoc}
     */
    public CachedData encode( final Object object ) {
        final CachedData result = _delegate.encode( object );
        _statistics.register( StatsType.CACHED_DATA_SIZE, result.getData().length );
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public int getMaxSize() {
        return _delegate.getMaxSize();
    }

}
