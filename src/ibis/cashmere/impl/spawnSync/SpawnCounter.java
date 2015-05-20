/* $Id: SpawnCounter.java 9591 2008-10-02 08:26:33Z ceriel $ */

package ibis.cashmere.impl.spawnSync;

import ibis.cashmere.impl.Cashmere;

import java.util.HashMap;

/**
 * This class represents a counter of spawning events. Access to its internals
 * is package-protected.
 */
public final class SpawnCounter {
    private static SpawnCounter spawnCounterCache = null;

    private int value = 0;

    private SpawnCounter next;

    /** For debugging purposes ... */
    private HashMap<InvocationRecord, Throwable> m = null;

    /** For debugging purposes ... */
    private Throwable lastIncr = null;

    /** For debugging purposes ... */
    private Throwable lastDecr = null;

    /** For debugging purposes ... */
    private int lastvalue = 0;

    /**
     * Obtains a new spawn counter. This does not need to be synchronized, only
     * one thread spawns.
     * 
     * @return a new spawn counter.
     */
    static synchronized public final SpawnCounter newSpawnCounter() {
	if (spawnCounterCache == null) {
	    return new SpawnCounter();
	}

	SpawnCounter res = spawnCounterCache;
	spawnCounterCache = res.next;

	return res;
    }

    /**
     * Makes a spawn counter available for recycling. This does not need to be
     * synchronized, only one thread spawns.
     * 
     * @param s
     *            the spawn counter made available.
     */
    static synchronized public final void deleteSpawnCounter(SpawnCounter s) {
	if (Cashmere.ASSERTS && s.value < 0) {
	    Cashmere.spawnLogger.error(
		    "deleteSpawnCounter: spawncouner < 0, val =" + s.value,
		    new Throwable());
	    System.exit(1); // Failed assertion
	}

	// Only put it in the cache if its value is 0.
	// If not, there may be references to it yet.
	if (s.value == 0) {
	    s.next = spawnCounterCache;
	    spawnCounterCache = s;
	} else {
	    System.err.println("EEK, deleteSpawnCounter, while counter > 0");
	    new Exception().printStackTrace();
	    // we can continue, but I don't know how this can ever happen --Rob
	}
    }

    public synchronized void incr(InvocationRecord r) {
	if (Cashmere.ASSERTS && Cashmere.spawnLogger.isDebugEnabled()) {
	    debugIncr(r);
	} else {
	    value++;
	}
	if (Cashmere.spawnLogger.isDebugEnabled()) {
	    Cashmere.spawnLogger.debug("Incremented spawnCounter for "
		    + r.getStamp() + ", value = " + value);
	}
    }

    private synchronized void debugIncr(InvocationRecord r) {
	Throwable e = new Throwable();
	if (m == null) {
	    m = new HashMap<InvocationRecord, Throwable>();
	}
	if (value != lastvalue) {
	    Cashmere.spawnLogger.debug("Incr: lastvalue != value!");
	    if (lastIncr != null) {
		Cashmere.spawnLogger.debug("Last increment: ", lastIncr);
	    }
	    if (lastDecr != null) {
		Cashmere.spawnLogger.debug("Last decrement: ", lastDecr);
	    }
	}
	value++;
	lastvalue = value;
	lastIncr = e;
	if (m.containsKey(r)) {
	    Cashmere.spawnLogger.debug("Incr: already present from here: ",
		    m.remove(r));
	    Cashmere.spawnLogger.debug("Now here: ", e);
	}
	m.put(r, e);
	if (m.size() != value) {
	    Cashmere.spawnLogger.debug("Incr: hashmap size = " + m.size()
		    + ", value = " + value, e);
	}
    }

    public synchronized void decr(InvocationRecord r) {
	if (Cashmere.ASSERTS && Cashmere.spawnLogger.isDebugEnabled()) {
	    decrDebug(r);
	} else {
	    value--;
	}
	if (value == 0) {
	    notifyAll();
	}
	if (Cashmere.spawnLogger.isDebugEnabled()) {
	    Cashmere.spawnLogger.debug("Decremented spawnCounter for "
		    + r.getStamp() + ", value = " + value);
	}
	if (Cashmere.ASSERTS && value < 0) {
	    System.err.println("Just made spawncounter < 0");
	    new Exception().printStackTrace();
	    System.exit(1); // Failed assertion
	}
    }

    private synchronized void decrDebug(InvocationRecord r) {
	if (m == null) {
	    m = new HashMap<InvocationRecord, Throwable>();
	}
	if (value != lastvalue) {
	    Cashmere.spawnLogger.debug("Decr: lastvalue != value!");
	    Cashmere.spawnLogger.debug(r.simpleString());
	    if (lastIncr != null) {
		Cashmere.spawnLogger.debug("Last increment: ", lastIncr);
	    }
	    if (lastDecr != null) {
		Cashmere.spawnLogger.debug("Last decrement: ", lastDecr);
	    }
	}
	value--;
	lastvalue = value;
	Throwable x;
	lastDecr = new Throwable();
	x = m.remove(r);
	if (x == null) {
	    Cashmere.spawnLogger.debug("Decr: not present: " + r.simpleString()
		    + ", hashcode = " + r.hashCode(), lastDecr);
	    Cashmere.spawnLogger.debug("HashMap keys: ");
	    for (InvocationRecord rr : m.keySet()) {
		Cashmere.spawnLogger.debug("        " + rr.simpleString()
			+ ", hashCode = " + rr.hashCode());
		if (r == rr) {
		    Cashmere.spawnLogger.debug("But they are the same!");
		} else if (r.equals(rr)) {
		    Cashmere.spawnLogger.debug("But they are equal!");
		}
	    }
	}
	if (m.size() != value) {
	    Cashmere.spawnLogger.debug("Decr: r = " + r.simpleString()
		    + ", hashmap size = " + m.size() + ", value = " + value,
		    lastDecr);
	}
    }

    public int getValue() {
	return value;
    }
}
