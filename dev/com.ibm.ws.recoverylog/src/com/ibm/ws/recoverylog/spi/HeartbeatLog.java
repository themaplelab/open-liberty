/*******************************************************************************
 * Copyright (c) 2020, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.recoverylog.spi;

/**
 * The currency of a HeartbeatLog is maintained through a heartbeat() method. The currency can be tested through the isLogStale() method.
 *
 * This interface was introduced to allow a server to maintain ownership of its Transaction Recovery Logs where those logs are held in a
 * database. Rather than using a long duration locking scheme, the approach is to allow a first server to maintain its currency by updating
 * a timestamp in its DB table through the heartbeat() method. A second server can test whether the first server is still actively working
 * its recovery logs by calling isLogStale() against the appropriate table.
 *
 */
public interface HeartbeatLog {

    /**
     * Used to maintain the liveness of the Recovery Log.
     *
     * @throws LogClosedException
     *
     */
    public void heartBeat() throws LogClosedException;

    /**
     * Claim ownership of the Local Server's Recovery Logs.
     *
     * @return true if the log has been successfully claimed, false otherwise.
     */
    public boolean claimLocalRecoveryLogs();

    /**
     * Claim ownership of a Peer Server's Recovery Logs.
     *
     * @return true if the log has been successfully claimed, false otherwise.
     */
    public boolean claimPeerRecoveryLogs();

    /**
     * Set time interval that specifies how long
     * before a log goes stale under the HA DB Peer locking scheme.
     *
     * @param timeBeforeStale
     */
    public void setTimeBeforeLogStale(int timeBeforeStale);

    /**
     * Set the heartbeat time interval for the HA DB Peer locking
     * scheme.
     *
     * @param timeBetweenHeartbeats
     */
    public void setTimeBetweenHeartbeats(int timeBetweenHeartbeats);

    /**
     * Set the time interval for an HA DB retry where the operation has a standard retry scheme such
     * as opening or forcing recovery logs.
     *
     * @param logRetryInterval
     */
    public void setLogRetryInterval(int logRetryInterval);

    /**
     * Set the number of retries for an HA DB retry where the operation has a standard retry scheme such
     * as opening or forcing recovery logs.
     *
     * @param logRetryLimit
     */
    public void setLogRetryLimit(int logRetryLimit);

    /**
     * Set the time interval for an HA DB retry where the operation has a lightweight retry scheme such
     * as claiming peer logs or heartbeating.
     *
     * @param lightweightLogRetryInterval
     */
    public void setLightweightLogRetryInterval(int lightweightLogRetryInterval);

    /**
     * Set the number of retries for an HA DB retry where the operation has a lightweight retry scheme such
     * as claiming peer logs or heartbeating.
     *
     * @param lightweightLogRetryLimit
     */
    public void setLightweightLogRetryLimit(int lightweightLogRetryLimit);

    /**
     * Signals to the Recovery Log that the server is stopping.
     */
    public void serverStopping();
}
