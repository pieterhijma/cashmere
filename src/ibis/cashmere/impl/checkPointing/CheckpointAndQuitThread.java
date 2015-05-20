package ibis.cashmere.impl.checkPointing;

import ibis.cashmere.impl.Cashmere;

public class CheckpointAndQuitThread extends Thread {

    int milis; //wait that long before dying

    public CheckpointAndQuitThread(int time) {
        super("CheckpointAndQuitThread");
        this.milis = time * 1000;
    }

    public void run() {
        try {
            sleep(milis);
        } catch (InterruptedException e) {	    
            //ignore
        }
        Cashmere cashmere = Cashmere.getCashmere();
        cashmere.ft.checkpointAndQuit();	        
    }

}
