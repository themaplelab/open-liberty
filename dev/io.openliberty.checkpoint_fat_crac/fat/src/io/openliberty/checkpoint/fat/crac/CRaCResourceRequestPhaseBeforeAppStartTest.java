/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.checkpoint.fat.crac;

import static io.openliberty.checkpoint.fat.crac.FATSuite.getTestMethodNameOnly;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions;

import componenttest.annotation.Server;
import componenttest.annotation.SkipIfCheckpointNotSupported;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.rules.repeater.MicroProfileActions;
import componenttest.rules.repeater.RepeatTests;
import componenttest.topology.impl.LibertyServer;
import io.openliberty.checkpoint.fat.crac.app.request.fail.incorrect.phase.CRaCResourceRequestFailIncorrectPhaseServlet;
import io.openliberty.checkpoint.spi.CheckpointPhase;

@RunWith(FATRunner.class)
@SkipIfCheckpointNotSupported
public class CRaCResourceRequestPhaseBeforeAppStartTest {
    public static final String APP_NAME = "testApp";
    public static final String APP_PACKAGE = CRaCResourceRequestFailIncorrectPhaseServlet.class.getPackage().getName();

    @Rule
    public TestName testName = new TestName();

    @Server("cracFATServer")
    public static LibertyServer server;

    @ClassRule
    public static RepeatTests repeatTest = MicroProfileActions.repeat("cracFATServer", TestMode.FULL, //
                                                                      MicroProfileActions.MP41, // first test in LITE mode
                                                                      // rest are FULL mode
                                                                      MicroProfileActions.MP50, MicroProfileActions.MP60);

    @BeforeClass
    public static void copyAppToDropins() throws Exception {
        ShrinkHelper.defaultApp(server, APP_NAME, new DeployOptions[] { DeployOptions.OVERWRITE }, APP_PACKAGE);
    }

    @Before
    public void setUp() throws Exception {
        // BEFORE_APP_START is the wrong phase, for app initiated checkpoint must use APP_REQUESTED
        server.setCheckpoint(CheckpointPhase.BEFORE_APP_START, true,
                             server -> {
                                 assertNotNull("'SRVE0169I: ' message not found in log before rerstore",
                                               server.waitForStringInLogUsingMark("SRVE0169I: Loading Web Module: " + APP_NAME, 0));
                             });
    }

    @Test
    public void testRequestCheckpointBeforeStartPhase() throws Exception {
        server.startServer(getTestMethodNameOnly(testName) + ".log");
        assertNotNull("'CWWKZ0001I: ' message not found in log.",
                      server.waitForStringInLogUsingMark("CWWKZ0001I: Application " + APP_NAME + " started", 0));
        assertNotNull("Application did not get CheckpointException", server.waitForStringInLogUsingMark("TESTING - got CheckpointException: CWWKC0553E", 0));
    }

    @After
    public void tearDown() throws Exception {
        server.stopServer();
    }

}
