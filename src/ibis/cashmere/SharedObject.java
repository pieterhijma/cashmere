/* $Id: SharedObject.java 5236 2007-03-21 10:05:37Z jason $ */

package ibis.cashmere;

import ibis.cashmere.impl.Cashmere;

/**
 * A Cashmere shared object must be of a class that extends this
 * <code>SharedObject</code> class.
 * This class exports one method that actually marks the object as shared.
 */
public class SharedObject implements java.io.Serializable {

    /** 
     * Generated
     */
    private static final long serialVersionUID = -5944640339385558907L;

    /** Identification of this shared object. */
    private String objectId;

    /** Counter for generating shared-object identifications. */
    private static int sharedObjectsCounter = 0;

    private boolean exported = false;

    private boolean isUnshared = true;

    /**
     * Creates an identification for the current object and marks it as shared.
     */
    protected SharedObject() {
        Cashmere cashmere = Cashmere.getCashmere();

        if (cashmere == null) {
            // Assuming sequential run, not rewritten code.
            return;
        }

        //create identifier
        sharedObjectsCounter++;
        objectId = "SO" + sharedObjectsCounter + "@"
            + Cashmere.getCashmere().ident;

        //add yourself to the sharedObjects hashtable
        cashmere.so.addObject(this);
    }

    /**
     * Returns true if the shared object is still local.
     * Write operations that are done while the object is still local
     * are not broadcasted.
     * As soon as an invocation record is made with this object,
     * or exportObject is called, the object is considered non-local.
     * @return true if the object is still local.
     */
    public final boolean isUnshared() {
        return isUnshared;
    }

    /**
     * This method is optional, and can be used after creating a shared object.
     * It allows Cashmere to immediately distribute a replica to all machines
     * participating in the application.
     * This way, machines won't have to ask for it later.
     */
    public void exportObject() {
        if (exported) {
            throw new RuntimeException(
                "you cannot export an object more than once.");
        }

        Cashmere cashmere = Cashmere.getCashmere();

        if (cashmere == null) {
            // Assuming sequential run, not rewritten code.
            return;
        }

        if (! isUnshared) {
            throw new RuntimeException(
                    "write method invoked before exportObject");
        }

        isUnshared = false;

        synchronized (cashmere) {
            cashmere.so.broadcastSharedObject(this);
            exported = true;
        }
    }

    public final String getObjectId() {
        return objectId;
    }

    public final String getObjectIdAndSetNonlocal() {
        isUnshared = false;
        return objectId;
    }
}
