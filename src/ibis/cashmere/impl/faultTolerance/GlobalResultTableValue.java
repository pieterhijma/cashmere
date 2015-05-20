/*
 * Created on Jun 1, 2006
 */
package ibis.cashmere.impl.faultTolerance;

import ibis.ipl.IbisIdentifier;
import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.spawnSync.InvocationRecord;
import ibis.cashmere.impl.spawnSync.ReturnRecord;

public class GlobalResultTableValue implements java.io.Serializable, Config {
    /** 
     * Generated
     */
    private static final long serialVersionUID = 4797223605264498290L;

    protected static final int TYPE_RESULT = 1;

    protected static final int TYPE_POINTER = 2;

    protected int type;

    protected transient IbisIdentifier sendTo;

    protected ReturnRecord result;

    protected IbisIdentifier owner;

    public GlobalResultTableValue(int type, InvocationRecord r) {
        this.type = type;
        this.owner = Cashmere.getCashmere().ident;
        if (type == TYPE_RESULT) {
            result = r.getReturnRecord();
        }
    }
        
    // this new function solves the ambiguity of Value(type, null)
    // so always use Value(int type) instead of Value(int type, null)
    public GlobalResultTableValue(int type){
        this.type = type;
        this.owner = Cashmere.getCashmere().ident;
    }

    public GlobalResultTableValue(int type, ReturnRecord rr){
        this.type = type;
        this.owner = Cashmere.getCashmere().ident;
        if (type == TYPE_RESULT){
            result = rr;
        }
    }

    public String toString() {
        String str = "";
        switch (type) {
        case TYPE_RESULT:
            str += "(RESULT,result:" + result + ")";
            break;
        case TYPE_POINTER:
            str += "(POINTER,owner:" + owner + ")";
            break;
        default:
            grtLogger.error("CASHMERE '" + Cashmere.getCashmere().ident
                + "': illegal type in value");
        }
        return str;
    }
}
