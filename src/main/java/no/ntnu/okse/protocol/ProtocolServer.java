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

package no.ntnu.okse.protocol;

import no.ntnu.okse.core.messaging.Message;

/**
 * Created by Aleksander Skraastad (myth) on 3/13/15.
 * <p>
 * okse is licenced under the MIT licence.
 */
public interface ProtocolServer {

    /**
     * This interface method must return the total amount of requests the protocol server has handled.
     * @return An integer representing the total amount of requests handled.
     */
    public int getTotalRequests();

    /**
     * This interface method must return the total amount of messages that has been processed by the
     * protocol server.
     * @return An integer representing the total amount of processed messages.
     */
    public int getTotalMessages();

    /**
     * This interface method must return the total amount of bad requests recieved by the protocol server.
     * @return An integer representing the total amount of recieved malformed or bad requests
     */
    public int getTotalBadRequests();

    /**
     * This interface method must return the total amount of errors generated by the protocol server.
     * @return An integer representing the total amount of errors in the protocol server.
     */
    public int getTotalErrors();

    /**
     * This interface method must implement a complete initialization and startup process of a protocol server.
     * As it is used in the Core Service to fire up all registered protocol servers upon application start.
     */
    public void boot();

    /**
     * This interface method should contain the main run loop initialization
     */
    public void run();

    /**
     * This interface method must implement a complete shutdown procedure of the protocol server.
     */
    public void stopServer();

    /**
     * This interface method must return a string with the name of the protocol for which the protocol server
     * is responsible for handling.
     * @return A string representing the name of the protocol in question.
     */
    public String getProtocolServerType();

    /**
     * This interface method must take in an instance of Message, which contains the appropriate references
     * and flags needed to distribute the message to consumers. Implementation specific details can vary from
     * protocol to protocol, but the end result of a method call to sendMessage is that the message is delivered,
     * or an error is logged.
     *
     * @param message An instance of Message containing the required data to distribute a message.
     */
    public void sendMessage(Message message);
}
