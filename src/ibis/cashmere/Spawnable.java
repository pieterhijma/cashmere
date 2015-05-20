/* $Id: Spawnable.java 2928 2005-02-18 15:13:09Z reeuwijk $ */

package ibis.cashmere;

/**
 * The marker interface that indicates which methods of a class are
 * spawnable by the Cashmere divide-and-conquer environment. Use this interface to
 * mark the methods that may be spawned. The way to do this is to create an
 * interface that extends <code>ibis.cashmere.Spawnable</code> and specifies the
 * methods that may be spawned. The interface extends java.io.Serializable
 * because the "this" parameter is also sent across the network when work is
 * transferred.
 */
public interface Spawnable extends java.io.Serializable {
    // just a marker interface
}
