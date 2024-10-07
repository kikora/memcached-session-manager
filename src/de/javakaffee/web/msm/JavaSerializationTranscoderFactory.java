package de.javakaffee.web.msm;

import de.javakaffee.web.msm.MemcachedSessionService.SessionManager;

public class JavaSerializationTranscoderFactory implements TranscoderFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionAttributesTranscoder createTranscoder( final SessionManager manager ) {
        return new JavaSerializationTranscoder( manager );
    }

    /**
     * If <code>copyCollectionsForSerialization</code> is set to <code>true</code>,
     * an {@link UnsupportedOperationException} will be thrown, as java serialization
     * cannot be changed and it does not copy collections for serialization.
     *
     * @param copyCollectionsForSerialization the copyCollectionsForSerialization value
     */
    @Override
    public void setCopyCollectionsForSerialization( final boolean copyCollectionsForSerialization ) {
        if ( copyCollectionsForSerialization ) {
            throw new UnsupportedOperationException(
                    "Java serialization cannot be changed - it does not copy collections for serialization." );
        }
    }

    /**
     * Throws an {@link UnsupportedOperationException}, as java serialization
     * does not support custom xml format.
     *
     * @param customConverterClassNames a list of class names or <code>null</code>.
     */
    @Override
    public void setCustomConverterClassNames( final String[] customConverterClassNames ) {
        if ( customConverterClassNames != null && customConverterClassNames.length > 0 ) {
            throw new UnsupportedOperationException( "Java serialization does not support custom converter." );
        }
    }

}
