/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Norwegian Defence Research Establishment / NTNU
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package no.ntnu.okse;

import no.ntnu.okse.core.CoreService;
import no.ntnu.okse.core.messaging.MessageService;
import no.ntnu.okse.core.subscription.SubscriptionService;
import no.ntnu.okse.core.topic.TopicService;
import no.ntnu.okse.db.DB;
import no.ntnu.okse.examples.DummyProtocolServer;
import no.ntnu.okse.protocol.amqp.AMQProtocolServer;
import no.ntnu.okse.protocol.wsn.WSNotificationServer;
import no.ntnu.okse.web.Server;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.ntnunotif.wsnu.base.util.Log;

import java.io.File;
import java.time.Duration;


/**
 * Created by Håkon Ødegård Løvdal (hakloev) on 25/02/15.
 * <p>
 * okse is licenced under the MIT licence.
 */
public class Application {

    // Version
    public static final String VERSION = "0.0.1a";

    // Initialization time
    public static long startedAt = System.currentTimeMillis();

    /* Default global fields */
    public static String OKSE_SYSTEM_NAME = "OKSE System";
    public static boolean BROADCAST_SYSTEM_MESSAGES_TO_SUBSCRIBERS = false;
    public static boolean CACHE_MESSAGES = true;
    public static long DEFAULT_SUBSCRIPTION_TERMINATION_TIME = 15552000000L; // Half a year
    public static long DEFAULT_PUBLISHER_TERMINATION_TIME = 15552000000L; // Half a year

    private static Logger log;
    public static CoreService cs;
    public static Server webserver;

    /**
     * Main method for the OKSE Message Broker
     * Used to initate the complete application (CoreService and WebServer)
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        PropertyConfigurator.configure("config/log4j.properties");
        // Turn off WS-Nu debug output
        Log.setEnableDebug(false);
        log = Logger.getLogger(Application.class.getName());

        File dbFile = new File("okse.db");

        if (!dbFile.exists()) {
            DB.initDB();
            log.info("okse.db initiated");
        } else {
            log.info("okse.db exists");
        }

        // Initialize main system components
        webserver = new Server();
        cs = CoreService.getInstance();

        /* REGISTER CORE SERVICES HERE */
        cs.registerService(TopicService.getInstance());
        cs.registerService(MessageService.getInstance());
        cs.registerService(SubscriptionService.getInstance());

        /* REGISTER PROTOCOL SERVERS HERE */
        cs.addProtocolServer(WSNotificationServer.getInstance());
        cs.addProtocolServer(DummyProtocolServer.getInstance());    // Example ProtocolServer
        cs.addProtocolServer(AMQProtocolServer.getInstance());

        // Start the admin console
        webserver.run();

        // Start the CoreService
        log.info("Starting OKSE " + VERSION);
        cs.boot();
    }

    /**
     * Returns a Duration instance of the time the Application has been running
     * @return The amount of time the application has been running
     */
    public static Duration getRunningTime() {
        return Duration.ofMillis(System.currentTimeMillis() - startedAt);
    }

    /**
     * Resets the time at which the system was initialized. This method
     * can be used during a system restart from
     */
    public static void resetStartTime() {
        startedAt = System.currentTimeMillis();
    }
}