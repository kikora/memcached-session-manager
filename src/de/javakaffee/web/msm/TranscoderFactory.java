package de.javakaffee.web.msm;

import de.javakaffee.web.msm.MemcachedSessionService.SessionManager;

public interface TranscoderFactory {

    /**
     * Creates a new {@link SessionAttributesTranscoder} with the given manager.
     *
     * @param manager
     *            the manager that needs to be set on deserialized sessions.
     * @return an implementation of {@link SessionAttributesTranscoder}.
     */
    SessionAttributesTranscoder createTranscoder( SessionManager manager );


    void setCopyCollectionsForSerialization( boolean copyCollectionsForSerialization );

    void setCustomConverterClassNames( String[] customConverterClassNames );

}
