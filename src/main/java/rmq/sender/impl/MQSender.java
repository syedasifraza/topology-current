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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rmq.sender.api.RmqConnectionManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Connects client with server using start API, publish the messages received
 * from onos events and disconnect the client from server using stop API.
 */
public class MQSender implements RmqConnectionManager {

    private static final String E_CREATE_CHAN =
                                  "Error creating the RabbitMQ channel";
    private static final String E_PUBLISH_CHAN =
                                  "Error in publishing to the RabbitMQ channel";
    private static final Logger log = LoggerFactory.getLogger(MQSender.class);
    private static final int RECOVERY_INTERVAL = 15000;

    private final BlockingQueue<MessageContext> outQueue;
    private final String exchangeName;
    private final String routingKey;
    private final String queueName;
    private final String url;

    private ExecutorService executorService;
    private Connection conn;
    private Channel channel;


    /**
     * Creates a MQReciever initialized with the specified parameters.
     *
     * @param outQueue     represents message context
     * @param exchangeName represents mq exchange name
     * @param routingKey   represents bound routing key
     * @param queueName    represents mq queue name
     * @param url          represents the mq server url
     */
    public MQSender(BlockingQueue<MessageContext> outQueue, String exchangeName,
            String routingKey, String queueName, String url) {
        this.outQueue = outQueue;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.queueName = queueName;
        this.url = url;
    }


    /**
     * Sets the executor sender.
     *
     * @param executorService the executor sender to use
     */
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void start() {
        SSLContext c = null;
        try {
            char[] pass = "changeit".toCharArray();
            KeyStore tks = KeyStore.getInstance("JKS");
            tks.load(new FileInputStream("/root/test-project/topology-current/" +
                    "/src/main/resources/client/client_cacerts.jks"), pass);

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
            channel.exchangeDeclare(exchangeName, "topic", true);
            /*
             * Setting the following parameters to queue
             * durable    - true
             * exclusive  - false
             * autoDelete - false
             * arguments  - null
             */
            channel.queueDeclare(this.queueName, true, false, true, null);
            channel.queueBind(queueName, exchangeName, routingKey);
        } catch (Exception e) {
            log.error(E_CREATE_CHAN, e);
        }
        log.info("Connection started");
    }

    @Override
    public void publisher() {
        try {
            MessageContext input = outQueue.poll();
            channel.basicPublish(exchangeName, routingKey,
                    new AMQP.BasicProperties.Builder()
                            .correlationId("onos->rmqserver").build(),
                    input.getBody());
            String message1 = new String(input.getBody(), "UTF-8");
            log.info(" [x] Sent: '{}'", message1);
        } catch (Exception e) {
            log.error(E_PUBLISH_CHAN, e);
        }
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
    }

    @Override
    public String consumer() {
        final String[] message = new String[1];
        try {
            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body)
                        throws IOException {
                    message[0] = new String(body, "UTF-8");
                    log.info(" [x] Received '" + message[0] + "'");

                }
            };
            channel.basicConsume(queueName, true, consumer);
        } catch (Exception e) {
            log.error(E_PUBLISH_CHAN, e);
        }
        log.info("Again Recieved: " + message[0]);
        return message[0];

    }


}
