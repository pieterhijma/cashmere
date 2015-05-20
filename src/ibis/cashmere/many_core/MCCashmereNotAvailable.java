package ibis.cashmere.many_core;


public class MCCashmereNotAvailable extends Exception {

    private static final long serialVersionUID = 8847994110046436104L;
    
    public MCCashmereNotAvailable() {
	super("Many-core Cashmere not available on this node");
    }
    public MCCashmereNotAvailable(String s) {
	super("Many-core Cashmere not available on this node: " + s);
    }
}
