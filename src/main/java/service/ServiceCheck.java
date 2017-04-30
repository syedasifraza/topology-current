package service;


import com.google.common.collect.Multimap;
import init.config.InitConfigService;
import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rmq.sender.api.RmqEvents;
import rmq.sender.api.RmqMsgListener;
import rmq.sender.api.RmqService;

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
    protected InitConfigService initConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

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
                groupedThreads("onos/deviceevents", "events-%d", log));

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
        String consume;
        consume = rmqService.consume();
        log.info(consume);
        Multimap<DeviceId, ConnectPoint> multimap = initConfigService.gatewaysInfo();
        log.info("Gateway ID: " + multimap);

    }

    /**
     * Listener for VPLS configuration events.
     */
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

    private class InternalTopologyListener implements TopologyListener {

        @Override
        public void event(TopologyEvent event) {
            if (event == null) {
                log.debug("Topology event is null.");
                return;
            }
            //rmqService.publish(event);

        }
    }
}
