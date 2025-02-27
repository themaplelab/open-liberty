/*******************************************************************************
 * Copyright (c) 2010, 2018 IBM Corporation and others.
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

package com.ibm.ws.ejbcontainer.cdi.jcdi.ejb_int;

import java.util.List;

/**
 * Common local interface for beans that verify CDI Interceptors.
 **/
public interface InterceptorRemote {
    /**
     * Verifies that the interceptors were properly called per the
     * configuration of the bean.
     *
     * @param callStack list to be updated by interceptors
     **/
    public void verifyInterceptorCalls(List<String> callStack);
}
