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
import org.jocl.cl_context;

/**
 * Represents a method to initialize an OpenCL library. The OpenCL library can be initialized using the supplied
 * {@link cl_context} and {@link cl_command_queue}.
 */
@FunctionalInterface
public interface InitLibraryFunction {

    /**
     * Initializes the OpenCL library.
     *
     * @param context
     *            the <code>cl_context</code> to be used for the library
     * @param queue
     *            the <code>cl_command_queue</code> to be used for the library
     * @exception CLException
     *                if an error occurs during initialization of the library
     */
    public void initialize(cl_context context, cl_command_queue queue) throws CLException;
}
