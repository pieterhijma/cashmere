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
 * An exception that indicates that the library is not available.
 */
public class LibFuncNotAvailable extends CashmereException {

    private static final long serialVersionUID = 8847994110046436104L;

    /**
     * Creates a new <code>LibFuncNotAvailable</code> exception with the supplied name of the library function.
     *
     * @param nameLibFunc
     *            the name of the library function not being available
     */
    public LibFuncNotAvailable(String nameLibFunc) {
        super("Library function " + nameLibFunc + " not available");
    }
}
