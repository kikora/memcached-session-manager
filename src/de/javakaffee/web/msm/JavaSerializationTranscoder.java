package de.javakaffee.web.msm;

import de.javakaffee.web.msm.MemcachedSessionService.SessionManager;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JavaSerializationTranscoder implements SessionAttributesTranscoder {

    private static final Log LOG = LogFactory.getLog( JavaSerializationTranscoder.class );

    private static final String EMPTY_ARRAY[] = new String[0];

    /**
     * The dummy attribute value serialized when a NotSerializableException is
     * encountered in <code>writeObject()</code>.
     */
    protected static final String NOT_SERIALIZED = "___NOT_SERIALIZABLE_EXCEPTION___";

    private final SessionManager _manager;

    public JavaSerializationTranscoder() {
        this( null );
    }

    /**
     * Constructor.
     *
     * @param manager
     *            the manager
     */
    public JavaSerializationTranscoder( final SessionManager manager ) {
        _manager = manager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serializeAttributes( final MemcachedBackupSession session, final ConcurrentMap<String, Object> attributes ) {
        if ( attributes == null ) {
            throw new NullPointerException( "Can't serialize null" );
        }

        ByteArrayOutputStream bos = null;
        ObjectOutputStream oos = null;
        try {
            bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream( bos );

            writeAttributes( session, attributes, oos );

            return bos.toByteArray();
        } catch ( final IOException e ) {
            throw new IllegalArgumentException( "Non-serializable object", e );
        } finally {
            closeSilently( bos );
            closeSilently( oos );
        }

    }

    private void writeAttributes( final MemcachedBackupSession session, final Map<String, Object> attributes,
                                  final ObjectOutputStream oos ) throws IOException {

        // Accumulate the names of serializable and non-serializable attributes
        final String keys[] = attributes.keySet().toArray( EMPTY_ARRAY );
        final List<String> saveNames = new ArrayList<String>();
        final List<Object> saveValues = new ArrayList<Object>();
        for ( int i = 0; i < keys.length; i++ ) {
            final Object value = attributes.get( keys[i] );
            if ( value == null || session.exclude( keys[i], value ) ) {
                continue;
            } else if ( value instanceof Serializable) {
                saveNames.add( keys[i] );
                saveValues.add( value );
            } else {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "Ignoring attribute '" + keys[i] + "' as it does not implement Serializable" );
                }
            }
        }

        // Serialize the attribute count and the Serializable attributes
        final int n = saveNames.size();
        oos.writeObject( Integer.valueOf( n ) );
        for ( int i = 0; i < n; i++ ) {
            oos.writeObject( saveNames.get( i ) );
            try {
                oos.writeObject( saveValues.get( i ) );
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "  storing attribute '" + saveNames.get( i ) + "' with value '" + saveValues.get( i ) + "'" );
                }
            } catch ( final NotSerializableException e ) {
                LOG.warn( _manager.getString( "standardSession.notSerializable", saveNames.get( i ), session.getIdInternal() ), e );
                oos.writeObject( NOT_SERIALIZED );
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "  storing attribute '" + saveNames.get( i ) + "' with value NOT_SERIALIZED" );
                }
            }
        }

    }

    /**
     * Get the object represented by the given serialized bytes.
     *
     * @param in
     *            the bytes to deserialize
     * @return the resulting object
     */
    @Override
    public ConcurrentMap<String, Object> deserializeAttributes(final byte[] in ) {
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bis = new ByteArrayInputStream( in );
            ois = createObjectInputStream( bis );

            final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<String, Object>();
            final int n = ( (Integer) ois.readObject() ).intValue();
            for ( int i = 0; i < n; i++ ) {
                final String name = (String) ois.readObject();
                final Object value = ois.readObject();
                if ( ( value instanceof String ) && ( value.equals( NOT_SERIALIZED ) ) ) {
                    continue;
                }
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug( "  loading attribute '" + name + "' with value '" + value + "'" );
                }
                attributes.put( name, value );
            }

            return attributes;
        } catch ( final ClassNotFoundException e ) {
            LOG.warn( "Caught CNFE decoding "+ in.length +" bytes of data", e );
            throw new TranscoderDeserializationException( "Caught CNFE decoding data", e );
        } catch ( final IOException e ) {
            LOG.warn( "Caught IOException decoding "+ in.length +" bytes of data", e );
            throw new TranscoderDeserializationException( "Caught IOException decoding data", e );
        } finally {
            closeSilently( bis );
            closeSilently( ois );
        }
    }

    private ObjectInputStream createObjectInputStream( final ByteArrayInputStream bis ) throws IOException {
        final ObjectInputStream ois;
        ClassLoader classLoader = null;
        if ( _manager != null && _manager.getContext() != null ) {
            classLoader = _manager.getContainerClassLoader();
        }
        if ( classLoader != null ) {
            ois = new CustomObjectInputStream( bis, classLoader );
        } else {
            ois = new ObjectInputStream( bis );
        }
        return ois;
    }

    private void closeSilently( final OutputStream os ) {
        if ( os != null ) {
            try {
                os.close();
            } catch ( final IOException f ) {
                // fail silently
            }
        }
    }

    private void closeSilently( final InputStream is ) {
        if ( is != null ) {
            try {
                is.close();
            } catch ( final IOException f ) {
                // fail silently
            }
        }
    }

}
