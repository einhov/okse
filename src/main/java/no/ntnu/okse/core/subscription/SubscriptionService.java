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

package no.ntnu.okse.core.subscription;

import no.ntnu.okse.core.AbstractCoreService;
import no.ntnu.okse.core.event.PublisherChangeEvent;
import no.ntnu.okse.core.event.SubscriptionChangeEvent;
import no.ntnu.okse.core.event.listeners.PublisherChangeListener;
import no.ntnu.okse.core.event.listeners.SubscriptionChangeListener;
import no.ntnu.okse.core.topic.Topic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Aleksander Skraastad (myth) on 4/5/15.
 * <p>
 * okse is licenced under the MIT licence.
 */
public class SubscriptionService extends AbstractCoreService {

    private static SubscriptionService _singleton;
    private static Thread _serviceThread;
    private static boolean _invoked = false;

    private LinkedBlockingQueue<SubscriptionTask> queue;
    private HashSet<SubscriptionChangeListener> _subscriptionListeners;
    private HashSet<PublisherChangeListener> _registrationListeners;
    private HashSet<Subscriber> _subscribers;
    private HashSet<Publisher> _publishers;

    /**
     * Private constructor that passes classname to superclass log field and calls initialization method
     */
    private SubscriptionService() {
        super(SubscriptionService.class.getName());
        init();
    }

    /**
     * The main instanciation method of SubscriptionService adhering to the singleton pattern
     * @return
     */
    public static SubscriptionService getInstance() {
        if (!_invoked) _singleton = new SubscriptionService();
        return _singleton;
    }

    /**
     * Initializing method
     */
    @Override
    protected void init() {
        _invoked = true;
        log.info("Initializing SubscriptionService...");
        queue = new LinkedBlockingQueue<>();
        _subscribers = new HashSet<>();
        _publishers = new HashSet<>();
        _registrationListeners = new HashSet<>();
        _subscriptionListeners = new HashSet<>();
    }

    /**
     * Startup method that sets up the service
     */
    @Override
    public void boot() {
        if (!_running) {
            log.info("Booting SubscriptionService...");
            _serviceThread = new Thread(() -> {
                _running = true;
                _singleton.run();
            });
            _serviceThread.setName("SubscriptionService");
            _serviceThread.start();
        }
    }

    /**
     * Main run method that will be called when the subclass' serverThread is started
     */
    @Override
    public void run() {
        log.info("SubscriptionService booted successfully");
        while (_running) {
            try {
                SubscriptionTask task = queue.take();
                log.debug(task.getType() + " job recieved, executing task...");
                // Perform the task
                task.run();
            } catch (InterruptedException e) {
                log.warn("Interrupt caught, consider sending a No-Op Task to the queue to awaken the thread.");
            }
        }
    }

    /**
     * Graceful shutdown method
     */
    @Override
    public void stop() {
        _running = false;
        Runnable job = () -> log.info("Stopping SubscriptionService...");
        try {
            queue.put(new SubscriptionTask(SubscriptionTask.Type.SHUTDOWN, job));
        } catch (InterruptedException e) {
            log.error("Interrupted while trying to inject shutdown event to queue");
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    /* Begin Service-Local methods */

    /**
     * This helper method injects a task into the task queue and handles interrupt exceptions
     * @param task The SubscriptionTask to be executed
     */
    private void insertTask(SubscriptionTask task) {
        try {
            // Inject the task into the task queue
            this.queue.put(task);
        } catch (InterruptedException e) {
            log.error("Interrupted while injecting task into queue");
        }
    }

    /**
     * Service-local private method to add a Subscriber to the list of subscribers
     * @param s : A Subscriber instance with the proper fields set
     */
    private void addSubscriberLocal(Subscriber s) {
        if (!_subscribers.contains(s)) {
            // Add the subscriber
            _subscribers.add(s);
            log.info("Added new subscriber: " + s);
            // Fire the subscribe event
            fireSubcriptionChangeEvent(s, SubscriptionChangeEvent.Type.SUBSCRIBE);
        } else {
            log.warn("Attempt to add a subscriber that already exists!");
        }
    }

    /**
     * Service-local private method to remove a subscriber from the list of subscribers
     * @param s : A Subscriber instance that exists in the subscribers set
     */
    private void removeSubscriberLocal(Subscriber s) {
        if (_subscribers.contains(s)) {
            // Remove the subscriber
            _subscribers.remove(s);
            log.info("Removed subscriber: " + s);
            // Fire the unsubscribe event
            fireSubcriptionChangeEvent(s, SubscriptionChangeEvent.Type.UNSUBSCRIBE);
        } else {
            log.warn("Attempt to remove a subscriber that did not exist!");
        }
    }

    /**
     * Service-local private method to renew the subscription for a particular subscriber
     * @param s : The subscriber that is to be changed
     * @param timeout : The new timeout time represented as seconds since unix epoch
     */
    private void renewSubscriberLocal(Subscriber s, long timeout) {
        if (_subscribers.contains(s)) {
            // Update the timeout field
            s.setTimeout(timeout);
            log.info("Renewed subscriber: " + s);
            // Fire the renew event
            fireSubcriptionChangeEvent(s, SubscriptionChangeEvent.Type.RENEW);
        } else {
            log.warn("Attempt to modify a subscriber that does not exist in the service!");
        }
    }

    /**
     * Service-local private method to pause the subscription for a particular subscriber
     * @param s : The subscriber that is to be paused
     */
    private void pauseSubscriberLocal(Subscriber s) {
        if (_subscribers.contains(s)) {
            // Set the Paused attribute to true
            s.setAttribute("paused", "true");
            log.info("Paused subscriber: " + s);
            // Fire the pause event
            fireSubcriptionChangeEvent(s, SubscriptionChangeEvent.Type.PAUSE);
        } else {
            log.warn("Attempt to modify a subscriber that does not exist in the service!");
        }
    }

    /**
     * Service-local private method to register a publisher to the publisher set
     * @param p : The publisher object that is to be registered
     */
    private void addPublisherLocal(Publisher p) {
        if (!_publishers.contains(p)) {
            // Add the publisher
            _publishers.add(p);
            log.info("Added publisher: " + p);
            // Fire the register event
            firePublisherChangeEvent(p, PublisherChangeEvent.Type.REGISTER);
        } else {
            log.warn("Attempt to add a publisher that already exists!");
        }
    }

    /**
     * Service-local private method to unregister a publisher from the publisher set
     * @param p : A publisher object that exists in the publishers set
     */
    private void removePublisherLocal(Publisher p) {
        if (_publishers.contains(p)) {
            // Remove the publisher
            _publishers.remove(p);
            log.info("Removed publisher: " + p);
            // Fire the remove event
            firePublisherChangeEvent(p, PublisherChangeEvent.Type.UNREGISTER);
        }
    }
    /* End Service-Local methods */

    /* ------------------------------------------------------------------------------------------ */

    /* Begin subscriber public API */

    /**
     * Public method to add a Subscriber
     * @param s The subscriber to be added
     */
    public void addSubscriber(Subscriber s) {
        if (!_subscribers.contains(s)) {
            // Create the job
            Runnable job = () -> addSubscriberLocal(s);
            // Initialize the SubscriptionTask wrapper
            SubscriptionTask task = new SubscriptionTask(SubscriptionTask.Type.NEW_SUBSCRIBER, job);
            // Inject the task
            insertTask(task);
        } else {
            log.warn("Attempt to add a subscriber that already exists!");
        }
    }

    /**
     * Public method to remove a Subscriber
     * @param s A subscriber that exists in the subscribers set
     */
    public void removeSubscriber(Subscriber s) {
        if (_subscribers.contains(s)) {
            // Create the job
            Runnable job = () -> removeSubscriberLocal(s);
            // Initialize the SubscriptionTask wrapper
            SubscriptionTask task = new SubscriptionTask(SubscriptionTask.Type.DELETE_SUBSCRIBER, job);
            // Inject the task
            insertTask(task);
        } else {
            log.warn("Attempt to remove a subscriber that did not exist!");
        }
    }

    /**
     * Public method to renew a subscription
     * @param s The subscriber object that is to be renewed
     * @param timeout The new timeout of the subscription represented as seconds since unix epoch
     */
    public void renewSubscriber(Subscriber s, Long timeout) {
        if (!_subscribers.contains(s)) {
            // Create the job
            Runnable job = () -> renewSubscriberLocal(s, timeout);
            // Initialize the SubscriptionTask wrapper
            SubscriptionTask task = new SubscriptionTask(SubscriptionTask.Type.UPDATE_SUBSCRIBER, job);
            // Inject the task
            insertTask(task);
        } else {
            log.warn("Attempt to modify a subscriber that did not exist in the service!");
        }
    }

    /**
     * Public method to pause a subscroption
     * @param s The subciber object that is to be paused
     */
    public void pauseSubscriber(Subscriber s) {
        if (_subscribers.contains(s)) {
            // Create the job
            Runnable job = () -> pauseSubscriberLocal(s);
            // Initialize the SubscriptionTask wrapper
            SubscriptionTask task = new SubscriptionTask(SubscriptionTask.Type.UPDATE_SUBSCRIBER, job);
            // Inject the task
            insertTask(task);
        } else {
            log.warn("Attempt to modify a subscriber that did not exist in the service!");
        }
    }
    /* End subscriber public API */

    /* ------------------------------------------------------------------------------------------ */

    /* Begin publisher public API */

    /**
     * Public method to register a publisher
     * @param p The publisher object that is to be registered
     */
    public void addPublisher(Publisher p) {
        if (!_publishers.contains(p)) {
            // Create the job
            Runnable job = () -> addPublisherLocal(p);
            // Initialize the SubscriptionTask wrapper
            SubscriptionTask task = new SubscriptionTask(SubscriptionTask.Type.NEW_PUBLISHER, job);
            // Inject the task
            insertTask(task);
        } else {
            log.warn("Attempt to add a publisher that already exists!");
        }
    }

    /**
     * Public method to unregister a publisher
     * @param p A publisher object that exists in the publishers set
     */
    public void removePublisher(Publisher p) {
        if (_publishers.contains(p)) {
            // Create the job
            Runnable job = () -> removePublisherLocal(p);
            // Initialize the SubscriptionTask wrapper
            SubscriptionTask task = new SubscriptionTask(SubscriptionTask.Type.DELETE_PUBLISHER, job);
            // Inject the task
            insertTask(task);
        } else {
            log.warn("Attempt to add a publisher that did not exist in the service!");
        }
    }
    /* End publisher public API */

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Attempt to locate a subscriber by remote address, port and topic object.
     * @param address : The remote IP or hostname of the client
     * @param port    : The remote port of the client
     * @param topic   : The topic object you want the subscriber object for
     * @return The Subscriber, if found, null otherwise.
     */
    public Subscriber getSubscriber(String address, Integer port, Topic topic) {
        // TODO: FIX THIS AS EVERY SUBSCRIBER MUST HAVE A UNIQUE ID
        // TODO: Also maek into stream and use filter lambdas
        // TODO: Will this trigger concurrent modification exception if new subs are added during iteration?
        for (Subscriber s: _subscribers) {
            if (s.getHost().equals(address) &&
                    s.getPort().equals(port) &&
                    s.getTopic().equals(topic.getFullTopicString())) {
                return s;
            }
        }
        return null;
    }

    /**
     * Retrieve a HashSet of all subscribers that have subscribed to a specific topic
     * @param topic A raw topic string of the topic to select subscribers from
     * @return A HashSet of Subscriber objects that have subscribed to the specified topic
     */
    public HashSet<Subscriber> getAllSubscribersForTopic(String topic) {
        // Initialize a collector
        HashSet<Subscriber> results = new HashSet<>();

        // TODO: Will this trigger concurrent modification exception if new subs are added during iteration?

        // Iterate over all subscribers
        getAllSubscribers().stream()
                    // Only pass on those who match topic argument
                    .filter(s -> s.getTopic().equals(topic))
                    // Collect in the results set
                    .forEach(s -> results.add(s));

        return results;
    }

    /**
     * Retrive a HashSet of all subscribers on the broker
     * @return A HashSet of Subscriber objects that have subscribed on the broker
     */
    public HashSet<Subscriber> getAllSubscribers() {
        return (HashSet<Subscriber>) _subscribers.clone();
    }

    public HashSet<Publisher> getAllPublishers() {
        return (HashSet<Publisher>) _publishers.clone();
    }

    /* ------------------------------------------------------------------------------------------ */

    /* Begin listener support */

    /**
     * SubscriptionChange event listener support
     * @param s : An object implementing the SubscriptionChangeListener interface
     */
    public synchronized void addSubscriptionChangeListener(SubscriptionChangeListener s) {
        _subscriptionListeners.add(s);
    }

    /**
     * SubscriptionChange event listener support
     * @param s : An object implementing the SubscriptionChangeListener interface
     */
    public synchronized void removeSubscriptionChangeListener(SubscriptionChangeListener s) {
        if (_subscriptionListeners.contains(s)) _subscriptionListeners.remove(s);
    }

    /**
     * Private helper method fo fire the subscriptionChange method on all listners.
     * @param sub   : The particular subscriber object that has changed.
     * @param type  : What type of action is associated with the subscriber object.
     */
    private void fireSubcriptionChangeEvent(Subscriber sub, SubscriptionChangeEvent.Type type) {
        SubscriptionChangeEvent sce = new SubscriptionChangeEvent(type, sub);
        _subscriptionListeners.stream().forEach(l -> l.subscriptionChanged(sce));
    }

    /**
     * PublisherChange event listener support
     * @param r : An object implementing the PublisherChangeListener interface
     */
    public synchronized void addPublisherChangeListener(PublisherChangeListener r) {
        _registrationListeners.add(r);
    }

    /**
     * PublisherChange event listener support
     * @param r : An object implementing the PublisherChangeListener interface
     */
    public synchronized void removePublisherChangeListener(PublisherChangeListener r) {
        if (_registrationListeners.contains(r)) _registrationListeners.remove(r);
    }

    /**
     * Private helper method fo fire the publisherChange method on all listners.
     * @param reg   : The particular publisher object that has changed.
     * @param type  : What type of action is associated with the publisher object.
     */
    private void firePublisherChangeEvent(Publisher reg, PublisherChangeEvent.Type type) {
        PublisherChangeEvent rce = new PublisherChangeEvent(type, reg);
        _registrationListeners.stream().forEach(l -> l.publisherChanged(rce));
    }

    /* End listener support */
}
