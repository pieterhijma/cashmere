/* $Id: BodiesInterface.java 6136 2007-08-22 09:54:28Z rob $ */

import ibis.cashmere.WriteMethodsInterface;

public interface BodiesInterface extends WriteMethodsInterface {
    public void updateBodies(BodyUpdates b, int iteration);
}
