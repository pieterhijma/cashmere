package ibis.cashmere.many_core;


import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;



public class MCStats implements java.io.Serializable {



    private static final long serialVersionUID = 1L;



    private List<MCTimer> timers;



    // This is the public interface to the rest of the framework.
    public MCStats() {
	timers = Collections.synchronizedList(new ArrayList<MCTimer>());
    }


    public void add(MCStats s) {
	this.timers.addAll(s.timers);
    }


    /** Conclude the statistics. 
     * This method is called by every Cashmere instance on exiting. 
     */
    public void conclude() {
	MCCashmere.conclude();
    }


    /** Print the statistics. This is the entry point for the master in the
     * conclusion phase process all
     * statistics. The statistics from all other nodes have already been added
     * to this.
     */
    public void printStats(TimeSyncInfo timeSyncInfo) {
	System.out.print("\n-------------------------------");
	System.out.print("MANY-CORE STATISTICS");
	System.out.println("-------------------------------");

	timeSyncInfo.normalize();

	normalize(timeSyncInfo);

	MCTimer timer = getTotalMCTimer();
        timer.filterOverall();
	//System.out.println(timer.extensiveOutput());
	
	printActions(timer);

	printDataTransfers();
	
	printGNUPlotData(timeSyncInfo);
    }


    void addTimer(MCTimer timer) {
	timers.add(timer);
    }




    private void printActions(MCTimer timer) {
	List<MCTimer> actionTimers = timer.groupByAction();

	for (MCTimer t : actionTimers) {
	    print(t.getAction(), t);
	}
    }


    private void printDataTransfers() {
	MCTimer timer = getTotalMCTimer();
	timer.onlyDataTransfers();

	List<MCTimer> actionTimers = timer.groupByAction();

	for (MCTimer t : actionTimers) {
	    System.out.println(t.dataTransferOutput());
	}
    }



    private void clean() {
	for (MCTimer timer : timers) {
	    timer.clean();
	}
    }


    private void normalize(TimeSyncInfo timeSyncInfo) {
	clean();
	MCTimer timer = getTotalMCTimer();

	normalizeTimer(timer, timeSyncInfo);
    }


    private void normalizeTimer(MCTimer timer, TimeSyncInfo timeSyncInfo) {
	timer.equalize(timeSyncInfo);
	long min = Long.MAX_VALUE;
	min = Math.min(timer.getMinimumTime(), min);
	timer.normalize(min);
    }


    private MCTimer getTotalMCTimer() {
	MCTimer temp = new MCTimer();
	for (MCTimer t : timers) {
	    temp.add(t);
	}
	return temp;
    }


    private void write(MCTimer timer, String fileName, boolean perThread) {
	PrintStream ps = null;
	try {
	    ps = new PrintStream(fileName);
	    ps.print(timer.gnuPlotData(perThread));
	}
	catch (FileNotFoundException e) {
	    System.out.println(e);
	} finally {
	    if (ps != null) {
		ps.close();
	    }
	}
    }


    private void printGNUPlotData(TimeSyncInfo tsi) {
	MCTimer temp = getTotalMCTimer();
	temp.filterOverall();
	write(temp, "gantt.data", false);
	write(temp, "gantt-thread.data", true);
    }


    void print(String kind, MCTimer t) {
	System.out.printf("%-53s %3d %s %s\n", kind, t.nrTimes(),
		t.averageTime(), t.totalTime());
    }
}
