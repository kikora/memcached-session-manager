package de.javakaffee.web.msm;

import java.util.concurrent.ConcurrentMap;

public interface SessionAttributesTranscoder {


    byte[] serializeAttributes( final MemcachedBackupSession session, final ConcurrentMap<String, Object> attributes );


    ConcurrentMap<String, Object> deserializeAttributes(final byte[] data );

}
