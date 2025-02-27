/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
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
package com.ibm.websphere.security.wim.scim20.model;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ibm.ws.security.wim.scim20.model.ErrorImpl;

@JsonDeserialize(as = ErrorImpl.class)
public interface Error {
    public List<String> getSchemas();

    public String getScimType();

    public String getDetail();

    public Integer getStatus();
}
