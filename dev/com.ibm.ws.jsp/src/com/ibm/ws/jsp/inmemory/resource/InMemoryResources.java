/*******************************************************************************
 * Copyright (c) 1997, 2005 IBM Corporation and others.
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
package com.ibm.ws.jsp.inmemory.resource;

import java.io.Writer;

public interface InMemoryResources {
    public Writer getGeneratedSourceWriter();
    public char[] getGeneratedSourceChars();
    public byte[] getClassBytes(String className);
    public void setClassBytes(byte[] bytes, String className);
}
