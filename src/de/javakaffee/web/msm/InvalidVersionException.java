package de.javakaffee.web.msm;

public class InvalidVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final short _version;

    /**
     * Creates a new {@link InvalidVersionException}.
     * @param msg the error message
     * @param version the version (the first 2 bytes) read from the session data that was loaded from memcached.
     */
    public InvalidVersionException( final String msg, final short version ) {
        super( msg );
        _version = version;
    }

    /**
     * The version (the first 2 bytes) read from the session data that was loaded from memcached.
     * @return the version
     */
    public short getVersion() {
        return _version;
    }

}
