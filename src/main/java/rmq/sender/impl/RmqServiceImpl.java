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
import org.onosproject.event.Event;
import org.onosproject.net.topology.TopologyEvent;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rmq.sender.api.RmqConnectionManager;
import rmq.sender.api.RmqEvents;
import rmq.sender.api.RmqMsgListener;
import rmq.sender.api.RmqService;
import rmq.sender.util.MQUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
public class RmqServiceImpl extends AbstractListenerManager<RmqEvents, RmqMsgListener>
        implements RmqService {
    private static final Logger log = LoggerFactory.getLogger(
                                                       RmqServiceImpl.class);

    private static final String E_CREATE_CHAN =
            "Error creating the RabbitMQ channel";
    private static final String E_PUBLISH_CHAN =
            "Error in publishing to the RabbitMQ channel";

    private final BlockingQueue<MessageContext> msgOutQueue =
            new LinkedBlockingQueue<>(10);

    private RmqConnectionManager manageSender;

    private RmqConnectionManager manageReciever;

    private String correlationId;

    private BlockingQueue<MessageContext> outQueue;


    private Channel channel_c;
    private Channel channel_p;

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
        log.info("Stopped");
    }

    private void initializeProducers(ComponentContext context) {
        try {
            correlationId = "onos->rmqserver";
            outQueue = msgOutQueue;
            manageSender = new MQSender(msgOutQueue, "onos_exchg_wr_to_rmqs", "onos.rkey.rmqs", "onos_send_queue",
                    "amqp://onosrmq:onosrocks@127.0.0.1:5672/%2F");

            manageReciever = new MQSender(msgOutQueue, "onos_exchg", "abc.zxy", "onos_recieve_queue",
                    "amqp://onosrmq:onosrocks@127.0.0.1:5672/%2F");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        channel_p = manageSender.start1();
        log.info("Sender Connections Started");

        channel_c = manageReciever.start1();
        log.info("Reciever Connections Started");

    }

    private void uninitializeProducers() {
        log.info("RMQ Serivce Stoped");
        manageSender.stop();
        manageReciever.stop();
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
        props.put("correlation_id11", correlationId);
        MessageContext mc = new MessageContext(body, props);
        try {
            msgOutQueue.put(mc);
            String message = new String(body, "UTF-8");
            log.info(" [x] Sent '{}'", message);
        } catch (InterruptedException | UnsupportedEncodingException e) {
            log.error(ExceptionUtils.getFullStackTrace(e));
        }
        publisher();
    }

    @Override
    public void consume() {
        log.info("Consumer Called");
        consumer();
    }

    public void consumer() {
        try {
            Consumer consumer = new DefaultConsumer(channel_c) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body)
                        throws IOException {
                    String message = new String(body, "UTF-8");
                    log.info(" [x] Received '" + message + "'");
                    post(new RmqEvents(RmqEvents.Type.RMQ_MSG_RECIEVED, "Test Received Msg"));
                }
            };
            channel_c.basicConsume("onos_recieve_queue", true, consumer);
        } catch (Exception e) {
            log.error(E_PUBLISH_CHAN, e);
        }

    }

    public void publisher() {
        try {
            MessageContext input = outQueue.poll();
            channel_p.basicPublish("onos_exchg_wr_to_rmqs", "onos.rkey.rmqs",
                    new AMQP.BasicProperties.Builder()
                            .correlationId("onos->rmqserver").build(),
                    input.getBody());
            String message1 = new String(input.getBody(), "UTF-8");
            log.info(" [x] Sent: '{}'", message1);
        } catch (Exception e) {
            log.error(E_PUBLISH_CHAN, e);
        }
    }

}
