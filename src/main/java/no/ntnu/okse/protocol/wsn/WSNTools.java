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

package no.ntnu.okse.protocol.wsn;

import no.ntnu.okse.core.messaging.Message;
import org.apache.log4j.Logger;
import org.ntnunotif.wsnu.base.net.NuNamespaceContextResolver;
import org.ntnunotif.wsnu.base.net.XMLParser;
import org.ntnunotif.wsnu.base.topics.ConcreteEvaluator;
import org.ntnunotif.wsnu.base.topics.FullEvaluator;
import org.ntnunotif.wsnu.base.util.InternalMessage;
import org.ntnunotif.wsnu.services.general.ServiceUtilities;
import org.ntnunotif.wsnu.services.general.WsnUtilities;
import org.oasis_open.docs.wsn.b_2.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlsoap.schemas.soap.envelope.Envelope;

import javax.validation.constraints.NotNull;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by Aleksander Skraastad (myth) on 4/21/15.
 * <p>
 * okse is licenced under the MIT licence.
 */
public class WSNTools {

    // Intialize the logger
    private static Logger log = Logger.getLogger(WSNTools.class.getName());

    // Initialize the WSN XML Object factories
    public static org.oasis_open.docs.wsn.b_2.ObjectFactory b2_factory = new org.oasis_open.docs.wsn.b_2.ObjectFactory();
    public static org.oasis_open.docs.wsn.t_1.ObjectFactory t1_factory = new org.oasis_open.docs.wsn.t_1.ObjectFactory();

    // Namespace references
    public static final String _ConcreteTopicExpression = "http://docs.oasis-open.org/wsn/t-1/TopicExpression/Concrete";
    public static final String _SimpleTopicExpression = "http://docs.oasis-open.org/wsn/t-1/TopicExpression/Simple";
    public static final String _FullTopicExpression = "http://docs.oasis-open.org/wsn/t-1/TopicExpression/Full";
    public static final String _XpathTopicExpression = "http://www.w3.org/TR/1999/REC-xpath-19991116";

    /**
     * Generate a valid XML SOAP envelope containing a WS-Notification Notify
     * @param topic The full raw topic path.
     * @param dialect The namespace URI of the dialect used
     * @param messageContent The full raw content of the message.
     * @return A complete WS-Notification Notify XML structure as a string
     */
    public static String generateRawSoapEnvelopedNotifyString(String topic, String dialect, @NotNull String messageContent) {

        // Set topic to an empty string if null, this will cause notify to be sent to all topics
        if (topic == null) topic = "";
        // If dialect is null, check if it contains node path slash, which is not allowed in simpletopic
        if (dialect == null) {
            if (topic.contains("/")) {
                dialect = _ConcreteTopicExpression;
            } else {
                dialect = _SimpleTopicExpression;
            }
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<s:Envelope xmlns:ns2=\"http://www.w3.org/2001/12/soap-envelope\"\n" +
                "            xmlns:ns3=\"http://docs.oasis-open.org/wsrf/bf-2\"\n" +
                "            xmlns:wsa=\"http://www.w3.org/2005/08/addressing\"\n" +
                "            xmlns:wsnt=\"http://docs.oasis-open.org/wsn/b-2\"\n" +
                "            xmlns:ns6=\"http://docs.oasis-open.org/wsn/t-1\"\n" +
                "            xmlns:ns7=\"http://docs.oasis-open.org/wsn/br-2\"\n" +
                "            xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                "            xmlns:ns9=\"http://docs.oasis-open.org/wsrf/r-2\">\n" +
                "<s:Header>\n" +
                "<wsa:Action>http://docs.oasis-open.org/wsn/bw-2/NotificationConsumer/Notify</wsa:Action>\n" +
                "</s:Header>\n" +
                "<s:Body>\n" +
                "<wsnt:Notify>\n" +
                "<wsnt:NotificationMessage>\n" +
                "    <wsnt:Topic Dialect=\"" + dialect + "\">" + topic + "</wsnt:Topic>\n" +
                "<wsnt:Message>" + messageContent + "</wsnt:Message>\n" +
                "        </wsnt:NotificationMessage>\n" +
                "        </wsnt:Notify>\n" +
                "        </s:Body>\n" +
                "        </s:Envelope>";
    }

    /**
     * Generate a valid XML SOAP envelope containing a WS-Notification Notify
     * @param m An OKSE Message object containing topic, dialect and message
     * @return A complete WS-Notification Notify XML structure as a string
     */
    public static String generateRawSoapEnvelopedNotifyString(Message m) {
        return generateRawSoapEnvelopedNotifyString(
                m.getTopic(),
                m.getAttribute(WSNSubscriptionManager.WSN_DIALECT_TOKEN),
                m.getMessage());
    }

    /**
     * Extract the raw XML starting from a specific node, omitting XML declaration
     * @param node The XML Node to start discovery
     * @return A raw XML string representing the XML structure from the specified node
     */
    public static String extractRawXmlContentFromDomNode(Node node) {
        try {
            // Create transformer
            TransformerFactory transFactory = TransformerFactory.newInstance();
            Transformer transformer = transFactory.newTransformer();
            // Init a stringbuffer
            StringWriter buffer = new StringWriter();
            // We dont want the xml declaration
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            // Transform the node from source and beyond
            transformer.transform(new DOMSource(node), new StreamResult(buffer));
            // Convert to string
            String str = buffer.toString();
            // Return results
            return str;
        } catch (TransformerConfigurationException e) {
            log.error(e.getMessage());
            e.printStackTrace();
        } catch (TransformerException e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Takes in a raw XML string, and uses the WS-Nu XMLParser to unmarshal and link the XML nodes
     * @param rawXmlString The raw XML String to be parsed
     * @return A WS-Nu InternalMessage instance with the message attribute containing the parsed XML Structure
     */
    public static InternalMessage parseRawXmlString(String rawXmlString) {
        InputStream inputStream = new ByteArrayInputStream(rawXmlString.getBytes());
        try {
            InternalMessage returnMessage = XMLParser.parse(inputStream);
            return returnMessage;
        } catch (JAXBException e) {
            log.error("Failed to parse raw xml string");
        }
        return new InternalMessage(InternalMessage.STATUS_FAULT | InternalMessage.STATUS_FAULT_INVALID_PAYLOAD, null);
    }

    /**
     * Create a WS-Notification Notify wrapper from OKSE Message object
     * @param m The OKSE Message object to transform
     * @return A WS-Notification Notify wrapper containing topic, dialect and message
     */
    public static Notify createNotify(Message m) {
        String rawXml = generateRawSoapEnvelopedNotifyString(m);
        InternalMessage result = parseRawXmlString(rawXml);
        if ((result.statusCode & InternalMessage.STATUS_FAULT) > 0) {
            log.error("There was an error during parsing of raw xml string");
            return null;
        }
        // Extract message object as a JAXB element
        JAXBElement msg = (JAXBElement) result.getMessage();
        // Cast it to a SOAP envelope
        Envelope env = (Envelope) msg.getValue();
        // Extract the Notify wrapper
        Notify notify = (Notify) env.getBody().getAny().get(0);

        return notify;
    }

    /**
     * Extract the Message element from a WS-Notification Notify wrapper
     * This method does not support multiple notificationmessages bundled in a single Notify
     *
     * @param notify The Notify object to extract message content from
     * @return The Message object contained within the notificationmessage
     */
    public static Object extractMessageContentFromNotify(Notify notify) {
        return notify.getNotificationMessage().get(0).getMessage().getAny();
    }

    /**
     * Injects an XML sub-tree into the message content of a notify
     * @param object The Xml root subnode to be injected
     * @param notify The Notify object to be updated
     */
    public static void injectMessageContentIntoNotify(Object object, Notify notify) {
        notify.getNotificationMessage().get(0).getMessage().setAny(object);
    }

    /**
     * Helper method that removes namespace prefixes from a topic expression
     * @param topicExpression The raw topic expression as a string
     * @return A rebuilt topic node set string without namespace prefixes
     */
    public static String removeNameSpacePrefixesFromTopicExpression(String topicExpression) {
        // If we do not have any prefix delimiter, return
        if (!topicExpression.contains(":")) return topicExpression;
        // Create the holder list for our results
        ArrayList<String> filteredNodeSet = new ArrayList<>();
        // Split to an array of nodes
        String[] nodes = topicExpression.split("/");
        // Iterate through the nodes
        for (String node : nodes) {
            // If the current node contains a prefix delimiter
            if (node.contains(":")) {
                // Split the node
                String[] filtered = node.split(":");
                // Maybe superflous check for duplicate ocurrances of :
                if (filtered.length == 2) filteredNodeSet.add(filtered[1]);
                else {
                    // If we had more than one ocurrance of : remove the first and keep remaining
                    StringBuilder builder = new StringBuilder();
                    for (int i = 1; i < filtered.length; i++) builder.append(filtered[i]);
                    filteredNodeSet.add(builder.toString());
                }
            } else {
                // If no namespace prefix was defined, just add to node set
                filteredNodeSet.add(node);
            }
        }
        // Return the node set after reinserting path delimiter
        return String.join("/", filteredNodeSet);
    }

    /**
     * Helper method that takes in raw string content,
     *
     * @return a notify with its context
     */
    public static NotifyWithContext buildNotifyWithContext(String content, String topic, String prefix, String namespace) {

        // Create a contextResolver, and fill it with the namespace bindings used in the notify
        NuNamespaceContextResolver contextResolver = new NuNamespaceContextResolver();
        contextResolver.openScope();
        if (prefix != null && namespace != null) contextResolver.putNamespaceBinding(prefix, namespace);

        // Build the notify
        ObjectFactory factory = new ObjectFactory();
        Notify notify = factory.createNotify();

        // Create message and holder
        NotificationMessageHolderType.Message message = factory.createNotificationMessageHolderTypeMessage();
        NotificationMessageHolderType messageHolderType = factory.createNotificationMessageHolderType();

        Element e = buildGenericContentElement(content);
        message.setAny(e);

        // Set holders message
        messageHolderType.setMessage(message);

        // Build topic expression
        String expression = (prefix != null && namespace != null) ? prefix + ":" + topic : topic;
        // Build topic
        TopicExpressionType topicExpressionType = factory.createTopicExpressionType();
        topicExpressionType.setDialect(ConcreteEvaluator.dialectURI);
        topicExpressionType.getContent().add(expression);

        messageHolderType.setTopic(topicExpressionType);

        // remember to bind the necessary objects to the context
        contextResolver.registerObjectWithCurrentNamespaceScope(message);
        contextResolver.registerObjectWithCurrentNamespaceScope(topicExpressionType);

        // Add message to the notify
        notify.getNotificationMessage().add(messageHolderType);


        // ready for return
        contextResolver.closeScope();
        NotifyWithContext notifyWithContext = new NotifyWithContext();
        notifyWithContext.notify = notify;
        notifyWithContext.nuNamespaceContextResolver = contextResolver;

        return notifyWithContext;
    }

    public static Element buildGenericContentElement(String content) {

        // create message content
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            Document document = documentBuilderFactory.newDocumentBuilder().newDocument();
            Element element = document.createElement("Content");
            element.setTextContent(content);
            return element;
        } catch (ParserConfigurationException e) {
            log.error("Invalid parser configuration, unknown reason: " + e.getMessage());
        }

        return null;
    }

    /**
     * Helper method that extracts a subscription reference from raw subscriberequest response
     * @param subResponse The internalMessage containing the raw XML SubscribeResponse
     * @return A string with the complete URL + endpoint and params needed
     */
    public static String extractSubscriptionReferenceFromRawXmlResponse(InternalMessage subResponse) {
        try {InternalMessage parsed = parseRawXmlString(subResponse.getMessage().toString());
            JAXBElement jaxb = (JAXBElement) parsed.getMessage();
            Envelope env = (Envelope) jaxb.getValue();
            SubscribeResponse sr = (SubscribeResponse) env.getBody().getAny().get(0);
            return ServiceUtilities.getAddress(sr.getSubscriptionReference());

        } catch (ClassCastException e) {
            log.error("Failed to cast: " + e.getMessage());
        }
        return null;
    }

    /**
     * A wrapper class to hold a notify with its context.
     */
    public static class NotifyWithContext {
        public Notify notify;
        public NuNamespaceContextResolver nuNamespaceContextResolver;
    }
}
