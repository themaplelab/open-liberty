/*******************************************************************************
 * Copyright (c) 2015, 2023 IBM Corporation and others.
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
package com.ibm.tx.jta.cdi.interceptors;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import com.ibm.tx.TranConstants;
import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.uow.UOWSynchronizationRegistry;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;

@Transactional(value = TxType.REQUIRES_NEW)
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
@Interceptor
public class RequiresNew extends TransactionalInterceptor {
    private static final long serialVersionUID = 1L;

    private static final TraceComponent tc = Tr.register(RequiresNew.class, TranConstants.TRACE_GROUP, TranConstants.NLS_FILE);

    /**
     * <p>If called outside a transaction context, the interceptor must begin a new
     * JTA transaction, the managed bean method execution must then continue
     * inside this transaction context, and the transaction must be completed by
     * the interceptor.</p>
     * <p>If called inside a transaction context, the current transaction context must
     * be suspended, a new JTA transaction will begin, the managed bean method
     * execution must then continue inside this transaction context, the transaction
     * must be completed, and the previously suspended transaction must be resumed.</p>
     */

    @AroundInvoke
    @FFDCIgnore(value = { Exception.class })
    public Object requiresNew(final InvocationContext context) throws Exception {
        if (tc.isEntryEnabled())
            Tr.entry(tc, "requiresNew", context);

        Object ret = null;
        try {
            ret = runUnderUOWManagingEnablement(UOWSynchronizationRegistry.UOW_TYPE_GLOBAL_TRANSACTION, false, context, "REQUIRES_NEW");
        } catch (Exception e) {
            final Exception e1 = processException(context, e);
            if (tc.isEntryEnabled())
                Tr.exit(tc, "required", e1);
            throw e1;
        }

        if (tc.isEntryEnabled())
            Tr.exit(tc, "requiresNew", ret);
        return ret;
    }
}