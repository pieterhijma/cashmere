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

/**
 * A <code>LibFunc</code> represents an entry point to an OpenCL library. From this <code>LibFunc</code> we can create more than
 * one {@link LibFuncLaunch} instances with which the library function can be launched.
 */
public class LibFunc extends ManyCoreUnit {

    // A libfunc cannot be created from outside the package, but can be obtained using Cashmere.getLibFunc().
    LibFunc(String name, String threadName, Device device) {
        super(name, threadName, device);
    }

    /**
     * Create a <code>LibFuncLaunch</code> for this <code>LibFunc</code>.
     *
     * @return a <code>LibFuncLaunch</code> value
     */
    @Override
    public LibFuncLaunch createLaunch() {
        return createLaunch(threadName);
    }

    /**
     * Create a <code>LibFuncLaunch</code> for this <code>LibFunc</code>.
     *
     * @param threadName
     *            the name for this thread
     * @return the <code>LibFuncLaunch</code>
     */
    @Override
    public LibFuncLaunch createLaunch(String threadName) {
        LibFuncLaunch libFuncLaunch = new LibFuncLaunch(name, threadName, device);
        return libFuncLaunch;
    }
}
