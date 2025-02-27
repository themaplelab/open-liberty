/*******************************************************************************
 * Copyright (c) 2012, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.ibm.ws.sib.jfapchannel.impl;

import static com.ibm.ws.sib.jfapchannel.JFapChannelConstants.CONNECTION_INVALIDATE_01;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.CLOSED;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.CLOSE_IN_PROGRESS;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.CLOSE_NOTIFYING_PEER_PENDING;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.CLOSE_PENDING;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.INVALIDATE_IN_PROGRESS;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.INVALIDATE_NOTIFYING_PEER_IN_PROGRESS;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.INVALIDATE_NOTIFYING_PEER_PENDING;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.INVALIDATE_PENDING;
import static com.ibm.ws.sib.jfapchannel.impl.Connection.State.OPEN;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import com.ibm.ejs.ras.TraceNLS;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.sib.exception.SIErrorException;
import com.ibm.websphere.sib.exception.SIException;
import com.ibm.websphere.sib.exception.SIResourceException;
import com.ibm.ws.ffdc.FFDCFilter;
import com.ibm.ws.sib.jfapchannel.AcceptListener;
import com.ibm.ws.sib.jfapchannel.ConnectionClosedListener;
import com.ibm.ws.sib.jfapchannel.ConnectionInterface;
import com.ibm.ws.sib.jfapchannel.Conversation;
import com.ibm.ws.sib.jfapchannel.Conversation.ThrottlingPolicy;
import com.ibm.ws.sib.jfapchannel.ConversationMetaData;
import com.ibm.ws.sib.jfapchannel.ConversationReceiveListener;
import com.ibm.ws.sib.jfapchannel.ConversationUsageType;
import com.ibm.ws.sib.jfapchannel.HandshakeProperties;
import com.ibm.ws.sib.jfapchannel.JFapByteBuffer;
import com.ibm.ws.sib.jfapchannel.JFapChannelConstants;
import com.ibm.ws.sib.jfapchannel.SendListener;
import com.ibm.ws.sib.jfapchannel.buffer.WsByteBuffer;
import com.ibm.ws.sib.jfapchannel.buffer.WsByteBufferPool;
import com.ibm.ws.sib.jfapchannel.framework.FrameworkException;
import com.ibm.ws.sib.jfapchannel.framework.IOConnectionContext;
import com.ibm.ws.sib.jfapchannel.framework.IOReadCompletedCallback;
import com.ibm.ws.sib.jfapchannel.framework.IOReadRequestContext;
import com.ibm.ws.sib.jfapchannel.framework.IOWriteCompletedCallback;
import com.ibm.ws.sib.jfapchannel.framework.IOWriteRequestContext;
import com.ibm.ws.sib.jfapchannel.framework.NetworkConnection;
import com.ibm.ws.sib.jfapchannel.framework.NetworkConnectionContext;
import com.ibm.ws.sib.jfapchannel.impl.eventrecorder.ConnectionEventRecorder;
import com.ibm.ws.sib.jfapchannel.impl.eventrecorder.ConnectionEventRecorderFactory;
import com.ibm.ws.sib.jfapchannel.netty.NettyNetworkConnection;
import com.ibm.ws.sib.mfp.ConnectionSchemaSet;
import com.ibm.ws.sib.utils.RuntimeInfo;
import com.ibm.ws.sib.utils.ras.SibTr;
import com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException;
import com.ibm.wsspi.sib.core.exception.SIConnectionLostException;

import io.openliberty.netty.internal.exception.NettyException;

/**
 * Represents an actual socket connection to another machine
 *
 * @author prestona
 */
public abstract class Connection implements ConnectionInterface
{
    private static final TraceComponent tc = SibTr.register(Connection.class,
            JFapChannelConstants.MSG_GROUP,
            JFapChannelConstants.MSG_BUNDLE);

    private static final TraceNLS nls = TraceNLS.getTraceNLS(JFapChannelConstants.MSG_BUNDLE); // D226223

    // ID to use for first conversation.
    public static final short FIRST_CONVERSATION_ID = 1;

    // Is this the first conversation to use the connection or not?
    protected boolean first = true;

    // Object used to order messages by priority over this connection
    private PriorityQueue priorityQueue = null;

    protected IOConnectionContext tcpCtx = null;

    private IOReadRequestContext tcpReadCtx = null;

    private IOWriteRequestContext tcpWriteCtx = null;

    // A table which tracks conversations using this connection.
    protected ConversationTable conversationTable = null;

    // Callbacks notified when a read or write completes for this connection
    private final BaseConnectionWriteCallback writeCompletedCallback;

    private BaseConnectionReadCallback readCompletedCallback = null;

    // Arbitrary object user has attached to this connection.
    private volatile Object userAttachment = null;

    // The MFP SchemaSet belonging to this Connection
    private volatile ConnectionSchemaSet schemaSet = null;

    private AtomicInteger nextRequestNumber = new AtomicInteger();

    private NetworkConnection vc = null;

    protected NetworkConnectionContext connChannel = null;

    // Amount of 'quiet' before we hearbeat (seconds)
    private volatile int heartbeatInterval;

    // Amount of time to wait for a heartbeat response (seconds)
    private volatile int heartbeatTimeout;
    
    // Determine if the connection is using Netty or Channelfw
    private final boolean usingNetty;

    private Conversation.ConversationType conversationType =          // F193735.3
            Conversation.UNKNOWN;

    private final Map<ConversationUsageType, ConnectionClosedListener> closeListeners = new EnumMap<>(ConversationUsageType.class);

    private HandshakeProperties handshakeProperties;

    private final ConnectionEventRecorder eventRecorder;

    public enum State {
        // Enumerations for states which the connection may be in.
        // The state transition table is as follows:
        //
        //           /-->  CLOSE_PENDING ----------------------\
        //           |           |                             |
        //           |           |                             |
        //           |           \                             |
        //           |-->  CLOSE_NOTIFYING_PEER_PENDING -------|
        //           |           /   |                         |
        //           |           |   |                         |
        // OPEN  ----|-----------|---|-------------------------|----->  CLOSE_IN_PROGRESS --------------------|------> CLOSED
        //           |           |   |                                         |                              |
        //           |           V   V                                         |                              |
        //           |-->  INVALIDATE_PENDING -----------------\               |                              |
        //           |                                         |               V                              |
        //           |-----------------------------------------|-----> INVALIDATE_IN_PROGRESS ----------------|
        //           |                                                                                        |
        //           |-->  INVALIDATE_NOTIFYING_PEER_PENDING --\                                              |
        //           |                                         |                                              |
        //           \-----------------------------------------|-----> INVALIDATE_NOTIFYING_PEER_IN_PROGRESS -/
        //
        // Notes:
        // - Invalidate is an intermediate step before close implemented by subclasses (invalidateImpl),
        //   that can be skipped when the subclasses call us directly.
        // - A close can be changed into an invalidate.
        // - The _PENDING states are entered when a close or invalidate is requested
        //   while sends are active, deferring the transition to the _IN_PROGRESS state
        //   to the post-send callback.
        // - CLOSED is the final state (where the physical socket is cleaned up) for both
        //   the CLOSE_ and INVALIDATE_ paths.
        CLOSED,
        INVALIDATE_IN_PROGRESS(CLOSED),
        CLOSE_IN_PROGRESS(CLOSED, INVALIDATE_IN_PROGRESS),
        INVALIDATE_NOTIFYING_PEER_IN_PROGRESS(CLOSED),
        INVALIDATE_PENDING(INVALIDATE_IN_PROGRESS),
        INVALIDATE_NOTIFYING_PEER_PENDING(INVALIDATE_NOTIFYING_PEER_IN_PROGRESS),
        CLOSE_PENDING(CLOSE_IN_PROGRESS, INVALIDATE_PENDING),
        CLOSE_NOTIFYING_PEER_PENDING(CLOSE_IN_PROGRESS, INVALIDATE_PENDING),
        OPEN(CLOSE_PENDING, CLOSE_NOTIFYING_PEER_PENDING, CLOSE_IN_PROGRESS, INVALIDATE_PENDING, INVALIDATE_IN_PROGRESS, INVALIDATE_NOTIFYING_PEER_PENDING, INVALIDATE_NOTIFYING_PEER_IN_PROGRESS);
        final Set<State> transitions;
        State(State...nextStates) { this.transitions = unmodifiableSet(new HashSet<>(asList(nextStates))); }
    }

    public static class StateHolder {
        private State state = OPEN;

        State currentState() { return state; }

        synchronized boolean changeState(final State from, final State to) {
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "changeState from="+from+", to="+to);
            if (from != state) return false;
            if (from.transitions.contains(to)) {
                state = to;
                return true;
            }
            throw new IllegalStateException("Illegal attempt to change state from " + from + " to " + to);
        }
    }

    // The state of the connection.
    private StateHolder state = new StateHolder();
    // Guard lock for state, writeLock() must be held for state transition.
    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();

    private interface L extends AutoCloseable {
        void close();
    }

    private static L obtainLock(Lock lock) {
        lock.lock();
        return lock::unlock;
    }

    // Counter of threads currently in the send method.  This is used to decide if a close or invalidate
    // needs to be deferred until all the send operations have completed.
    private final AtomicInteger threadsSendingData = new AtomicInteger(0);

    // Used to store the throwable (if any) supplied to the invalidate method if the operation has been
    // deferred until a send operation completes.
    private volatile Throwable invalidatePendingThrowable = null;

    // Size of maximum transmission that can be sent over this connection without being segmented.
    private int maxTransmissionSize = JFapChannelConstants.DEFAULT_MAX_TRANSMISSION_SIZE; // F181603.2

    // Description of this connection - used for trace.
    protected String description = "";                                                   // D224560

    // Used for error messages
    protected String remoteHostAddress;                                                 // D226223
    protected String chainName;                                                         // D226223

    private final boolean logIOEvents;

    /**
     * Creates a new connection object which communicates over the specified ConnLink.
     * @throws FrameworkException if anything goes wrong while creating a new instance of Connection.
     */
    public Connection(NetworkConnectionContext channel,                // F177053
            NetworkConnection vc,
            int heartbeatInterval,
            int heartbeatTimeout,
            boolean usingNetty) throws FrameworkException
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled())
            SibTr.entry(this, tc, "<init>", new Object[] {channel, vc, ""+heartbeatInterval, ""+heartbeatTimeout, ""+usingNetty});

        first = true;
        priorityQueue = new PriorityQueue();
        conversationTable = new ConversationTable();

        if (heartbeatInterval < 0 || heartbeatTimeout < 1)    // F177053
            throw new SIErrorException(nls.getFormattedMessage("CONNECTION_INTERNAL_SICJ0043", null, "CONNECTION_INTERNAL_SICJ0043")); // D226242
        this.heartbeatInterval = heartbeatInterval;           // F175658
        this.heartbeatTimeout = heartbeatTimeout;             // F175658


        tcpCtx = channel.getIOContextForDevice();

        if(tcpCtx == null)
        {
            //If we have a null tcpCtx that means the physical TCP/IP connection has been ripped from under us.
            throw new FrameworkException(nls.getFormattedMessage("NETWORK_FAILURE_SICJ0083", null, "NETWORK_FAILURE_SICJ0083"));
        }

        tcpReadCtx = tcpCtx.getReadInterface();
        tcpWriteCtx = tcpCtx.getWriteInterface();

        connChannel = channel;                                // F174772
        this.vc = vc;                                         // F174772
        
        this.usingNetty = usingNetty;
        
        if(isUsingNetty()) {
        	writeCompletedCallback = new NettyConnectionWriteCompletedCallback(priorityQueue, tcpWriteCtx, this);
        	try {
        		((NettyNetworkConnection) vc).setHearbeatInterval(heartbeatInterval);
        	} catch (NettyException e) {
        		throw new FrameworkException(e);
        	}
        }else {
        	writeCompletedCallback = new ConnectionWriteCompletedCallback(priorityQueue, tcpWriteCtx, this); // F176003
        }

        logIOEvents = RuntimeInfo.getProperty(JFapChannelConstants.LOG_IO_TO_FFDC_EVENTLOG_PROPERTY) != null;
        int numConversationEvents = -1;
        int numConnectionEvents = -1;

        try
        {
            if (RuntimeInfo.getProperty(JFapChannelConstants.CONNECTION_FFDC_EVENTLOG_SIZE_PROPERTY) != null)
                numConnectionEvents = Integer.parseInt(RuntimeInfo.getProperty(JFapChannelConstants.CONNECTION_FFDC_EVENTLOG_SIZE_PROPERTY));
            if (RuntimeInfo.getProperty(JFapChannelConstants.CONVERSATION_FFDC_EVENTLOG_SIZE_PROPERTY) != null)
                numConversationEvents = Integer.parseInt(RuntimeInfo.getProperty(JFapChannelConstants.CONVERSATION_FFDC_EVENTLOG_SIZE_PROPERTY));
        }
        catch(NumberFormatException nfe)
        {
            // No FFDC Code Needed
        }
        if ((numConnectionEvents < 0) && (numConversationEvents < 0))
            eventRecorder = ConnectionEventRecorderFactory.getConnectionEventRecorder();
        else if (numConversationEvents < 0)
            eventRecorder = ConnectionEventRecorderFactory.getConnectionEventRecorder(numConnectionEvents);
        else
            eventRecorder = ConnectionEventRecorderFactory.getConnectionEventRecorder(numConnectionEvents, numConversationEvents);

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "<init>");
    }
    
    public IOReadCompletedCallback getReadCompletedCallback () {
 	   return this.readCompletedCallback;
    }

    public NetworkConnection getNetworkConnection () {
 	   return this.vc;
    }

    public IOReadRequestContext getReadRequestContext () {
 	   return this.tcpReadCtx;
    }

    /**
     * This method should be used by inbound connection implementations to start a new conversation
     * based upon data received across an existing connection.
     */
    protected abstract ConversationImpl startNewConversation(ConversationImpl conv)
         throws SIResourceException;

    /**
     * Initiates a new Conversation on this Connection.
     */
    protected ConversationImpl startNewConversationGeneric(ConversationImpl conv, boolean onClientSide, AcceptListener acceptListener)
            throws SIResourceException
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled())
            SibTr.entry(this, tc, "startNewConversationGeneric", new Object[] {conv,onClientSide, acceptListener});

        if (onClientSide)
            conv.setOnClientSide();                                              // F181603.2

        try (L l = obtainLock(stateLock.readLock())) {
           if (state.currentState() != OPEN) {
              throw new SIResourceException(nls.getFormattedMessage("CONNECTION_CLOSED_SICJ0044", null, "CONNECTION_CLOSED_SICJ0044"));
           }
           conversationTable.add(conv);
        }

        if (first)
        {
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "first conversation for connection");

            if(isUsingNetty()) {
            	readCompletedCallback = new NettyConnectionReadCompletedCallback(this, 
            			onClientSide, 
            			acceptListener, 
            			conversationTable, 
            			conv, 
            			tcpCtx);

            }else {
            	readCompletedCallback = new ConnectionReadCompletedCallback(this,
            			onClientSide,
            			acceptListener,
            			conversationTable,
            			conv,
            			tcpCtx);
            }

            // MS:4 take advantage of being able to leave this null for read (cf integration)
            // TODO: Verify adding Netty check for performance?
            if (tcpReadCtx.getBuffer() == null)
            {
                if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "needs new buffer");
                WsByteBuffer buffer = WsByteBufferPool.getInstance().wrap(new byte[1024]); // F196678.10
                tcpReadCtx.setBuffer(buffer);
            }

            first = false;

            // begin F177053
            // Set the initial read timeout value as appropriate for the heartbeat settings.
            int timeout = 0;  // F187828
            if (heartbeatInterval > 0)
            {
                if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
                    SibTr.debug(this, tc, "setting timeout hint to: "+heartbeatInterval*1000+" milliseconds");
                timeout = heartbeatInterval*1000;                     // F187828
            }
            else
            {
                if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "not setting timeout - heartbeating disabled");
                timeout = IOReadRequestContext.NO_TIMEOUT;           // F187828
            }

            // If we already have data in the buffer associated with our read context, then
            // pass it to the appropriate completed callback - otherwise perform a read
            // operation.
            
            if(!isUsingNetty()) {
            	if(tcpReadCtx.getBuffer().remaining() < tcpReadCtx.getBuffer().capacity())
                {
                    readCompletedCallback.complete(vc, tcpReadCtx);
                }
            	else
                {
                    // begin 251021
                    WsByteBuffer readBuffer = tcpReadCtx.getBuffer();
                    if (!readBuffer.isDirect())
                    {
                        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
                            SibTr.debug(this, tc, "replacing read buffer with direct version (read buffer = "+readBuffer+")");
                        WsByteBuffer newBuffer = WsByteBufferPool.getInstance().allocateDirect(readBuffer.capacity());
                        int pos = readBuffer.position();
                        int limit = readBuffer.limit();
                        newBuffer.position(0);
                        newBuffer.limit(pos);
                        readBuffer.position(0);
                        readBuffer.limit(pos);
                        newBuffer.put(readBuffer);
                        newBuffer.position(pos);
                        newBuffer.limit(limit);
                        tcpReadCtx.setBuffer(newBuffer);
                    }
                    // end 251021

                    if (logIOEvents) getConnectionEventRecorder().logDebug("invoking readCtx.read() on context "+System.identityHashCode(tcpReadCtx)+" with a timeout of "+timeout);
                    NetworkConnection conn = tcpReadCtx.read(1,
                            readCompletedCallback,
                            false,
                            timeout);
                    if (conn != null)
                    {
                        readCompletedCallback.complete(conn, tcpReadCtx);
                    }

                }
                // end F177053
            }

        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "startNewConversationGeneric", conv);
        return conv;
    }

    /**
     * Invoked by the send methods prior to sending data.  This method is responsible for
     * both detecting if the connection is already closed (and throwing the appropriate exception)
     * and enforcing the connection locking policy.
     * @param okayIfClosing
     * @throws SIConnectionDroppedException
     */
    private final void preSendProcessing(boolean okayIfClosing) throws SIConnectionDroppedException
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "preSendProcessing", ""+okayIfClosing);

        try (L l = obtainLock(stateLock.readLock()))
        {
            if (state.currentState() != OPEN && !(okayIfClosing && ((state.currentState() == CLOSE_IN_PROGRESS) || (state.currentState() == INVALIDATE_NOTIFYING_PEER_IN_PROGRESS))))
            {
                // If we are not in an open state and we are not about to send the last transmission before
                // closing - then throw an exception.
                throw new SIConnectionDroppedException(nls.getFormattedMessage("CONNECTION_CLOSED_SICJ0044", null, "CONNECTION_CLOSED_SICJ0044")); // D226224
            }

            // Increment the counter of threads inside the send method.  This is used to defer
            // certain operations until the send method is about to exit
            threadsSendingData.incrementAndGet();
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "preSendProcessing");
    }

    /**
     * Invoked by the send method prior to exiting, and once the send method has finished
     * sending data.  This method checks to see if any operations have been defered until
     * the send method returns - and if so - carries them out.
     */
    private final void postSendProcessing()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "postSendProcessing");

        final Optional<State> previousState;
        try (L l = obtainLock(stateLock.writeLock()))
        {
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
                SibTr.debug(this, tc, "state = "+state+", threadsSendingData="+threadsSendingData);

            // Decrement the number of threads sending data, if this hits zero then
            // check if any actions have been deferred while all sending threads have
            // finished
            if (threadsSendingData.decrementAndGet() == 0)
            {
                switch (state.currentState()) {
                case CLOSE_PENDING:
                case CLOSE_NOTIFYING_PEER_PENDING:
                    previousState = Optional.of(state.currentState());
                    state.changeState(previousState.get(), CLOSE_IN_PROGRESS);
                    break;
                case INVALIDATE_PENDING:
                    previousState = Optional.of(state.currentState());
                    state.changeState(previousState.get(), INVALIDATE_IN_PROGRESS);
                    break;
                case INVALIDATE_NOTIFYING_PEER_PENDING:
                    previousState = Optional.of(state.currentState());
                    state.changeState(previousState.get(), INVALIDATE_NOTIFYING_PEER_IN_PROGRESS);
                    break;
                default:
                    previousState = Optional.empty();
                }
            } else {
                previousState = Optional.empty();
            }
        }

        // Perform any deferred action AFTER releasing the lock
        previousState.ifPresent( action -> {
            if (TraceComponent.isAnyTracingEnabled()) JFapUtils.debugSummaryMessage(tc, this, null, "Performing deferred action: " + action);
            switch (action) {
            case CLOSE_PENDING:
                nonThreadSafePhysicalClose(false);
                break;
            case CLOSE_NOTIFYING_PEER_PENDING:
                nonThreadSafePhysicalClose(true);
                break;
            case INVALIDATE_PENDING:
                nonThreadSafeInvalidate(false, invalidatePendingThrowable);
                break;
            case INVALIDATE_NOTIFYING_PEER_PENDING:
                nonThreadSafeInvalidate(true, invalidatePendingThrowable);
                break;
            }
        });
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "postSendProcessing");
    }

    /**
     * Sends data to the peer we are connected to.
     */
    public long send(JFapByteBuffer buffer,                                      // F168604.3, D179183
            int segmentType,
            int conversationId,
            int requestNumber,
            int priority,
            boolean pooledBuffersHint,
            boolean partOfExchange,
            ThrottlingPolicy throttlingPolicy,
            SendListener sendListener,
            Conversation conversation,
            boolean userRequest)
                    throws SIConnectionLostException,
                    SIConnectionDroppedException
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled())
            SibTr.entry(this, tc, "send", new Object[] {buffer, ""+segmentType, ""+conversationId, ""+requestNumber, ""+priority, ""+pooledBuffersHint, throttlingPolicy, sendListener, ""+userRequest});

        if (TraceComponent.isAnyTracingEnabled()) {
            String msg = null;

            if (!userRequest) {
                if (partOfExchange) msg = "System exchange ";
                else msg = "System send ";
            } else {
                if (partOfExchange) msg = "Exchange ";
                else msg = "Send ";
            }

            if (requestNumber > 0)
                msg += "[Request Id:" + requestNumber + "] ";

            msg += Integer.toHexString(segmentType)+" ("+JFapChannelConstants.getSegmentName(segmentType)+")";

            JFapUtils.debugSummaryMessage(tc, this, (ConversationImpl)conversation, msg, requestNumber);
        }

        long sendAmount = 0;                                           // F168604.3

        preSendProcessing(false);

        try
        {
            sendAmount = buffer.prepareForTransmission();

            priorityQueue.queue(buffer,
                    segmentType,
                    requestNumber,
                    priority,
                    sendListener,
                    conversation,
                    this,
                    conversationId,
                    pooledBuffersHint,
                    partOfExchange,
                    sendAmount,
                    false,
                    throttlingPolicy);

            // Proddle the write callback to get it to leap into action and start
            // sending data from the priority queue.
            writeCompletedCallback.proddle();
        }
        finally
        {
            postSendProcessing();
        }
        // end D203646

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "send", ""+sendAmount);    // F168604.3
        return sendAmount;                                              // F168604.3
    }

    // begin F181603.2
    /**
     * Sends data to the peer we are connected to.
     *
     * @param data
     * @param segmentType
     * @param priority
     * @param pooledBuffersHint
     * @param partOfExchange
     * @param throttlingPolicy
     * @param sendListener
     * @param terminate
     */
    private long send(JFapByteBuffer buffer,
            int segmentType,
            int priority,
            boolean pooledBuffersHint,
            boolean partOfExchange,
            ThrottlingPolicy throttlingPolicy,
            SendListener sendListener,
            boolean terminate)
                    throws SIConnectionLostException,
                    SIConnectionDroppedException
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled())
            SibTr.entry(this, tc, "send", new Object[] {buffer, ""+segmentType, ""+priority, ""+pooledBuffersHint, sendListener, ""+terminate});

        if (TraceComponent.isAnyTracingEnabled()) {
            String msg = null;

            if (partOfExchange) msg = "System connection level exchange ";
            else msg = "System connection level send ";

            msg += Integer.toHexString(segmentType)+" ("+JFapChannelConstants.getSegmentName(segmentType)+")";

            JFapUtils.debugSummaryMessage(tc, this, null, msg);
        }

        long sendAmount = 0;

        preSendProcessing(terminate);

        try
        {
            sendAmount = buffer.prepareForTransmission();

            priorityQueue.queue(buffer,
                    segmentType,
                    0,
                    priority,
                    sendListener,
                    null,
                    this,
                    0,
                    pooledBuffersHint,
                    partOfExchange,
                    sendAmount,
                    terminate,
                    throttlingPolicy);

            // Proddle the write callback to get it to leap into action and start
            // sending data from the priority queue.
            writeCompletedCallback.proddle();
        }
        finally
        {
            postSendProcessing();

            // if request was terminal, wait for close to complete.
            if (terminate) priorityQueue.waitForCloseToComplete();
        }
        // end D203646

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "send", ""+sendAmount);
        return sendAmount;
    }
    // end F181603.2

    protected void sendHeartbeat() throws SIConnectionDroppedException, SIConnectionLostException
    {
        InternalJFapByteBuffer buffer = new InternalJFapByteBuffer();

        send(buffer,
                JFapChannelConstants.SEGMENT_HEARTBEAT,
                Conversation.PRIORITY_HEARTBEAT,
                true,
                false,
                ThrottlingPolicy.DO_NOT_THROTTLE,
                null,
                false);
    }

    public void setAttachment(Object attachment)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "setAttachment", attachment);
        userAttachment = attachment;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "setAttachment");
    }

    public Object getAttachment()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getAttachment");
        final Object returnValue = userAttachment;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getAttachment", returnValue);
        return returnValue;
    }

    /**
     * Notification that this connection has been closed by our peer.
     * All conversations using the connection have been invalidated by this time
     * this method is called.
     * The implementation should remove any references being maintained to the connection.
     */
    protected abstract void connectionClosedByPeer();

    /**
     * Notification that one of our conversations has closed.  Called from the conversation class.
     * @param c
     */
    public abstract void closeNotification(Conversation c);

    /**
     * Returns an array of the conversations using this link.
     * @return Conversation[] The conversations using this link.
     */
    // begin F172937
    public Conversation[] getConversations()                                // D179183
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getConversations");

        // begin D179183
        Conversation returnConversations[];
        synchronized(conversationTable)                                         // D179183
        {
            Iterator iterator = conversationTable.iterator();
            ArrayList<Conversation> list = new ArrayList<Conversation>();

            while (iterator.hasNext()) list.add((Conversation)iterator.next());

            returnConversations = new Conversation[list.size()];

            for (int i=0; i < returnConversations.length; ++i)
            {
                returnConversations[i] = list.get(i);
                if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(tc, "conversation "+i, returnConversations[i]);
            }

        }
        // end D179183
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getConversations", returnConversations);
        return returnConversations;
    }
    // end F172937

    /**
     * Returns the next request number to use as part of the next transmission sent on this connection.
     */
    protected byte getNextRequestNumber()
    {
        return (byte) nextRequestNumber.getAndIncrement();
    }

    /**
     * Attempt to close the physcial connection.  This is achieved by
     * flowing a "physical close" notification to our peer, then
     * closing the socket.
     * This method only has an effect for an open connection.
     */
    public void physicalClose(boolean notifyPeer)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "physicalClose", ""+notifyPeer);

        if (TraceComponent.isAnyTracingEnabled()) JFapUtils.debugSummaryMessage(tc, this, null, "Connection close requested (notifyPeer="+notifyPeer+")");

        boolean performCloseOnThisThread = false;

        try (L l = obtainLock(stateLock.writeLock()))
        {
            if (state.currentState() == OPEN)
            {
                if (threadsSendingData.get() > 0)
                {
                    // If there are currently threads sending data then defer the close
                    if (notifyPeer) state.changeState(OPEN, CLOSE_NOTIFYING_PEER_PENDING);
                    else state.changeState(OPEN,CLOSE_PENDING);

                    if (TraceComponent.isAnyTracingEnabled()) JFapUtils.debugSummaryMessage(tc, this, null, "Deferring call to close");
                }
                else
                {
                    state.changeState(OPEN, CLOSE_IN_PROGRESS);
                    performCloseOnThisThread = true;
                }
            }
        }

        if (performCloseOnThisThread)
        {
            nonThreadSafePhysicalClose(notifyPeer);
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "physicalClose");
    }
    // end F174772

    /**
     * This is the method called from within the implementation of invalidateImpl
     * to close the socket.
     */
    public void physicalCloseFromInvalidateImpl(boolean notifyPeer) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "physicalCloseFromInvalidateImpl", Boolean.valueOf(notifyPeer));

        // We do not perform any state checking here.
        // This is because we know that this thread should be the one that should
        // perform the physical close, and there is close-once checking inside the
        // nonThreadSafePhysicalClose method.
        // This wrapper exists only to provide a public interface for invalidateImpl implementations to call into
        nonThreadSafePhysicalClose(notifyPeer);

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "physicalCloseFromInvalidateImpl");
    }

    /**
     * The code that actually performs the close.  This is not thread safe and
     * is intended to be called from the physicalClose method.  The physicalClose
     * method takes care of ensuring that at most one thread can enter this method
     */
    private void nonThreadSafePhysicalClose(boolean notifyPeer)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "nonThreadSafePhysicalClose", ""+notifyPeer);


        if (notifyPeer)                                                   // F176003
        {                                                                 // F176003
            // Send a flow notifying our peer of our intention to close the
            // socket.
            // begin F176003
            InternalJFapByteBuffer buffer = new InternalJFapByteBuffer();
            buffer.put((byte)0x01);

            try
            {
                send(buffer,
                        JFapChannelConstants.SEGMENT_PHYSICAL_CLOSE,
                        Conversation.PRIORITY_HIGH,
                        true,
                        false,
                        ThrottlingPolicy.DO_NOT_THROTTLE,
                        null,
                        true);
            }
            //  end F176003
            catch (SIException e)
            {
                FFDCFilter.processException(e, "com.ibm.ws.sib.jfapchannel.impl.Connection",
                        JFapChannelConstants.CONNECTION_PHYSICALCLOSE_01,
                        getDiagnostics(true));
                if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) SibTr.exception(this, tc, e);
            }
        }
        else
        {
            priorityQueue.close(true);
        }

        // Guard against two threads racing each other into this section of code.
        boolean completeClose;
        try (L l = obtainLock(stateLock.writeLock()))
        {
            completeClose = state.currentState() != CLOSED;
            state.changeState(state.currentState(), CLOSED);
        }

        if (completeClose)
        {
            try
            {
                priorityQueue.waitForCloseToComplete();
                readCompletedCallback.physicalCloseNotification();
                writeCompletedCallback.physicalCloseNotification();
                if (vc.requestPermissionToClose((heartbeatInterval+heartbeatTimeout)*1000))
                {
                    //Release any buffers in the channel below as it is now safe to do so.
                    //Note that this also releases the buffers returned by tcpReadCtx/tcpWriteCtx.getBuffer.
                    final WsByteBuffer[] readBuffers = tcpReadCtx.getBuffers();
                    if(readBuffers != null)
                    {
                        for(final WsByteBuffer readBuffer: readBuffers)
                        {
                            //Absorb any exceptions if the buffer gets released by another thread (for example by ConnectionReadCompletedCallback.error).
                            try
                            {
                                readBuffer.release();
                            }
                            catch(RuntimeException e)
                            {
                                //No FFDC code needed
                                if(TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "Caught exception on releasing buffer.", e);
                            }
                        }

                        tcpReadCtx.setBuffers(null);
                    }

                    final WsByteBuffer[] writeBuffers =  tcpWriteCtx.getBuffers();
                    if(writeBuffers != null)
                    {
                        for(final WsByteBuffer writeBuffer: writeBuffers)
                        {
                            //Absorb any exceptions if the buffer gets released by another thread (for example by ConnectionWriteCompletedCallback.error).
                            try
                            {
                                writeBuffer.release();
                            }
                            catch(RuntimeException e)
                            {
                                //No FFDC code needed
                                if(TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "Caught exception on releasing buffer.", e);
                            }
                        }

                        tcpWriteCtx.setBuffers(null);
                    }

                    connChannel.close(vc, new Exception());
                }
                else
                {
                    FFDCFilter.processException(new Exception("Did not get permission to close"),
                            "com.ibm.ws.sib.jfapchannel.impl.Connection",
                            JFapChannelConstants.CONNECTION_PHYSICALCLOSE_03,
                            this);
                    if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "Did not get permission to close!");
                }
            }
            finally
            {
                // Notify any ConnectionCloseListeners and then null them out so they can't be called again.
                //Do this in a finally block as it ALWAYS needs to be done.
                notifyCloseListeners();
            }
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "nonThreadSafePhysicalClose");
    }

    /**
     * Sets the heartbeat interval on this connection.
     * @param seconds
     */
    protected void setHeartbeatInterval (final int seconds) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "setHeartbeatInterval","seconds="+seconds);
        if (seconds < 0) throw new SIErrorException(nls.getFormattedMessage("CONNECTION_INTERNAL_SICJ0043", null, "CONNECTION_INTERNAL_SICJ0043"));
        heartbeatInterval = seconds;
        if(isUsingNetty()) {
        	try {
        		((NettyNetworkConnection) vc).setHearbeatInterval(seconds);
        	} catch (NettyException e) {
        		throw new SIErrorException(e);
        	}
        }
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "setHeartbeatInterval");
    }

    /**
     * Returns the last set value for the heartbeat interval on this
     * connection.  If no value was set, this will take the default
     * value from the client or server connection manager at the
     * point the connection was created.
     * @return int
     */
    protected int getHeartbeatInterval () {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getHeartbeatInterval");
        final int rc = heartbeatInterval; // Snap shot volatile value to enure we trace what we return
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getHeartbeatInterval", "rc="+rc);
        return rc;
    }

    /** Allow access to heartbeat interval without trace entry/exit, for toString methods */
    protected int getHeartbeatIntervalForToString() {
        return heartbeatInterval;
    }

    /**
     * Sets the heartbeat timeout on this connection.
     * @param seconds
     */
    protected void setHeartbeatTimeout (int seconds) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "setHeartbeatTimeout", "seconds="+seconds);
        if (seconds < 1) throw new SIErrorException(nls.getFormattedMessage("CONNECTION_INERNAL_SICJ0043", null, "CONNECTION_INERNAL_SICJ0043"));
        heartbeatTimeout = seconds;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "setHeartbeatTimeout");
    }

    /**
     * Returns the last set value for the heartbeat timeout on this
     * connection.  If no value was set, this will take the default
     * value from the client or server connection manager at the
     * point the connection was created.
     * @return int
     */
    protected int getHeartbeatTimeout() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getHeartbeatTimeout");
        final int rc = heartbeatTimeout; // Snap shot volatile value to enure we trace what we return
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getHeartbeatTimeout", "rc="+rc);
        return rc;
    }

    /** Allow access to heartbeat timeout without trace entry/exit, for toString methods */
    protected int getHeartbeatTimeoutForToString() {
        return heartbeatTimeout;
    }

    /**
     * Thread-safe wrapper for invalidateImpl method.
     * Subclasses are free to implement their own brand of invalidate logic.
     * Locking is all managed from within this class.
     */
    public final void invalidate(boolean notifyPeer, Throwable throwable, String debugReason)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "invalidate", new Object[]{notifyPeer, throwable});

        if (TraceComponent.isAnyTracingEnabled()) JFapUtils.debugSummaryMessage(tc, this, null, "Connection invalidated (reason: "+debugReason+")");

        // These flags are set inside the synchronized(state) block and are used to
        // defer actions (that themselves synchronize on other objects monitors)
        // until the monitor of state has been released.  This avoids possible
        // deadlocks
        boolean purgePriorityQueue = false;
        boolean performInvalidateOnThisThread = false;

        try (L l = obtainLock(stateLock.writeLock()))
        {
          if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
                SibTr.debug(this, tc, "state = "+state+", threadsSendingData = "+threadsSendingData);

            if (state.currentState() == OPEN)
            {
                // In open state, no other threads are attempting to close or
                // invalidate this connection.
                // The decision on whether or not to purge the priority queue is taken
                // based on whether we intend to attempt any further communications with
                // the peer.
                purgePriorityQueue = !notifyPeer;

                if (threadsSendingData.get() > 0)
                {
                    // If there are currently threads sending data then defer the invalidate
                    // until these threads are exiting the send method.
                    if(notifyPeer) state.changeState(OPEN,INVALIDATE_NOTIFYING_PEER_PENDING);
                        else state.changeState(OPEN,INVALIDATE_PENDING);

                    if (TraceComponent.isAnyTracingEnabled()) JFapUtils.debugSummaryMessage(tc, this, null, "Deferring call to invalidate");
                    invalidatePendingThrowable = throwable;
                }
                else
                {
                    // No threads are sending data, and since this thread holds the state
                    // object's monitor, none can start until this synchronized block
                    // is exited.

                    // If we need to notify our peer then set the state to invalidate
                    // notifying peer in progress.  This permits one last transmission
                    // to be made.
                    if(notifyPeer) state.changeState(OPEN,INVALIDATE_NOTIFYING_PEER_IN_PROGRESS);
                      else state.changeState(OPEN,INVALIDATE_IN_PROGRESS);
                    performInvalidateOnThisThread = true;
                }
            }
            else if ((state.currentState() == CLOSE_NOTIFYING_PEER_PENDING) ||
                    (state.currentState() == CLOSE_PENDING))
            {
                // Upgrade a pending close to a pending invalidate.  Regardless of
                // whether the close request intended to notify the peer, we we not
                // try to notify the peer.  This guards against the possibility that
                // we are performing an invalidation as a result of a missed heartbeat
                // because the close notification is somehow blocked in the priority
                // queue.
                purgePriorityQueue = true;
                notifyPeer = false;

                state.changeState(state.currentState(),INVALIDATE_PENDING);
                if (TraceComponent.isAnyTracingEnabled()) JFapUtils.debugSummaryMessage(tc, this, null, "Deferring call to invalidate");

                invalidatePendingThrowable = throwable;
            }
            else if (state.currentState() == CLOSE_IN_PROGRESS)
            {
                // If there is already a close in progress - usurp it by purging
                // the priority queue and not notifying our peer.  This avoids the
                // risk of being blocked on enqueuing things onto the priority queue
                purgePriorityQueue = true;
                notifyPeer = false;
                performInvalidateOnThisThread = true;
                state.changeState(CLOSE_IN_PROGRESS,INVALIDATE_IN_PROGRESS);
            }
        }

        if (purgePriorityQueue)
        {
            priorityQueue.purge();
        }

        if (performInvalidateOnThisThread)
        {
            // PK91199 We have determined that it is safe to invalidate the connection on
            // this thread. Call down into the method that will call invalidateImpl
            // on the sub-class. That class is responsible for calling us back with
            // physicalClose when appropriate.
            nonThreadSafeInvalidate(notifyPeer, throwable);
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "invalidate");
    }

    /**
     * The code that actually performs invalidate processing.  This code is not thread aware
     * and is intended to be called from the invalidate method.  The invalidate method takes
     * care of ensuring that this method is invoked by a single thread and at most once.
     * @param notifyPeer Is an attempt made to notify the peer that the connection is to be
     * torn down?
     * @param throwable The exception which resulted in the connection being invalidated.
     */
    private void nonThreadSafeInvalidate(boolean notifyPeer, Throwable throwable)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled())
            SibTr.entry(this, tc, "nonThreadSafeInvalidate", new Object[]{""+notifyPeer, ""+throwable});

        // Call down into the invalidate implementation of the subclass.
        // This is responsible for calling us back with physicalClose once invalidation is complete.
        invalidateImpl(notifyPeer, throwable);

        // Notify any ConnectionCloseListeners and then null them out so they can't be called again.
        notifyCloseListeners();

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "nonThreadSafeInvalidate");
    }

    /**
     * Remove and notify each registered listener.
     */
    private void notifyCloseListeners()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "notifyCloseListeners");

        Stream.of(ConversationUsageType.values())
                .map(closeListeners::remove)
                .filter(Objects::nonNull)
                .forEach(listener -> {
                    try
                    {
                        listener.connectionClosed(this);
                    }
                    catch (Throwable t)
                    {
                        FFDCFilter.processException(t, "com.ibm.ws.sib.jfapchannel.impl.Connection", CONNECTION_INVALIDATE_01, new Object[]{this, listener});
                        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "Exception invoking close listener", new Object[]{listener, t});
                    }
                });
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "notifyCloseListeners");
    }

    /**
     * Actually invalidates this connection.  For every day operation this should
     * never be called.  When invoked, the implementation should:
     * <ul>
     * <li>Attempt to notify it's peer (depending on the argument)</li>
     * <li>Purge the connection from any tracker with which it is
     *     registered.</li>
     * <li>Wake up any outstanding exchanges with an exception.</li>
     * <li>Send an exception to each conversation receive listener.</li>
     * <li>Mark all the conversations that use the connection as
     *     invalid.</li>
     * <li>Close the underlying physical socket, using
     *     the physicalCloseFromInvalidateImpl method</li>
     * </ul>
     * @see #physicalCloseFromInvalidateImpl(boolean)
     * @param notifyPeer When set to true, an attempt is made to notify
     * our peer that we are about to close the socket.
     * @param throwable The exception to link to the "JFapConnectionBrokenException"
     * passed to outstanding exchanges and conversation receive listeners.
     */
    public abstract void invalidateImpl(boolean notifyPeer, Throwable throwable);     // D179183

    /**
     * Wakes up all conversations that share this connection.
     * This is done by invoking the wakeupAllWithException method
     * on each conversation.
     * @see ConversationImpl#wakeupAllWithException(SIConnectionLostException, boolean)
     * @param exception The exception to be delivered.
     * @param alwaysDeliverToConversationReceiveListener Should the conversation
     * receive listner always be driven with the exception?
     */
    // begin F175658
    protected void wakeupAllConversationsWithException(SIException exception,
            boolean alwaysDeliverToConversationReceiveListener)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled())
            SibTr.entry(this, tc, "wakeupAllConversationsWithException", new Object[] {exception, ""+alwaysDeliverToConversationReceiveListener}); // F176003

        ArrayList<ConversationImpl> toInvalidateList = new ArrayList<ConversationImpl>();
        synchronized(conversationTable)
        {
            Iterator conversationIterator = conversationTable.iterator();

            while (conversationIterator.hasNext())
            {
                toInvalidateList.add((ConversationImpl)conversationIterator.next());
            }

            conversationTable.clear();
        }

        for (int i = 0; i < toInvalidateList.size(); ++i)
        {
            ConversationImpl conversation = toInvalidateList.get(i);
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "conversation: "+conversation);
            conversation.invalidate(exception);
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "wakeupAllConversationsWithException");     // F176003
    }
    // end F175658

    // begin F181603.2
    /**
     * Processes recepit of connection based transmissions.  When a transmission
     * is received which does not contain a conversation header - it is routed
     * to this method to deal with.
     *
     * @returns true if this transmission resulted in the connection being closed, which means any buffers used by the connection have now been released.
     */
    protected boolean processData(int segmentType,
            int priority,
            boolean isPooled,
            boolean isExchange,
            WsByteBuffer data)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled())
            SibTr.entry(this, tc, "processData", new Object[]{""+segmentType, ""+priority, ""+isPooled, ""+isExchange, data});

        boolean closed = false;

        switch(segmentType)
        {
        case(JFapChannelConstants.SEGMENT_HEARTBEAT):
            processHeartbeat();
        break;
        case(JFapChannelConstants.SEGMENT_HEARTBEAT_RESPONSE):
            readCompletedCallback.heartbeatReceived();
        break;
        case(JFapChannelConstants.SEGMENT_PHYSICAL_CLOSE):
            processPhysicalClose();
        closed = true;
        break;
        default:
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "unexpected segment type: "+segmentType);
            invalidate(false, new RuntimeException(), "received unexpected segment for a connection based transmission");  // D224570
            break;
        }
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "processData", Boolean.valueOf(closed));
        return closed;
    }
    // end F181603.2

    // begin F181603.2
    /** Invoked to process heartbeat requests on this connection. */
    private void processHeartbeat()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "processHeartbeat");

        // Deal with a heartbeat by firing a response straight back
        InternalJFapByteBuffer buffer = new InternalJFapByteBuffer();
        try
        {
            send(buffer,
                    JFapChannelConstants.SEGMENT_HEARTBEAT_RESPONSE,
                    0,
                    0,
                    Conversation.PRIORITY_HEARTBEAT,
                    true,
                    false,
                    ThrottlingPolicy.DO_NOT_THROTTLE,
                    null,
                    null,
                    false);
        }
        catch (SIException e)
        {
            FFDCFilter.processException
            (e, "com.ibm.ws.sib.jfapchannel.impl.Connection", JFapChannelConstants.CONNECTION_PROCESSHEARTBEAT_01);
            if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) SibTr.exception(this, tc, e);
            invalidate(false, e, "SIException thrown from a system send operation");   // D224570
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "processHeartbeat");
    }
    // end F181603.2

    // begin F181603.2
    /** Invoked to deal with a physical close request for this connection */
    private void processPhysicalClose()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "processPhysicalClose");

        readCompletedCallback.stopReceiving();

        // Iterate through live conversations on this physical
        // connection.  For each one, wake up any exchanges and
        // deliver a exception to its conversation receive listener.
        //We need to do the iteration while holding the lock on conversationTable to prevent
        //ConcurrentModificationExceptions. However we call invalidate when not holding the lock
        //to prevent deadlocks creeping in.

        final List<ConversationImpl> conversations = new ArrayList<ConversationImpl>();

        synchronized(conversationTable)
        {
            final Iterator conversationIterator = conversationTable.iterator();
            while(conversationIterator.hasNext())
            {
                conversations.add((ConversationImpl)conversationIterator.next());
            }
        }

        if (!conversations.isEmpty())
        {
            // If we found open conversations, we need a suitable exception to pass to them,
            // which will be thrown to any API calls that are in progress.
            SIConnectionLostException closeException = null;
            new SIConnectionLostException(nls.getFormattedMessage("CONVERSATIONIMPL_INVALIDATE_SICJ0045",
                    new Object[] {remoteHostAddress, chainName} , null));

            for(final ConversationImpl conversation: conversations)
            {
                if(TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) SibTr.debug(this, tc, "invoking invalidate on conversation " + conversation);
                conversation.invalidate(closeException);
            }
        }

        // PK54706 - we need to actually perform the physical close,
        // and respond to the SEGMENT_PHYSICAL_CLOSE from the peer
        // PK91199 - we should not send a response back to a physical close, as the remote
        // server will have closed the socket after sending us this packet.
        physicalClose(false);

        // Notify the inbound/outbound implementation that the connection is closed
        connectionClosedByPeer();

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "processPhysicalClose");
    }
    // end F181603.2

    // begin F181603.2
    /** Returns the maximum transmission size being used for this connection */
    protected int getMaxTransmissionSize()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getMaxTransmissionSize");
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getMaxTransmissionSize", ""+maxTransmissionSize);
        return maxTransmissionSize;
    }
    // end F181603.2

    // begin F181603.2
    /**
     * Sets the maximum transmission size being used for this connection.
     * <strong>Note:</strong> no particular care is taken to ensure that this
     * will not result in protocol errors.  Set this to a smaller value than
     * inflight transmissions at your own risk!
     */
    protected void setMaxTransmissionSize(int newMaxTransmissionSize)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "setMaxTransmissionSize", ""+newMaxTransmissionSize);
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getMaxTransmissionSize");
        maxTransmissionSize = newMaxTransmissionSize;
    }
    // end F181603.2

    /**
     * Invoked when handshaking is complete for this connection.  Invoking
     * the handshake complete method on any conversation created for this
     * connection should simply invoke this method.  The implementing class
     * must decide how to deal with this event.  In the inbound case, simply
     * ignoring it may be an adequate action.
     */
    protected abstract void handshakeComplete();                            // D181493

    /**
     * Invoked when handshaking is complete for this connection, but has failed.
     * @see Connection#handshakeComplete()
     */
    protected abstract void handshakeFailed();                              // D221433

    // begin F191566
    /**
     * Clone a conversation on this connection.  The implementor of this method should
     * return a "cloned" conversation on this connection.
     * @param receiveListener
     * @return Conversation
     */
    protected abstract Conversation cloneConversation(ConversationReceiveListener receiveListener)
            throws SIResourceException;
    // end F191566

    // begin D203646
    public ConversationImpl getConversationById(int id)
    {
        return conversationTable.get(id);
    }
    // end D203646

    // begin D203646
    protected void removeConversationById(int id)
    {
        conversationTable.remove(id);
    }
    // end D203646

    protected abstract ConversationMetaData getMetaData();                  // D196678.10.1

    // begin F193735.3
    public Conversation.ConversationType getConversationType()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getConversationType");
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getConversationType", conversationType);
        return conversationType;
    }
    // end F193735.3

    // begin F193735.3
    protected void setConversationType(Conversation.ConversationType type)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "setConversationType", type);

        if ((conversationType == Conversation.UNKNOWN) && (type != Conversation.UNKNOWN))
        {
            conversationType = type;


            priorityQueue.setType(type);
        }
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "setConversationType");
    }
    // end F193735.3

    /**
     * Adds a ConnectionClosedListener for the specified usage type to the set of ConnectionClosedListeners associated with this Connection.
     * If there is already a ConnectionClosedListener for the specified usage type then it is overwritten.
     *
     * @param listener
     * @param usageType
     */
    public void addConnectionClosedListener(ConnectionClosedListener listener, ConversationUsageType usageType)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "addConnectionClosedListener", new Object[] {listener, usageType});

        ConnectionClosedListener oldListener = closeListeners.put(usageType, listener);

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "addConnectionClosedListener", oldListener);
    }

    /**
     * Get the ConnectionClosedListener for the specified usage type or null if there is none.
     *
     * @param usageType
     * @return
     */
    public ConnectionClosedListener getConnectionClosedListener(ConversationUsageType usageType)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getConnectionClosedListener", usageType);

        ConnectionClosedListener listener = closeListeners.get(usageType);

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getConnectionClosedListener", listener);
        return listener;
    }

    /**
     * Implementors should return true from this method if the connection is an inbound
     * connection.
     *
     * @return Returns true if this connection is an inbound connection.
     */
    protected abstract boolean isInbound();

    protected HandshakeProperties getHandshakeProperties()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getHandshakeProperties");
        final HandshakeProperties result = handshakeProperties;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getHandshakeProperties", result);
        return result;
    }

    protected void setHandshakeProperties(HandshakeProperties handshakeProperties)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "setHandshakeProperties", handshakeProperties);
        this.handshakeProperties = handshakeProperties;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "setHandshakeProperties");
    }

    protected ConnectionEventRecorder getConnectionEventRecorder()
    {
        return eventRecorder;
    }

    protected boolean isLoggingIOEvents()
    {
        return logIOEvents;
    }
    
    public boolean isUsingNetty() {
 	   return this.usingNetty;
    }


    public String getDiagnostics(boolean includeConversations)
    {
        StringBuffer sb = new StringBuffer();
        sb.append(eventRecorder);
        if (includeConversations)
        {
            synchronized(conversationTable)
            {
                for (Iterator iterator = conversationTable.iterator(); iterator.hasNext();)
                {
                    sb.append(iterator.next());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Returns true if a close operation has been deferred, false otherwise.
     *
     * @return
     */
    public boolean isCloseDeferred()
    {
        try (L l = obtainLock(stateLock.readLock()))
        {
            return (state.currentState() == CLOSE_NOTIFYING_PEER_PENDING || state.currentState() == CLOSE_PENDING);
        }
    }

    /**
     * Returns true if an invalidate operation has been deferred, false otherwise.
     *
     * @return
     */
    public boolean isInvalidateDeferred()
    {
        try (L l = obtainLock(stateLock.readLock()))
        {
            return (state.currentState() == INVALIDATE_NOTIFYING_PEER_PENDING || state.currentState() == INVALIDATE_PENDING);
        }
    }


    /**
     * setSchemaSet
     * Sets the schemaSet instance variable to the MFP SchemaSet for this Connection.
     *
     * @param schemaSet   The SchemaSet which pertains to this Connection.
     */
    public void setSchemaSet(ConnectionSchemaSet schemaSet)
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "setSchemaSet", schemaSet);
        this.schemaSet = schemaSet;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "setSchemaSet");
    }

    /**
     * getSchemaSet
     * Returns the MFP SchemaSet which pertains to this Connection.
     *
     * @throws SIConnectionDroppedException Thrown if the underlying connection is closed.
     *
     * @return ConnectionSchemaSet The SchemaSet belonging to this Connection.
     */
    public ConnectionSchemaSet getSchemaSet() throws SIConnectionDroppedException
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "getSchemaSet");

        try (L l = obtainLock(stateLock.readLock()))
        {
            if (state.currentState() != OPEN) {
                throw new SIConnectionDroppedException(nls.getFormattedMessage("CONNECTION_CLOSED_SICJ0044", null, "CONNECTION_CLOSED_SICJ0044"));
            }
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "getSchemaSet",  this.schemaSet);
        return this.schemaSet;
    }

    /**
     * @return a String that uniquely represents this connection. The value returned must be the same regardless of whether this is a
     * client or server side connection.
     */
    public abstract String getEyeCatcher();

    /*
     * (non-Javadoc)
     * @see com.ibm.ws.sib.jfapchannel.ConnectionInterface#isClosed()
     */
    public boolean isClosed()
    {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.entry(this, tc, "isClosed");
        try (L l = obtainLock(stateLock.readLock()))
        {
            boolean answer = (state.currentState() != OPEN);
            if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) SibTr.exit(this, tc, "isClosed", answer);
            return answer;
        }
    }
}
