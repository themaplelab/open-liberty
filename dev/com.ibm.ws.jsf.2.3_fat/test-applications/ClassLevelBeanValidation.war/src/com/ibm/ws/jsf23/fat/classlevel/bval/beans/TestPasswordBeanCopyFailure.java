/*******************************************************************************
 * Copyright (c) 2017, 2018 IBM Corporation and others.
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
package com.ibm.ws.jsf23.fat.classlevel.bval.beans;

import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Simple RequestScoped bean
 * This bean does not implement Serializable or Cloneable and it does not have a copy constructor. Therefore it will not be copied.
 */
@Named
@RequestScoped
@Password(groups = com.ibm.ws.jsf23.fat.classlevel.bval.beans.PasswordValidationGroup.class)
public class TestPasswordBeanCopyFailure implements PasswordHolder {

    @NotNull
    private String password1;
    @NotNull
    private String password2;

    //This private contructor will cause an IllegalAccessException, which is expected for this test.
    private TestPasswordBeanCopyFailure() {}

    @NotNull(groups = com.ibm.ws.jsf23.fat.classlevel.bval.beans.PasswordValidationGroup.class)
    @Size(max = 16, min = 8, message = "Password must be between 8 and 16 characters long",
          groups = com.ibm.ws.jsf23.fat.classlevel.bval.beans.PasswordValidationGroup.class)
    @Override
    public String getPassword1() {
        return password1;
    }

    public void setPassword1(String password1) {
        this.password1 = password1;
    }

    @NotNull(groups = com.ibm.ws.jsf23.fat.classlevel.bval.beans.PasswordValidationGroup.class)
    @Size(max = 16, min = 8, message = "Password must be between 8 and 16 characters long",
          groups = com.ibm.ws.jsf23.fat.classlevel.bval.beans.PasswordValidationGroup.class)
    @Override
    public String getPassword2() {
        return password2;
    }

    public void setPassword2(String password12) {
        this.password2 = password12;
    }

}
