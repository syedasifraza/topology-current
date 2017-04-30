package service;

import bde.sdn.agent.config.VplsConfigService;
import com.google.common.collect.Multimap;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
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
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VplsConfigService vplsConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    protected ExecutorService eventExecutor;

    private final TopologyListener topologyListener =
            new InternalTopologyListener();

    private final InternalNetworkConfigListener configListener =
            new InternalNetworkConfigListener();




    private ApplicationId appId;

    @Activate
    protected void activate(ComponentContext context) {
        appId = coreService.registerApplication(APP_NAME);
        eventExecutor = newSingleThreadScheduledExecutor(
                groupedThreads("onos/deviceevents", "events-%d", log));
        topologyService.addListener(topologyListener);
        configService.addListener(configListener);
        log.info("This is recieved:" + rmqService.consume());
        setupConnectivity(false);
        log.info("Service Check Started");
    }

    @Deactivate
    protected void deactivate() {
        topologyService.removeListener(topologyListener);
        log.info("Stopped");
    }


    private void setupConnectivity(boolean isNetworkConfigEvent) {

        Multimap<DeviceId, ConnectPoint> multimap = vplsConfigService.gatewaysInfo();

        log.info("Gateways IDs: " + multimap);

    }

    /**
     * Listener for VPLS configuration events.
     */
    private class InternalNetworkConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if (event.configClass() == VplsConfigService.CONFIG_CLASS) {
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

    private class InternalTopologyListener implements TopologyListener {

        @Override
        public void event(TopologyEvent event) {
            if (event == null) {
                log.info("Topology event is null.");
                return;
            }
            log.info("Topology event generated.");
            rmqService.publish(event);
            log.info("Now going to consume messages");
        }
    }


}
