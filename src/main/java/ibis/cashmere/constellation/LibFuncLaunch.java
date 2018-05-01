/*
 * Copyright 2018 Vrije Universiteit Amsterdam, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ibis.cashmere.constellation;

import static org.jocl.CL.clWaitForEvents;

import java.util.Arrays;

import org.jocl.cl_event;

import ibis.util.ThreadPool;

/**
 * Represents one specific launch of a <code>LibFunc</code>. While {@link #launch} methods can only be called once, it is possible
 * to create multiple launches from a {@link LibFunc}.
 */
public class LibFuncLaunch extends Launch {

    // A LibFuncLaunch can only be created from within the package.
    LibFuncLaunch(String kernelName, String threadName, Device device) {
        super(kernelName, threadName, device);
    }

    /**
     * Launch the library function with the supplied <code>LaunchFunction</code>. The launch will be synchronous.
     *
     * @param launchFunction
     *            represents the functionality to launch the library function.
     */
    public void launch(LaunchFunction launchFunction) {
        launch(true, launchFunction);
    }

    /**
     * Launch the library function with the supplied <code>LaunchFunction</code>.
     *
     * @param synchronous
     *            indicates whether the launch will be synchronous or asynchronous.
     * @param launchFunction
     *            represents the functionality to launch the library function.
     */
    public void launch(boolean synchronous, LaunchFunction launchFunction) {
        device.launched();
        cl_event event = new cl_event();
        final cl_event[] wbeArray = writeBufferEvents.toArray(new cl_event[writeBufferEvents.size()]);

        Event.retainEvents(wbeArray);

        if (logger.isTraceEnabled()) {
            logger.debug("Launch: events to wait for: " + Arrays.toString(wbeArray));
            ThreadPool.createNew(new Thread() {
                @Override
                public void run() {
                    if (wbeArray.length != 0) {
                        clWaitForEvents(wbeArray.length, wbeArray);
                    }
                    System.out.println("Test wait successful: " + Arrays.toString(wbeArray));
                }
            }, "test event waiter");
        }

        device.withAllocationError(() -> {
            launchFunction.launch(executeQueue, wbeArray.length, wbeArray.length == 0 ? null : wbeArray, event);
            return 0;// is ignored, to make compiler happy.
        });

        if (eventLogger.isDebugEnabled()) {
            Event.nrEvents.getAndIncrement();
            eventLogger.debug(
                    "Launched LibFunc: " + event + " (new event) depends on : " + Arrays.toString(wbeArray) + "(retained)");
            eventLogger.debug("Storing {} in Launch.executeEvents", event);
        }

        executeEvents.add(event);
        registerExecuteEventToDevice(event);

        launched = true;
        if (synchronous) {
            finish();
        }
        registerWithThread();
    }

    @Override
    protected void setArgument(int size, Argument arg) {
        // do nothing, has no meaning here
    }
}
