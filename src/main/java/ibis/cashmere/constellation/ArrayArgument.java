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

import org.jocl.cl_command_queue;
import org.jocl.cl_context;

class ArrayArgument extends Argument {

    protected cl_context context;
    protected cl_command_queue readQueue;
    protected int referenceCount;

    protected ArrayArgument(Direction d, cl_context context, cl_command_queue readQueue) {
        super(d);
        this.context = context;
        this.readQueue = readQueue;

        this.referenceCount = 1;
    }

    @Override
    void clean() {
        if (direction == Direction.OUT || direction == Direction.INOUT) {
            transformBack();
        }
        super.clean();
    }

    protected void transform() {
    }

    protected void transformBack() {
    }
}
