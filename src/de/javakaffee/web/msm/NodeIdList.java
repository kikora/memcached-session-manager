package de.javakaffee.web.msm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NodeIdList extends ArrayList<String> {

    private static final long serialVersionUID = 2585919426234285289L;

    public NodeIdList(  final String ... nodeIds ) {
        super( Arrays.asList( nodeIds ) );
    }

    public NodeIdList(  final List<String> nodeIds ) {
        super( nodeIds );
    }


    public static NodeIdList create(  final String ... nodeIds ) {
        return new NodeIdList( nodeIds );
    }

    /**
     * Get the next node id for the given one. For the last node id
     * the first one is returned.
     * If this list contains only a single node, conceptionally there's no next node
     * so that <code>null</code> is returned.
     * @return the next node id or <code>null</code> if there's no next node id.
     * @throws IllegalArgumentException thrown if the given nodeId is not part of this list.
     */
    public String getNextNodeId(  final String nodeId ) throws IllegalArgumentException {
        final int idx = indexOf( nodeId );
        if ( idx < 0 ) {
            throw new IllegalArgumentException( "The given node id "+ nodeId +" is not part of this list " + toString() );
        }
        if ( size() == 1 ) {
            return null;
        }
        return ( idx == size() - 1 ) ? get( 0 ) : get( idx + 1 );
    }

}
