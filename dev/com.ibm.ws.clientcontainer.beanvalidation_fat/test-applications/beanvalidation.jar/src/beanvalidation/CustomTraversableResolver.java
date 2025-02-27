/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
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
package beanvalidation;

import java.lang.annotation.ElementType;

import javax.validation.Path;
import javax.validation.Path.Node;
import javax.validation.TraversableResolver;

/**
 * Simple implementation of a TraversableResolver that tolerates a null
 * parameter for testing purposes.
 */
public class CustomTraversableResolver implements TraversableResolver {

    @Override
    public boolean isCascadable(Object arg0, Node arg1, Class<?> arg2, Path arg3, ElementType arg4) {
        if (arg0.toString().equals("non-cascadable") &&
            arg1 == null &&
            arg2 == null &&
            arg3 == null &&
            arg4 == null) {

            return false;
        }
        return true;
    }

    @Override
    public boolean isReachable(Object arg0, Node arg1, Class<?> arg2, Path arg3, ElementType arg4) {
        if (arg0.toString().equals("non-reachable") &&
            arg1 == null &&
            arg2 == null &&
            arg3 == null &&
            arg4 == null) {

            return false;
        }
        return true;
    }

}
