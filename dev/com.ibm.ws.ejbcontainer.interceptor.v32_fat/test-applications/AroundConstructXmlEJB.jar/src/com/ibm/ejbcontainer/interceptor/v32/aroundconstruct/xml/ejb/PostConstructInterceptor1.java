/*******************************************************************************
 * Copyright (c) 2014, 2021 IBM Corporation and others.
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
package com.ibm.ejbcontainer.interceptor.v32.aroundconstruct.xml.ejb;

import javax.interceptor.InvocationContext;

public class PostConstructInterceptor1 {

    //@PostConstruct
    public Object postConstruct(InvocationContext ctx) throws Exception {
        Object o = ctx.proceed();
        
        if (o != null) {
            throw new Exception("Expected ctx.proceed() to return null even though postConstruct in PostConstructInterceptor2 returned a String.");
        }
        
        return o;
    }

}
