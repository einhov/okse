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
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package no.ntnu.okse.protocol.amqp;

import no.ntnu.okse.core.event.SubscriptionChangeEvent;
import no.ntnu.okse.core.event.listeners.SubscriptionChangeListener;
import no.ntnu.okse.core.subscription.Subscriber;
import no.ntnu.okse.core.subscription.SubscriptionService;
import no.ntnu.okse.core.topic.TopicService;
import org.apache.log4j.Logger;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.engine.*;
import org.ntnunotif.wsnu.services.implementations.notificationproducer.AbstractNotificationProducer;

import javax.jws.WebMethod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Most of this code is from the qpid-proton-demo (https://github.com/rhs/qpid-proton-demo) by Rafael Schloming
 * Created by kradalby on 24/04/15.
 */
public class SubscriptionHandler extends BaseHandler implements SubscriptionChangeListener{

    public static class Routes<T extends Link> {

        List<T> routes = new ArrayList<T>();

        void add(T route) {
            routes.add(route);
        }

        void remove(T route) {
            routes.remove(route);
        }

        int size() {
            return routes.size();
        }

        public T choose() {
            if (routes.isEmpty()) { return null; }
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            int idx = rand.nextInt(0, routes.size());
            return routes.get(idx);
        }

        public List<T> getRoutes() {
            return routes;
        }

        public void printRouteTable() {
            routes.forEach((route) -> {
                System.out.println(route.toString());
            });
        }

    }

    private static final Routes<Sender> EMPTY_OUT = new Routes<Sender>();
    private static final Routes<Receiver> EMPTY_IN = new Routes<Receiver>();
    private static Logger log = Logger.getLogger(SubscriptionHandler.class.getName());

    private static HashMap<Sender, Subscriber> localSenderSubscriberMap = new HashMap<>();
    private static HashMap<Subscriber, Sender> localSubscriberSenderMap = new HashMap<>();
    private static HashMap<String, Sender> localRemoteContainerSenderMap = new HashMap<>();
    private static HashMap<Sender, AbstractNotificationProducer.SubscriptionHandle> localSubscriberHandle = new HashMap<>();

    final private Map<String,Routes<Sender>> outgoing = new HashMap<String,Routes<Sender>>();
    final private Map<String,Routes<Receiver>> incoming = new HashMap<String,Routes<Receiver>>();

    public SubscriptionHandler() {}

    private String getAddress(Source source) {
        if (source == null) {
            return null;
        } else {
            return source.getAddress();
        }
    }

    private String getAddress(Target target) {
        if (target == null) {
            return null;
        } else {
            return target.getAddress();
        }
    }

    public String getAddress(Sender sender) {
        String source = getAddress(sender.getSource());
        String target = getAddress(sender.getTarget());
        return source != null ? source : target;
    }

    public String getAddress(Receiver receiver) {
        return getAddress(receiver.getTarget());
    }

    public Routes<Sender> getOutgoing(String address) {
        Routes<Sender> routes = outgoing.get(address);
        if (routes == null) { return EMPTY_OUT; }
        return routes;
    }

    public Routes<Receiver> getIncomming(String address) {
        Routes<Receiver> routes = incoming.get(address);
        if (routes == null) { return EMPTY_IN; }
        return routes;
    }

    private void add(Sender sender) {
        String senderAddress = "Unknown";
        int senderClientPort = 0;
        String protocolServerType = "Unknown";

        Session session = sender.getSession();
        Connection connection = session.getConnection();

        AMQProtocolServer server = AMQProtocolServer.getInstance();
        Driver driver = server.getDriver();
        int clientPort = driver.getPort();


        //The connected client hostname
        String remoteHostName = driver.getInetAddress().getHostName();

        //Get RemoteContainer id for the connection, used to identify the connection
        String remoteContainer = connection.getRemoteContainer();

        //If RemoteContainer id is not null add the subscriber to HashMap with RemoteContainer id as Key
        //Is used to identify which sender has open connections to the broker
        if(remoteContainer != null){
            localRemoteContainerSenderMap.put(remoteContainer, sender);
        }

        if(remoteHostName != null){
            senderAddress = remoteHostName;
        }


        if(clientPort != 0){
            senderClientPort = clientPort;
        }

        if(server.getProtocolServerType() != null){
            protocolServerType = server.getProtocolServerType();
        }

        //Building the Subscriber from a sender object
        Subscriber subscriber = new Subscriber(senderAddress, senderClientPort, getAddress(sender), protocolServerType);
        SubscriptionService.getInstance().addSubscriber(subscriber);



        localSenderSubscriberMap.put(sender, subscriber);
        localSubscriberSenderMap.put(subscriber, sender);
        TopicService.getInstance().addTopic(getAddress(sender));

        String address = getAddress(sender);
        Routes<Sender> routes = outgoing.get(address);
        if (routes == null) {
            log.debug("Route does not exist, adding route for: " + address);
            routes = new Routes<Sender>();
            outgoing.put(address,routes);
        }
        log.debug("Adding sender: " + remoteHostName + " to route: " + address);
        log.debug(outgoing.toString());
        log.debug("This is getAddress: " + getAddress(sender));
        routes.add(sender);
    }

    private void remove(Sender sender) {
        Session session = sender.getSession();
        Connection connection = session.getConnection();
        String remoteHostName = connection.getRemoteHostname();

        Subscriber subscriber = localSenderSubscriberMap.get(sender);
        localSenderSubscriberMap.remove(sender);
        localSubscriberSenderMap.remove(subscriber);

        String address = getAddress(sender);
        Routes<Sender> routes = outgoing.get(address);
        if (routes != null) {
            log.debug("Removing sender: " + remoteHostName + " from route: " + address);
            routes.remove(sender);
            if (routes.size() == 0) {
                outgoing.remove(address);
            }
        }
        log.debug("Detaching: " + remoteHostName);
        sender.abort();
        sender.detach();
        sender.close();
        log.debug(outgoing.toString());
    }
    private void add(Receiver receiver) {
        String address = getAddress(receiver);
        Routes<Receiver> routes = incoming.get(address);
        if (routes == null) {
            log.debug("Route does not exist, adding route for: " + address);
            routes = new Routes<Receiver>();
            incoming.put(address, routes);
        }
        log.debug("Adding receiver: " + receiver.getName() + " to route: " + address);
        log.debug(incoming.toString());
        routes.add(receiver);
    }

    private void remove(Receiver receiver) {
        String address = getAddress(receiver);
        Routes<Receiver> routes = incoming.get(address);
        if (routes != null) {
            log.debug("Removing receiver: " + receiver.getName() + "from route:" + address);
            routes.remove(receiver);
            if (routes.size() == 0) {
                incoming.remove(address);
            }
        }
        log.debug(incoming.toString());
    }

    private void add (Link link) {
        if (link instanceof Sender) {
            add((Sender) link);
        } else {
            add((Receiver) link);
        }
    }

    private void remove(Link link) {
        if (link instanceof Sender) {
            remove((Sender) link);
        } else {
            remove((Receiver) link);
        }
    }

    @Override
    public void onLinkLocalOpen(Event event) {
        log.debug("Local link opened");
        add(event.getLink());
    }

    @Override
    public void onConnectionUnbound(Event event) {
        //log.debug("This is event.getSession(): " + event.getSession());

        //Getting the RemoteContainer id for the event
        Connection eventConnection = event.getConnection();
        String eventRemoteContainer = eventConnection.getRemoteContainer();
        log.debug("This is event RemoteContainer: " + eventRemoteContainer);

        //Setting the RemoteContainer id for the sender to "Unknown" before we get it from the sender object
        String senderRemoteContainer = "Unknown";

        //Get sender from localRemoteContainerSenderMap  with eventRemoteContainer as key
        Sender sender = localRemoteContainerSenderMap.get(eventRemoteContainer);
        if(sender != null){
            Session senderSession = sender.getSession();
            Connection senderConnection = senderSession.getConnection();
            senderRemoteContainer = senderConnection.getRemoteContainer();
        }

        //Check if sender container id is equal to the event container id to see if the client has disconnected
        if (senderRemoteContainer.equals(eventRemoteContainer)){
            SubscriptionService.getInstance().removeSubscriber(localSenderSubscriberMap.get(sender));
        }
    }



    @Override
    @WebMethod(exclude = true)
    public void subscriptionChanged(SubscriptionChangeEvent e) {
        // If it is AMQP subscriber
        if (e.getData().getOriginProtocol().equals(AMQProtocolServer.getInstance().getProtocolServerType())) {
            // If we are dealing with an Unsubscribe
            if (e.getType().equals(SubscriptionChangeEvent.Type.UNSUBSCRIBE)) {
                log.debug("Unsubscribing " + localSubscriberSenderMap.get(e.getData()));
                // Remove the local mappings from AMQP subscriptionKey to OKSE Subscriber object and AMQP subscriptionHandle
                remove(localSubscriberSenderMap.get(e.getData()));
            } else if (e.getType().equals(SubscriptionChangeEvent.Type.SUBSCRIBE)) {
                log.debug("Recieved a SUBSCRIBE event");
                // TODO: Investigate if we really need to do anything here since it will function as a callback
                // TODO: after addSubscriber
            }
        }
    }
}
