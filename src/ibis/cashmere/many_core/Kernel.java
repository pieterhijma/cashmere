package ibis.cashmere.many_core;


//import java.io.InputStream;
//import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jocl.cl_event;

/*
import static org.jocl.CL.CL_KERNEL_FUNCTION_NAME;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateKernelsInProgram;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clGetKernelInfo;


import org.jocl.Pointer;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_kernel;
import org.jocl.cl_program;
*/

public class Kernel {


    private Device device;

    List<KernelLaunch> kernelLaunches; 

    private String name;
    private String threadName;

    public Kernel(String name, String threadName, Device device) {
	this.kernelLaunches = Collections.synchronizedList(new
		ArrayList<KernelLaunch>());
	this.name = name;
	this.device = device; 
	this.threadName = threadName;
    }


    public Device getDevice() {
	return device;
    }


    public String getName() {
	return name;
    }


    public String getThread() {
	return threadName;
    }

    public KernelLaunch createLaunch() {
	cl_event[] prevWrites = device.lockForWrites();
	KernelLaunch kernelLaunch = new
	    KernelLaunch(name, Thread.currentThread().getName(), device, prevWrites);
	kernelLaunches.add(kernelLaunch);
	return kernelLaunch;
    }
}
