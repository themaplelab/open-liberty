/*******************************************************************************
 * Copyright (c) 2019, 2023 IBM Corporation and others.
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
package com.ibm.ws.ejbcontainer.async.fat.tests;

import static junit.framework.Assert.assertNotNull;

import java.util.Collections;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions;
import com.ibm.ws.ejbcontainer.async.fat.secure.web.AsyncSecureServlet;

import componenttest.annotation.Server;
import componenttest.annotation.TestServlet;
import componenttest.annotation.TestServlets;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.RepeatTestFilter;
import componenttest.rules.repeater.EE8FeatureReplacementAction;
import componenttest.rules.repeater.FeatureReplacementAction;
import componenttest.rules.repeater.JakartaEE10Action;
import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.rules.repeater.JakartaEEAction;
import componenttest.rules.repeater.RepeatTests;
import componenttest.topology.impl.LibertyServer;

@RunWith(FATRunner.class)
public class AsyncSecureTests extends AbstractTest {

    @Server("com.ibm.ws.ejbcontainer.async.fat.AsyncSecureServer")
    @TestServlets({ @TestServlet(servlet = AsyncSecureServlet.class, contextRoot = "AsyncSecureTestWeb") })
    public static LibertyServer server;

    @Override
    public LibertyServer getServer() {
        return server;
    }

    @ClassRule
    public static RepeatTests r = RepeatTests.with(FeatureReplacementAction.EE7_FEATURES().fullFATOnly().forServers("com.ibm.ws.ejbcontainer.async.fat.AsyncSecureServer")).andWith(FeatureReplacementAction.EE8_FEATURES().forServers("com.ibm.ws.ejbcontainer.async.fat.AsyncSecureServer")).andWith(new JakartaEE9Action().fullFATOnly().forServers("com.ibm.ws.ejbcontainer.async.fat.AsyncSecureServer")).andWith(new JakartaEE10Action().fullFATOnly().forServers("com.ibm.ws.ejbcontainer.async.fat.AsyncSecureServer"));

    @BeforeClass
    public static void beforeClass() throws Exception {
        String eeVersion = JakartaEEAction.isEE10OrLaterActive() ? "EE10" : JakartaEEAction.isEE9Active() ? "EE9" : RepeatTestFilter.isRepeatActionActive(EE8FeatureReplacementAction.ID) ? "EE8" : "";

        // cleanup from prior repeat actions
        server.deleteAllDropinApplications();
        server.removeAllInstalledAppsForValidation();

        // start server; installing no applications until security is ready
        server.startServer();

        // verify the appSecurity-2.0 feature is ready
        assertNotNull("Security service did not report it was ready", server.waitForStringInLogUsingMark("CWWKS0008I"));
        assertNotNull("LTPA configuration did not report it was ready", server.waitForStringInLogUsingMark("CWWKS4105I"));
        server.setMarkToEndOfLog();

        // Use ShrinkHelper to build the Ears & Wars

        //#################### InitTxRecoveryLogApp.ear (Automatically initializes transaction recovery logs)
        JavaArchive InitTxRecoveryLogEJBJar = ShrinkHelper.buildJavaArchive("InitTxRecoveryLogEJB.jar", "com.ibm.ws.ejbcontainer.init.recovery.ejb.");

        EnterpriseArchive InitTxRecoveryLogApp = ShrinkWrap.create(EnterpriseArchive.class, "InitTxRecoveryLogApp.ear");
        InitTxRecoveryLogApp.addAsModule(InitTxRecoveryLogEJBJar);

        // Only after the server has started and appSecurity-2.0 feature is ready,
        // then allow the @Startup InitTxRecoveryLog bean to start.
        ShrinkHelper.exportDropinAppToServer(server, InitTxRecoveryLogApp, DeployOptions.SERVER_ONLY);

        //#################### AsyncSecureTestApp.ear
        JavaArchive AsyncSecureTestEJB = ShrinkHelper.buildJavaArchive("AsyncSecureTestEJB.jar", "com.ibm.ws.ejbcontainer.async.fat.secure.ejb.");
        WebArchive AsyncSecureTestWeb = ShrinkHelper.buildDefaultApp("AsyncSecureTestWeb.war", "com.ibm.ws.ejbcontainer.async.fat.secure.web.");
        EnterpriseArchive AsyncSecureTestApp = ShrinkWrap.create(EnterpriseArchive.class, "AsyncSecureTestApp.ear");
        AsyncSecureTestApp.addAsModule(AsyncSecureTestEJB).addAsModule(AsyncSecureTestWeb);
        AsyncSecureTestApp = (EnterpriseArchive) ShrinkHelper.addDirectory(AsyncSecureTestApp, "test-applications/AsyncSecureTestApp.ear/resources");

        // Only after the server has started and appSecurity-2.0 feature is ready,
        // then add AsyncSecureTestApp and update server.xml to include application configuration
        ShrinkHelper.exportAppToServer(server, AsyncSecureTestApp, DeployOptions.SERVER_ONLY, DeployOptions.DISABLE_VALIDATION);

        server.setMarkToEndOfLog();
        server.setServerConfigurationFile("AsyncSecureServer" + eeVersion + ".xml");
        server.waitForConfigUpdateInLogUsingMark(Collections.singleton("AsyncSecureTestApp"));
    }

    @AfterClass
    public static void afterClass() throws Exception {
        server.stopServer();
    }
}