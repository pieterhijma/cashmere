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

import static org.jocl.CL.CL_EVENT_REFERENCE_COUNT;
import static org.jocl.CL.clGetEventInfo;
import static org.jocl.CL.clReleaseEvent;
import static org.jocl.CL.clRetainEvent;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* There are four places where events are created in Cashmere/Constellation
 * - Argument.writeBufferNoCreate (writeBufferEvents)
 * - Argument.readBuffer (readBufferEvents)
 * - KernelLaunch.launch (executeEvents)
 * - LibFuncLaunch.launch (executeEvents)
 *
 * The most simple way to launch a kernel is to just call MCL.launch<kernel>
 * with the data-structure in the argument list.  This type of launch will
 * automatically copy the input data to the device and the output data from
 * the device.  The events that result from this will be stored in
 * Launch.writeBufferEvents, Launch.readBufferEvents, and Launch.executeEvents.
 */

class Event {

    /*
     * Loggers.
     *
     * this one is package, as it is used by other classes.
     */
    static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Event");

    /*
     * Debugging
     */
    static AtomicInteger nrEvents = new AtomicInteger();
    // public static Set<cl_event> nonReleasedEvents = Collections.synchronizedSet(new HashSet<cl_event>());

    static void showEvent(String type, cl_event event) {
        if (type == null) {
            type = "unknown";
        }
        int[] result = new int[1];
        clGetEventInfo(event, CL_EVENT_REFERENCE_COUNT, Sizeof.cl_uint, Pointer.to(result), null);
        logger.debug(String.format("%s event %s with refcount: %d", type, event, result[0]));
    }

    static void showEvents(String type, ArrayList<cl_event> events) {
        if (events != null) {
            for (cl_event event : events) {
                showEvent(type, event);
            }
        }
    }

    static void showEvents(String type, cl_event[] events) {
        if (events != null) {
            for (cl_event event : events) {
                showEvent(type, event);
            }
        }
    }

    static void retainEvents(cl_event[] events) {
        if (events != null) {
            for (cl_event event : events) {
                if (logger.isDebugEnabled()) {
                    logger.debug("about to do a clRetainEvent on {}", event);
                    nrEvents.incrementAndGet();
                }
                clRetainEvent(event);
            }
        }
    }

    static void clean(cl_event event) {
        int nrReferences = 0;
        if (logger.isDebugEnabled()) {
            int[] result = new int[1];
            clGetEventInfo(event, CL_EVENT_REFERENCE_COUNT, Sizeof.cl_uint, Pointer.to(result), null);
            nrReferences = result[0];
        }

        clReleaseEvent(event);
        if (logger.isDebugEnabled()) {
            nrEvents.decrementAndGet();

            logger.debug(String.format("releasing event %s with refcount: %d", event, nrReferences));
            if (nrReferences > 1) {
                logger.debug("{} still needs to be released another time", event);
            }
        }
    }
}
