/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
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
package com.ibm.ws.jmx.internal;

import java.util.Dictionary;

import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * Implement just enough of the ServiceRegistration interface to support the unit tests.
 */
public class MockServiceRegistration<S> implements ServiceRegistration<S> {

    @Override
    public ServiceReference<S> getReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setProperties(Dictionary<String, ?> properties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregister() {}

}
