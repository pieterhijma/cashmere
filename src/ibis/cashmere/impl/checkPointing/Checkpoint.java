package ibis.cashmere.impl.checkPointing;

import ibis.ipl.IbisIdentifier;
import ibis.cashmere.impl.spawnSync.ReturnRecord;
/**
 * A Checkpoint contains a ReturnRecord and an IbisIdentifier, telling which
 * node sent the checkpoint. This data is needed when the checkpoint file
 * is written;
 **/

public class Checkpoint implements java.io.Serializable {

    /**
     * if serialVersionUID is not defined here, java redefines it
     * for every new build of Cashmere. Different serialVersionUID's
     * can't be read by different Cashmere builds. That would make
     * checkpoint files created by a different cashmere build useless
     **/
    private static final long serialVersionUID = 12345;

    public ReturnRecord rr;
    
    public IbisIdentifier sender;

    public Checkpoint(ReturnRecord rr, IbisIdentifier sender){
	this.rr = rr;
	this.sender = sender;
    }
}
