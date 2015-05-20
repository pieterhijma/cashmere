/* $Id: SOInvocationRecord.java 3862 2006-06-20 14:14:19Z rob $ */

package ibis.cashmere.impl.sharedObjects;

import ibis.cashmere.SharedObject;

public abstract class SOInvocationRecord implements java.io.Serializable {

    private String objectId;

    public SOInvocationRecord(String objectId) {
        this.objectId = objectId;
    }

    protected abstract void invoke(SharedObject object);

    protected String getObjectId() {
        return objectId;
    }
}
