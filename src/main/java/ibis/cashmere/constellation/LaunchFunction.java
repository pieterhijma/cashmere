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

import org.jocl.CLException;
import org.jocl.cl_command_queue;
import org.jocl.cl_event;

/**
 * Represents a method to launch a library function {@link LibFunc}. The supplied parameters can be used to enqueue the library
 * function.
 */
@FunctionalInterface
public interface LaunchFunction {

    /**
     * Launches a library function. This means that the <code>queue</code> should be used to enqueue the library function. The
     * parameter <code>events_in_wait_list</code> will contain <code>num_events_in_wait_list</code> events that should finish
     * before the library function can be executed. In <code>event</code> the library function should return the event on which
     * other OpenCL commands should wait.
     *
     * @param queue
     *            the {@link cl_command_queue} with which library function executions can be enqueued
     * @param num_events_in_wait_list
     *            the number of events in <code>events_in_wait_list</code>
     * @param events_in_wait_list
     *            contains <code>num_events_in_wait_list</code> events that should finish before the library function
     * @param event
     *            the {@link cl_event} that should be returned by this execution of the library function
     * @exception CLException
     *                if an error occurs within the OpenCL library.
     */
    public void launch(cl_command_queue queue, int num_events_in_wait_list, cl_event[] events_in_wait_list, cl_event event)
            throws CLException;

}
