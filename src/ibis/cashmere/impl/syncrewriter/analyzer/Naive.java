package ibis.cashmere.impl.syncrewriter.analyzer;

import ibis.cashmere.impl.syncrewriter.Analyzer;
import ibis.cashmere.impl.syncrewriter.SpawnableCall;
import ibis.cashmere.impl.syncrewriter.SpawningMethod;
import ibis.cashmere.impl.syncrewriter.SyncInsertionProposalFailure;
import ibis.cashmere.impl.syncrewriter.util.Debug;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;


public class Naive implements Analyzer {


    public InstructionHandle[] proposeSyncInsertion(SpawningMethod spawnableMethod, Debug d)
	throws SyncInsertionProposalFailure {
	ArrayList<SpawnableCall> spawnableCalls = 
	    spawnableMethod.getSpawnableCalls();
	InstructionHandle[] instructionHandles = 
	    new InstructionHandle[spawnableCalls.size()];

	for (int i = 0; i < spawnableCalls.size(); i++) {
	    SpawnableCall call = spawnableCalls.get(i);
	    InstructionHandle invoke = call.getInvokeInstruction();
	    InstructionHandle store = invoke.getNext();
	    InstructionHandle rightAfterStore = store.getNext();

	    instructionHandles[i] = rightAfterStore;
	}

	return instructionHandles;
    }
}
