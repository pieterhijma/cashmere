/* $Id: LocalRecord.java 3862 2006-06-20 14:14:19Z rob $ */

package ibis.cashmere.impl.aborts;

import ibis.cashmere.impl.spawnSync.InvocationRecord;

/**
 * Describes the local variables and parameters of a method invoking a spawnable
 * method. The Cashmere frontend generates a subclass of this class for each caller
 * of a spawnable method.
 */
abstract public class LocalRecord {
    /**
     * Deals with an exception or error which is raised by the Cashmere invocation
     * described by the parameters. This method gets called when a Cashmere job,
     * executed locally, throws an exception or error.
     * 
     * @param spawnId
     *            the identification of the spawned Cashmere invocation.
     * @param t
     *            the exception or error thrown by this invocation.
     * @param parent
     *            the invocation record describing this invocation.
     */
    abstract public void handleException(int spawnId, Throwable t,
        InvocationRecord parent) throws Throwable;
}
