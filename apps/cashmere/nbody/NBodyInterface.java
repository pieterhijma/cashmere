/* $Id: */

import ibis.cashmere.*;

public interface NBodyInterface extends Spawnable {
    
    public BodyUpdates NBodySO(int lo, int hi, int iteration, Bodies bodies);
}
