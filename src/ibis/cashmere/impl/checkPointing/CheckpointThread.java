package ibis.cashmere.impl.checkPointing;

import ibis.cashmere.impl.Cashmere;

public class CheckpointThread extends Thread {

    int milis;
    int firstTime;
    boolean stopped;

    public CheckpointThread(int milis, int firstTime){
	super("CashmereCheckpointThread");
	this.milis = milis;
	this.firstTime = firstTime;
	stopped = false;
    }

    public void setExitCondition(boolean stop){
	this.stopped = stop;
    }

    public void run(){
	Cashmere cashmere = Cashmere.getCashmere();
	//boolean checkpointPush = cashmere.CHECKPOINT_PUSH;
	try {
	    sleep(firstTime);
	} catch (InterruptedException e){
	    System.out.println("CheckpointThread interrupted for some reason");
	}	
	while(true){
	    if (stopped){
		return;
	    } else {
		cashmere.ft.takeCheckpoint = true;
	    }
	    try {
		sleep(milis);
	    } catch (InterruptedException e){
		System.out.println("CheckpointThread interrupted for some reason");
	    }
	}
    }
}
