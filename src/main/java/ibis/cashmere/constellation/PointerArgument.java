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

import java.util.ArrayList;

import org.jocl.Pointer;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;

class PointerArgument extends ArrayArgument {

    PointerArgument(cl_context context, cl_command_queue readQueue) {
        super(Argument.Direction.INOUT, context, readQueue);
    }

    // This is not an override, but an alternative.
    void scheduleReads(Pointer to, long size, ArrayList<cl_event> waitListEvents, ArrayList<cl_event> readBufferEvents,
            boolean async) {
        cl_event event = readBuffer(context, readQueue, waitListEvents, size, to, async);
        if (event != null) {
            readBufferEvents.add(event);
        }
    }
}
