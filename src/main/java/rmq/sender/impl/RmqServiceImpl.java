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
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.event.Event;
import org.onosproject.net.topology.TopologyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rmq.sender.api.RmqEvents;
import rmq.sender.api.RmqMsgListener;
import rmq.sender.api.RmqService;
import rmq.sender.util.MQUtil;
import org.osgi.service.component.ComponentContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

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

    private String exchangeName;
    private String routingKey;
    private String queueName;
    private String url;

    private ExecutorService executorService;
    private Connection conn;
    private Channel channel;


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
        try {
            correlationId = "onos->rmqserver";
            exchangeName =  "onos_exchg_wr_to_rmqs";
            routingKey = "abc.zxy";
            queueName = "onos_recieve_queue";
            url = "amqps://yosemite.fnal.gov:5671/%2F";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        java.util.Properties prop = getProp(context);
        URL configUrl;

        configUrl = context.getBundleContext().getBundle()
                .getResource("client_cacerts.jks");

        try {
            InputStream is = configUrl.openStream();
            start(is);
        } catch (Exception e) {
            log.error(ExceptionUtils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        consumer();



    }

    private void uninitializeProducers() {
        log.info("RMQ Serivce Stoped");
        stop();
    }

    private byte[] bytesOf(JsonObject jo) {
        return jo.toString().getBytes();
    }

    /**
     * Publishes Device, Topology &amp; Link event message to MQ server.
     *
     * @param event Event received from the corresponding sender like topology, device etc
     */
    @Override
    public void publish(Event<? extends Enum, ?> event) {
        byte[] body = null;
        if (null == event) {
            log.info("Captured event is null...");
            return;
        }
        if (event instanceof TopologyEvent) {
            body = bytesOf(MQUtil.json((TopologyEvent) event));
        } else {
            log.info("Invalid event: '{}'", event);
        }
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

    public void consumer() {
        try {
            Consumer consumer = new DefaultConsumer(channel) {
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
            channel.basicConsume(queueName, true, consumer);
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


    public void setExecutorService(ExecutorService executorService) {
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
