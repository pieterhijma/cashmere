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
 * An exception that indicates that Cashmere is not available. An example case where Cashmere is not available is the situation
 * where a node has no or an unsupported many-core device.
 */
public class CashmereNotAvailable extends CashmereException {

    private static final long serialVersionUID = 8847994110046436104L;
    private static final String DEFAULT_MESSAGE = "Cashmere not available on this node";

    /**
     * Creates a new <code>CashmereNotAvailable</code> exception with the default message.
     *
     */
    public CashmereNotAvailable() {
        super(DEFAULT_MESSAGE);
    }

    /**
     * Creates a new <code>CashmereNotAvailable</code> exception with the specified message. The message is prepended by the
     * default message.
     *
     * @param message
     *            the message for this exception
     */
    public CashmereNotAvailable(String message) {
        super(DEFAULT_MESSAGE + ": " + message);
    }
}
