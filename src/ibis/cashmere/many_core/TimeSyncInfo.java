package ibis.cashmere.many_core;

import ibis.cashmere.impl.Cashmere;

import java.util.HashMap;


public class TimeSyncInfo extends HashMap<String, TimeSyncMeasurement>
    implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String thisNode;

    public TimeSyncInfo() {
	super();
	thisNode = Cashmere.getCashmere().ident.name();
	put(thisNode, new TimeSyncMeasurement());
    }


    public void normalize() {
	equalize();
	computeOffsets();
    }


    public long getOffsetToMaster(String node) {
	TimeSyncMeasurement tsm = get(node);
	return tsm.offsetToMaster;
    }


    private void equalize() {
	long masterTime = get(thisNode).currentTimeMillis;

	for (String node : keySet()) {
	    TimeSyncMeasurement tsm = get(node);
	    long difference = masterTime - tsm.currentTimeMillis;
	    tsm.currentTimeMillis += difference;
	    tsm.nanoTime += difference * 1000000;
	}
    }


    private void computeOffsets() {
	long masterNanoTime = get(thisNode).nanoTime;

	for (String node : keySet()) {
	    TimeSyncMeasurement tsm = get(node);
	    tsm.offsetToMaster = masterNanoTime - tsm.nanoTime;
	}
    }
}

