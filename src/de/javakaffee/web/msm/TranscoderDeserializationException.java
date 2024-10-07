package de.javakaffee.web.msm;

public class TranscoderDeserializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TranscoderDeserializationException() {
    }

    public TranscoderDeserializationException( final String message ) {
        super( message );
    }

    public TranscoderDeserializationException( final Throwable cause ) {
        super( cause );
    }

    public TranscoderDeserializationException( final String message, final Throwable cause ) {
        super( message, cause );
    }

}
