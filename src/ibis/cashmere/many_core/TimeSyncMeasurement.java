package ibis.cashmere.many_core;

public class TimeSyncMeasurement implements java.io.Serializable {



    private static final long serialVersionUID = 1L;

    long currentTimeMillis;
    long nanoTime;
    long offsetToMaster;



    public TimeSyncMeasurement() {
	this.currentTimeMillis = System.currentTimeMillis();
	this.nanoTime = System.nanoTime();
	this.offsetToMaster = 0;
    }


    public String toString() {
	return String.format("timeSyncInfo: currentTimeMillis: %-20d, " +
		"nanoTime: %-20d, offset: %-10d\n", currentTimeMillis,
		nanoTime, offsetToMaster);
    }
}
