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

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.rabbitmq.client.*;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rmq.sender.api.RmqEvents;
import rmq.sender.api.RmqManagerService;
import rmq.sender.api.RmqMsgListener;
import rmq.sender.api.RmqService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;

/**
 * Default implementation of {@link RmqService}.
 */
@Component(immediate = true)
@Service
public class RmqServiceImpl
        extends AbstractListenerManager<RmqEvents, RmqMsgListener>
        implements RmqService {

    private static final String E_CREATE_CHAN =
            "Error creating the RabbitMQ channel";
    private static final String E_PUBLISH_CHAN =
            "Error in publishing to the RabbitMQ channel";
    private static final Logger log = LoggerFactory.getLogger(RmqServiceImpl.class);
    private static final int RECOVERY_INTERVAL = 15000;

    private final BlockingQueue<MessageContext> msgOutQueue =
            new LinkedBlockingQueue<>(10);

    private final BlockingQueue<MessageContext> msgInQueue =
            new LinkedBlockingQueue<>(1);

    private RmqManagerService rmqManagerConsumer;

    private RmqManagerService rmqManagerPublisher;

    private String exchangeName_c;
    private String routingKey_c;
    private String queueName_c;
    private String exchangeName_p;
    private String routingKey_p;
    private String queueName_p;
    private String url;
    private String type;

    private Channel channel_c;
    private Channel channel_p;




    private String correlationId;

    private static final String APP_NAME = "RMQ.Service";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    protected ExecutorService eventExecutor;

    private ApplicationId appId;

    @Activate
    protected void activate(ComponentContext context) {
        eventDispatcher.addSink(RmqEvents.class, listenerRegistry);
        appId = coreService.registerApplication(APP_NAME);
        eventExecutor = newSingleThreadScheduledExecutor(
                groupedThreads("onos/deviceevents", "events-%d", log));
        initializeProducers(context);
        log.info("RMQ Service Provider Started");
    }

    @Deactivate
    protected void deactivate() {
        uninitializeProducers();
        eventDispatcher.removeSink(RmqEvents.class);
        log.info("Stopped");
    }

    private void initializeProducers(ComponentContext context) {
        java.util.Properties prop = getProp(context);
        URL configUrl;

        configUrl = context.getBundleContext().getBundle()
                .getResource("client_cacerts.jks");

        //log.info("Properties {}", prop.getProperty("amqp.sender.queue.info"));
        correlationId = prop.getProperty("amqp.col.id.info");
        url = prop.getProperty("amqp.protocol.info") + "://" +
                prop.getProperty("amqp.hostname.ip.info") + ":" +
                prop.getProperty("amqp.port.info") +
                prop.getProperty("amqp.vhost.info") + "%2F";
        type = prop.getProperty("amqp.type.info");

        //consumer setting from properties file
        exchangeName_c = prop.getProperty("amqp.consumer.exchange.info");
        routingKey_c = prop.getProperty("amqp.consumer.routingkey.info");
        queueName_c = prop.getProperty("amqp.consumer.queue.info");

        //publisher setting from properties file
        exchangeName_p = prop.getProperty("amqp.publisher.exchange.info");
        routingKey_p = prop.getProperty("amqp.publisher.routingkey.info");
        queueName_p = prop.getProperty("amqp.publisher.queue.info");

        //log.info("URL {}", url);
        rmqManagerConsumer = new RmqManagerImpl(msgOutQueue, exchangeName_c,
                routingKey_c,queueName_c, url, type);
        rmqManagerPublisher = new RmqManagerImpl(msgOutQueue, exchangeName_p,
                routingKey_p,queueName_p, url, type);
            /*correlationId = "onos->rmqserver";
            exchangeName =  "onos_exchg_wr_to_rmqs";
            routingKey = "abc.zxy";
            queueName = "onos_recieve_queue";*/
            //url = "amqps://yosemite.fnal.gov:5671/%2F";


        try {
            InputStream is = configUrl.openStream();
            InputStream is1 = configUrl.openStream();
            channel_c = rmqManagerConsumer.start(is);
            log.info("Consumer connection established");
            channel_p = rmqManagerPublisher.start(is1);
            log.info("Publisher connection established");
        } catch (Exception e) {
            log.error(ExceptionUtils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        consumer();

    }

    private void uninitializeProducers() {
        log.info("RMQ Serivce Stoped");
        rmqManagerConsumer.stop();
        rmqManagerPublisher.stop();
    }

    private byte[] bytesOf(JsonObject jo) {
        return jo.toString().getBytes();
    }


    @Override
    public void publish(byte[] body) {

        processAndPublishMessage(body);
    }

    /*
     * Constructs message context and publish it to rabbit mq server.
     *
     * @param body Byte stream of the event's JSON data
     */
    private void processAndPublishMessage(byte[] body) {
        Map<String, Object> props = Maps.newHashMap();
        props.put("correlation_id", correlationId);
        MessageContext mc = new MessageContext(body, props);
        try {
            msgOutQueue.put(mc);
            String message = new String(body, "UTF-8");
            log.info(" [x] Again Sent '{}'", message);
        } catch (InterruptedException | UnsupportedEncodingException e) {
            log.error(ExceptionUtils.getFullStackTrace(e));
        }
        publisher();
    }

    public void publisher() {
        try {
            MessageContext input = msgOutQueue.poll();
            channel_p.basicPublish(exchangeName_p, routingKey_p,
                    new AMQP.BasicProperties.Builder()
                            .correlationId(correlationId).build(),
                    input.getBody());
            String message1 = new String(input.getBody(), "UTF-8");
            log.info(" [x] Sent: '{}'", message1);
        } catch (Exception e) {
            log.error(E_PUBLISH_CHAN, e);
        }
    }

    public void consumer() {
        try {
            Consumer consumer = new DefaultConsumer(channel_c) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body)
                        throws IOException {
                    String message = new String(body, "UTF-8");
                    log.info(" [x] Recieved: '{}'", message);
                    processRecievedMessage(body);
                    post(new RmqEvents(RmqEvents.Type.RMQ_MSG_RECIEVED, message));
                    log.info(" [x] Recieved after post: '{}'", message);
                }
            };
            channel_c.basicConsume(queueName_c, true, consumer);
        } catch (Exception e) {
            log.error(E_PUBLISH_CHAN, e);
        }
    }

    private void processRecievedMessage(byte[] body) {
        MessageContext mc = new MessageContext(body);
        try {
            msgInQueue.put(mc);
            String message = new String(body, "UTF-8");
            log.info(" [x] Recieved msqInQueue: '{}'", message);
        } catch (InterruptedException | UnsupportedEncodingException e) {
            log.error(ExceptionUtils.getFullStackTrace(e));
        }
    }

    @Override
    public String consume() {
        try {
            MessageContext input = msgInQueue.poll();
            String message1 = new String(input.getBody(), "UTF-8");
            return message1;
        } catch (Exception e) {
            log.error(E_PUBLISH_CHAN, e);
        }
        return null;
    }


    /*public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void start(InputStream filepath) {
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
            channel.exchangeDeclare(exchangeName, type, true);
            /*
             * Setting the following parameters to queue
             * durable    - true
             * exclusive  - false
             * autoDelete - false
             * arguments  - null
             */
    /*        channel.queueDeclare(queueName, true, false, true, null);
            channel.queueBind(queueName, exchangeName, routingKey);
        } catch (Exception e) {
            log.error(E_CREATE_CHAN, e);
        }
        log.info("Connection started");
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
    }*/


    public static java.util.Properties getProp(ComponentContext context) {
        URL configUrl;
        try {
            configUrl = context.getBundleContext().getBundle()
                    .getResource("rabbitmq.properties");
        } catch (Exception ex) {
            // This will be used only during junit test case since bundle
            // context will be available during runtime only.
            File file = new File(
                    RmqServiceImpl.class.getClassLoader().getResource("rabbitmq.properties")
                            .getFile());
            try {
                configUrl = file.toURL();
            } catch (MalformedURLException e) {
                log.error(ExceptionUtils.getFullStackTrace(e));
                throw new RuntimeException(e);
            }
        }

        java.util.Properties properties;
        try {
            InputStream is = configUrl.openStream();
            properties = new java.util.Properties();
            properties.load(is);
        } catch (Exception e) {
            log.error(ExceptionUtils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return properties;
    }

}
