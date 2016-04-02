package no.ntnu.okse.protocol.mqtt;

import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptUnsubscribeMessage;
import io.moquette.interception.messages.*;
import io.moquette.server.config.MemoryConfig;
import io.netty.channel.Channel;
import no.ntnu.okse.core.messaging.Message;
import no.ntnu.okse.core.messaging.MessageService;
import no.ntnu.okse.core.subscription.Publisher;
import no.ntnu.okse.core.subscription.Subscriber;
import no.ntnu.okse.core.topic.TopicService;
import no.ntnu.okse.protocol.mqtt.MQTTSubscriptionManager;
import org.apache.log4j.Logger;

import io.moquette.BrokerConstants;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.InterceptHandler;
import io.moquette.server.Server;
import io.moquette.server.config.IConfig;
import io.moquette.parser.proto.messages.AbstractMessage;
import io.moquette.parser.proto.messages.PublishMessage;
import org.oasis_open.docs.wsn.bw_2.SubscriptionManager;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class MQTTServer extends Server {

	private static Logger log = Logger.getLogger(Server.class);
	private MQTTProtocolServer ps;
	private final IConfig config;
	private List<InterceptHandler> interceptHandlers;
	private MQTTSubscriptionManager subscriptionManager;

	protected class MQTTListener extends AbstractInterceptHandler {
		@Override
		public void onPublish(InterceptPublishMessage message) {
			HandlePublish(message);
		}

		@Override
		public void onSubscribe(InterceptSubscribeMessage message) {
			HandleSubscribe(message);
		}

		@Override
		public void onUnsubscribe(InterceptUnsubscribeMessage message) {
			HandleUnsubscribe(message);
		}

		@Override
		public void onDisconnect(InterceptDisconnectMessage message) {
			HandleDisconnect(message);
		}
	}

	public MQTTServer(MQTTProtocolServer ps, String host, int port) {
		this.ps = ps;
		interceptHandlers = new ArrayList<>();
		interceptHandlers.add(new MQTTListener());
		config = new MemoryConfig(getConfig(host, port));
	}

	public void start() {
		try {
			startServer(config, interceptHandlers);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	void HandlePublish(InterceptPublishMessage message){
		log.info("MQTT message received on topic: " + message.getTopicName() + " from ID: " + message.getClientID());

		Channel channel = getChannelByClientId(message.getClientID());
		if(channel == null)
			return;
		int port = getPort(channel);
		String host = getHost(channel);

		Publisher pub = new Publisher( message.getTopicName(), host, port, ps.getProtocolServerType());

		//Adds the publisher to the subscriptionManager, if it is already added the subscription manager will not add it
		subscriptionManager.addPublisher(pub, message.getClientID());

		String topic = message.getTopicName();
		String payload = getPayload(message);

		sendMessageToOKSE(new Message( payload, topic, pub, ps.getProtocolServerType()));
		ps.incrementTotalMessagesReceived();
	}

	void HandleSubscribe(InterceptSubscribeMessage message) {
		log.info("Client subscribed to: "  + message.getTopicFilter() + "   ID: " + message.getClientID());

		TopicService.getInstance().addTopic( message.getTopicFilter() );
		Channel channel = getChannelByClientId(message.getClientID());
		if(channel == null)
			return;

		int port = getPort(channel);
		String host = getHost(channel);

		Subscriber sub = new Subscriber( host, port, message.getTopicFilter(), ps.getProtocolServerType());
		subscriptionManager.addSubscriber(sub, message.getClientID());
	}

	void HandleUnsubscribe(InterceptUnsubscribeMessage message) {
		log.info("Client unsubscribed from: "  + message.getTopicFilter() + "   ID: " + message.getClientID());

		String clientID = message.getClientID();
		subscriptionManager.removeSubscriber(clientID);
	}

	void HandleDisconnect(InterceptDisconnectMessage message) {
		log.info("Client disconnected ID: " + message.getClientID());

		String clientID = message.getClientID();

		subscriptionManager.removeSubscriber(clientID);
		subscriptionManager.removePublisher(clientID);
	}

	public void sendMessageToOKSE(Message msg){
		MessageService.getInstance().distributeMessage(msg);
	}

	private String getPayload(InterceptPublishMessage message) {
		ByteBuffer buffer = message.getPayload();
		String payload = new String(buffer.array(), buffer.position(), buffer.limit());
		return payload;
	}

	private int getPort(Channel channel){
		return ((InetSocketAddress)channel.remoteAddress()).getPort();
	}
	private String getHost(Channel channel){
		return ((InetSocketAddress)channel.remoteAddress()).getHostString();
	}

	public void setSubscriptionManager(MQTTSubscriptionManager subscriptionManager){
		this.subscriptionManager = subscriptionManager;
	}

	private Properties getConfig(String host, int port) {
		Properties properties = new Properties();
		properties.setProperty(BrokerConstants.HOST_PROPERTY_NAME, host);
		properties.setProperty(BrokerConstants.PORT_PROPERTY_NAME, "" + port);
		// Set random port for websockets instead of 8080
		properties.setProperty(BrokerConstants.WEB_SOCKET_PORT_PROPERTY_NAME, "25342");
		// Disable automatic publishing (handled by the broker instead)
		properties.setProperty(BrokerConstants.PUBLISH_TO_CONSUMERS, "false");
		return properties;
	}

	/**
	 * Sends the message to any subscriber that is subscribed to the topic that the message was sent to
	 * @param message is the message that is sent from OKSE core
	 * */
	public void sendMessage(@NotNull Message message) {
		PublishMessage msg = createMQTTMessage(message);
		internalPublish(msg);
		ps.incrementTotalMessagesSent();
	}

	/**
	 * Creates an MQTT message from the given arguments
	 *
	 * @param message The OKSE message to use when creating MQTT message
	 * */
	protected PublishMessage createMQTTMessage(@NotNull Message message){
		PublishMessage msg = new PublishMessage();
		ByteBuffer payload = ByteBuffer.wrap(message.getMessage().getBytes());

		String topicName = message.getTopic();

		msg.setPayload(payload);
		msg.setTopicName(topicName);
		msg.setQos(AbstractMessage.QOSType.LEAST_ONE);
		return msg;
	}
}
