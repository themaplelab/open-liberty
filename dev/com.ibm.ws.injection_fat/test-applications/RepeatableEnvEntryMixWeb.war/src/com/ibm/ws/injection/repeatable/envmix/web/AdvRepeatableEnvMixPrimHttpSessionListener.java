/*******************************************************************************
 * Copyright (c) 2014, 2018 IBM Corporation and others.
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
package com.ibm.ws.injection.repeatable.envmix.web;

import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

@Resource(name = "com.ibm.ws.injection.repeatable.envmix.web.AdvRepeatableEnvMixPrimHttpSessionListener/JNDI_Ann_char", type = Character.class)
@Resource(name = "com.ibm.ws.injection.repeatable.envmix.web.AdvRepeatableEnvMixPrimHttpSessionListener/JNDI_Ann_byte", type = Byte.class)
@Resource(name = "com.ibm.ws.injection.repeatable.envmix.web.AdvRepeatableEnvMixPrimHttpSessionListener/JNDI_Ann_short", type = Short.class)
@Resource(name = "com.ibm.ws.injection.repeatable.envmix.web.AdvRepeatableEnvMixPrimHttpSessionListener/JNDI_Ann_int", type = Integer.class)
@Resource(name = "com.ibm.ws.injection.repeatable.envmix.web.AdvRepeatableEnvMixPrimHttpSessionListener/JNDI_Ann_long", type = Long.class)
@Resource(name = "com.ibm.ws.injection.repeatable.envmix.web.AdvRepeatableEnvMixPrimHttpSessionListener/JNDI_Ann_boolean", type = Boolean.class)
@Resource(name = "com.ibm.ws.injection.repeatable.envmix.web.AdvRepeatableEnvMixPrimHttpSessionListener/JNDI_Ann_double", type = Double.class)
@Resource(name = "com.ibm.ws.injection.repeatable.envmix.web.AdvRepeatableEnvMixPrimHttpSessionListener/JNDI_Ann_float", type = Float.class)
public class AdvRepeatableEnvMixPrimHttpSessionListener implements HttpSessionListener {
    private static final String CLASS_NAME = AdvRepeatableEnvMixPrimHttpSessionListener.class.getName();
    private final static Logger svLogger = Logger.getLogger(CLASS_NAME);

    // Expected Injected Value Constants as defined in the XML
    private static final char E_CHAR = 'o';
    private static final byte E_BYTE = 1;
    private static final short E_SHORT = 1;
    private static final int E_INTEGER = 158;
    private static final long E_LONG = 254L;
    private static final boolean E_BOOL = true;
    private static final double E_DOUBLE = 856.93D;
    private static final float E_FLOAT = 548.72F;

    // Resources to be field injected via annotation but described by XML
    // The XML should override the given values
    @Resource
    private char ifchar = 't';
    @Resource
    private byte ifbyte = Byte.MAX_VALUE;
    @Resource
    private short ifshort = Short.MAX_VALUE;
    @Resource
    private int ifint = Integer.MAX_VALUE;
    @Resource
    private long iflong = Long.MAX_VALUE;
    @Resource
    private boolean ifboolean = false;
    @Resource
    private double ifdouble = Double.MAX_VALUE;
    @Resource
    private float iffloat = Float.MAX_VALUE;

    // Resources to be method injected via annotation but described by XML
    private char imchar;
    private byte imbyte;
    private short imshort;
    private int imint;
    private long imlong;
    private boolean imboolean;
    private double imdouble;
    private float imfloat;

    private final String[] fieldNames = { "ifchar", "ifbyte", "ifshort", "ifint", "iflong", "ifboolean", "ifdouble", "iffloat" };
    private final String[] methodNames = { "imchar", "imbyte", "imshort", "imint", "imlong", "imboolean", "imdouble", "imfloat" };
    private final String[] clNames = { "JNDI_Ann_char", "JNDI_Ann_byte", "JNDI_Ann_short", "JNDI_Ann_int",
                                       "JNDI_Ann_long", "JNDI_Ann_boolean", "JNDI_Ann_double", "JNDI_Ann_float" };

    void preventFinal() {
        ifchar = 't';
        ifbyte = Byte.MAX_VALUE;
        ifshort = Short.MAX_VALUE;
        ifint = Integer.MAX_VALUE;
        iflong = Long.MAX_VALUE;
        ifboolean = false;
        ifdouble = Double.MAX_VALUE;
        iffloat = Float.MAX_VALUE;
    }

    @Override
    public void sessionCreated(HttpSessionEvent arg0) {
        svLogger.info("Prim Http Session: Session created...");
        processRequest(WCEventTracker.KEY_LISTENER_CREATED_AdvRepeatableEnvMixPrimHttpSessionListener);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent arg0) {
        // Do Nothing
    }

    public void processRequest(String key) {
        // Test Field injection
        RepeatableEnvMixPrimTestHelper.testRepeatableEnvMixPrimInjection(CLASS_NAME, key, ifchar, ifbyte, ifshort, ifint, iflong, ifboolean, ifdouble, iffloat, fieldNames);
        // Test Method Injection
        RepeatableEnvMixPrimTestHelper.testRepeatableEnvMixPrimInjection(CLASS_NAME, key, imchar, imbyte, imshort, imint, imlong, imboolean, imdouble, imfloat, methodNames);
        // Test class-level JNDI lookup
        RepeatableEnvMixPrimTestHelper.testRepeatableEnvMixPrimInjection(CLASS_NAME, key, E_CHAR, E_BYTE, E_SHORT, E_INTEGER, E_LONG, E_BOOL, E_DOUBLE, E_FLOAT, clNames);
    }

    @Resource
    public void setImchar(char imchar) {
        this.imchar = imchar;
    }

    @Resource
    public void setImbyte(byte imbyte) {
        this.imbyte = imbyte;
    }

    @Resource
    public void setImshort(short imshort) {
        this.imshort = imshort;
    }

    @Resource
    public void setImint(int imint) {
        this.imint = imint;
    }

    @Resource
    public void setImlong(long imlong) {
        this.imlong = imlong;
    }

    @Resource
    public void setImboolean(boolean imboolean) {
        this.imboolean = imboolean;
    }

    @Resource
    public void setImdouble(double imdouble) {
        this.imdouble = imdouble;
    }

    @Resource
    public void setImfloat(float imfloat) {
        this.imfloat = imfloat;
    }
}