package no.ntnu.okse.protocol.stomp;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import asia.stampy.server.netty.ServerNettyMessageGateway;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;

/**
 * The Class ServerNettyMessageGateway.
 */
@Resource
@StampyLibrary(libraryName = "stampy-NETTY-client-server-RI")
public class STOMPGateway extends ServerNettyMessageGateway {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private NioServerSocketChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());

    private Channel server;
    private String host;

    /**
     * Inits the server
     * @return ServerBootstrap instance
     * @see ServerBootstrap
     */
    private ServerBootstrap init() {
        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        initializeChannel(bootstrap);

        return bootstrap;
    }

    public Channel getServer(){
        return server;
    }

    /**
     * Connects the server, it binds the server
     * @throws Exception
     */
    @Override
    public void connect() throws Exception {
        if (server == null) {
            ServerBootstrap bootstrap = init();
            server = bootstrap.bind(new InetSocketAddress(getHost(), getPort()));
            log.info("Bound to {}", getHost() + ":" + getPort());
            log.info(String.valueOf(server.getLocalAddress()), String.valueOf(server.getRemoteAddress()));
        } else if (server.isBound()) {
            log.warn("Already bound");
        } else {
            log.error("Acceptor in unrecognized state: isBound {}, isConnected {}, ", server.isBound(), server.isConnected());
        }
    }

    /**
     * Sets the host for the gateway
     * @param host the host
     */
    public void setHost(String host){
        this.host = host;
    }

    /**
     * Gets the host
     * @return the host for the gateway
     */
    public String getHost(){
        return this.host;
    }

    /**
     * shuts down the server
     * If the server is null it logs an exception
     * @throws Exception
     * @see asia.stampy.common.gateway.AbstractStampyMessageGateway#shutdown()
     */
    @Override
    public void shutdown() throws Exception {
        if (server == null){
            log.error("Server was null, cannot shutdown!");
            return;
        }
        ChannelFuture cf = server.close();
        cf.awaitUninterruptibly();
        server = null;
        log.info("Server has been shut down");
    }

}
