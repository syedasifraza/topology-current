package service;


import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import init.config.InitConfigService;
import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pathmanager.api.BdePathService;
import rmq.sender.api.RmqEvents;
import rmq.sender.api.RmqMsgListener;
import rmq.sender.api.RmqService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;

/**
 * Created by root on 4/1/17.
 */
@Component(immediate = true)
public class ServiceCheck {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String APP_NAME = "sender.check";

    private static final String NET_CONF_EVENT =
            "Received NetworkConfigEvent {}";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected RmqService rmqService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected BdePathService getpath;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InitConfigService initConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CostService costService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    protected ExecutorService eventExecutor;

    private final InternalNetworkConfigListener configListener =
            new InternalNetworkConfigListener();

    private final RmqMsgListener rmqMsgListener =
            new InternalRmqMsgListener();


    private ApplicationId appId;

    @Activate
    protected void activate(ComponentContext context) {
        appId = coreService.registerApplication(APP_NAME);
        eventExecutor = newSingleThreadScheduledExecutor(
                groupedThreads("onos/sdnagentevents", "events-%d", log));

        configService.addListener(configListener);
        rmqService.addListener(rmqMsgListener);
        setupConnectivity(false);
        log.info("Service Check Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }


    private void setupConnectivity(boolean isNetworkConfigEvent) {
        Multimap<DeviceId, ConnectPoint> multimap = initConfigService.gatewaysInfo();
        log.info("Gateway ID: " + multimap);
    }

    private void msgRecieved() {
        JsonConverter(rmqService.consume());
        //log.info(consume);
        Multimap<DeviceId, ConnectPoint> multimap = initConfigService.gatewaysInfo();
        log.info("Gateway ID: " + multimap);
        //getpath.calcPath(consume);


    }


    private class InternalNetworkConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if (event.configClass() == InitConfigService.CONFIG_CLASS) {
                log.debug(NET_CONF_EVENT, event.configClass());
                switch (event.type()) {
                    case CONFIG_ADDED:
                    case CONFIG_UPDATED:
                    case CONFIG_REMOVED:
                        setupConnectivity(true);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private class InternalRmqMsgListener implements RmqMsgListener {

        @Override
        public void event(RmqEvents rmqEvents) {

            switch (rmqEvents.type()) {
                case RMQ_MSG_RECIEVED:
                    log.info("dispatch");
                    msgRecieved();
                    break;
                default:
                    log.info("No Msg recieved");
                    break;
            }
        }
    }


    private void JsonConverter(String messegeRecieved) {
        Map<String, Double> publishPathInfo = new HashMap<>();
        JsonParser parser = new JsonParser();
        JsonObject json = (JsonObject) parser.parse(messegeRecieved);
        JsonArray jsonArray = (JsonArray) json.get("dtns");
        log.info("Command = " + json.get("cmd"));
        if (json.get("cmd").toString().replaceAll("\"", "").equals("sdn_probe")) {
            for (int i = 0; i < jsonArray.size(); i++) {
                log.info("DTNs = " + jsonArray.get(i).getAsJsonObject().get("ip"));
                getpath.calcPath(jsonArray.get(i).getAsJsonObject().toString());
                publishPathInfo.put(jsonArray.get(i).getAsJsonObject().get("ip").toString(),
                        getpath.getPathBW(jsonArray.get(i).getAsJsonObject().get("ip").toString()));
            }
            //publishPathInfo.put("10.0.0.2", 90.00);
            JsonPublishCoverter(json.get("taskId").toString().replaceAll("\"", ""),
                    publishPathInfo);
        }
        else if (json.get("cmd").toString().replaceAll("\"", "").equals("sdn_reserve")) {
            //log.info("Reserve command {}", messegeRecieved);
            getpath.setupPath(json.get("pathId").toString().replaceAll("\"", ""));
        }
        else {
            log.info("command not found {}", messegeRecieved);
        }

    }

    private void JsonPublishCoverter(String taskId, Map<String, Double> dtns) {
        byte[] body = null;
        JsonObject outer = new JsonObject();
        JsonArray middle = new JsonArray();
        log.info("DTNs size {}", dtns.size());
        outer.addProperty("cmd", "sdn_response");
        outer.addProperty("taskId", taskId);
        for(Map.Entry items : dtns.entrySet()) {
            JsonObject obj = new JsonObject();
            //log.info("Keys IPs {}", items.getKey().toString().replaceAll("\"", ""));
            //log.info("Value {}", items.getValue().toString());
            obj.addProperty("ip", items.getKey().toString().replaceAll("\"", ""));
            obj.addProperty("AvailBW", items.getValue().toString());
            middle.add(obj);
        }


        outer.add("dtns", middle);
        body = bytesOf(outer);
        rmqService.publish(body);
        log.info("Publish msg {}", outer);
        //log.info("CMD recieved {}", cmd);
        //log.info("TaskID recieved {}", taskId);
        //log.info("DTNs Information {}", dtns.keySet().iterator().next());
    }

    private byte[] bytesOf(JsonObject jo) {
        return jo.toString().getBytes();
    }
}

