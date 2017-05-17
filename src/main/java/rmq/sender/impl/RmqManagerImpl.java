/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rmq.sender.impl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rmq.sender.api.RmqManagerService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;


/**
 * Connects client with server using start API, publish the messages received
 * from onos events and disconnect the client from server using stop API.
 */
public class RmqManagerImpl implements RmqManagerService {

    private static final String E_CREATE_CHAN =
                                  "Error creating the RabbitMQ channel";
    private static final String E_PUBLISH_CHAN =
                                  "Error in publishing to the RabbitMQ channel";
    private static final Logger log = LoggerFactory.getLogger(RmqManagerImpl.class);
    private static final int RECOVERY_INTERVAL = 15000;

    private final BlockingQueue<MessageContext> outQueue;
    private final String exchangeName;
    private final String routingKey;
    private final String queueName;
    private final String url;
    private final String type;

    private ExecutorService executorService;
    private Connection conn;
    private Channel channel;


    public RmqManagerImpl(BlockingQueue<MessageContext> outQueue, String exchangeName,
                          String routingKey, String queueName, String url, String type) {
        this.outQueue = outQueue;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.queueName = queueName;
        this.url = url;
        this.type = type;
    }


    @Override
    public Channel start(InputStream filepath) {
        SSLContext c = null;
        try {
            char[] pass = "changeit".toCharArray();
            KeyStore tks = KeyStore.getInstance("JKS");
            tks.load(filepath, pass);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(tks);

            c = SSLContext.getInstance("TLSv1.2");
            c.init(null, tmf.getTrustManagers(), null);
        } catch (Exception e) {
            log.error(E_CREATE_CHAN, e);
        }
        ConnectionFactory factory = new ConnectionFactory();
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        factory.useSslProtocol(c);
        try {
            factory.setUri(url);
            if (executorService != null) {
                conn = factory.newConnection(executorService);
            } else {
                conn = factory.newConnection();
            }
            channel = conn.createChannel();
            //channel.exchangeDeclare(exchangeName, type, true);

            channel.queueDeclare(queueName, false, false, false, null);
            //channel.queueBind(queueName, exchangeName, routingKey);
        } catch (Exception e) {
            log.error(E_CREATE_CHAN, e);
        }
        log.info("Connection started");
        return channel;
    }


    public void stop() {
        try {
            channel.close();
            conn.close();
        } catch (IOException e) {
            log.error("Error closing the rabbit MQ connection", e);
        } catch (TimeoutException e) {
            log.error("Timeout exception in closing the rabbit MQ connection",
                    e);
        }
    }
    /*public Channel start() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        try {
            factory.setUri(url);
            if (executorService != null) {
                conn = factory.newConnection(executorService);
            } else {
                conn = factory.newConnection();
            }
            channel = conn.createChannel();
            channel.exchangeDeclare(exchangeName, "topic", true);

            channel.queueDeclare(this.queueName, true, false, true, null);
            channel.queueBind(queueName, exchangeName, routingKey);
        } catch (Exception e) {
            log.error(E_CREATE_CHAN, e);
        }
        log.info("Connection started");
        return channel;
    }


    @Override
    public void stop() {
        try {
            channel.close();
            conn.close();
        } catch (IOException e) {
            log.error("Error closing the rabbit MQ connection", e);
        } catch (TimeoutException e) {
            log.error("Timeout exception in closing the rabbit MQ connection",
                    e);
        }
    }*/



}
