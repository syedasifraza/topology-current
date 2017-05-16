package service;

import org.apache.felix.scr.annotations.*;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by root on 5/1/17.
 */
@Component(immediate = true)
@Service
public class SetCost implements CostService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    Map<Link, Long> m = new HashMap<>();

    @Activate
    protected void activate(ComponentContext context) {
        CostAtStatup();
        log.info("Cost Service Activated");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public long retriveCost(String src, String dst) {

        CostOfLinks cl = new CostOfLinks();
        final long[] cost = new long[1];
        Iterator<Map.Entry<Link, Long>> compare = cl.getCost().entrySet().iterator();

        compare.forEachRemaining(n -> {
            if (n.getKey().src().deviceId().toString().equals(src)
                    && n.getKey().dst().deviceId().toString().equals(dst)) {
                cost[0] = n.getValue();
            }
        });
        return cost[0];

    }

    @Override
    public void changeCost(Collection<String> devices, Double rate) {
        log.info("In change cost {}", rate);

        CostOfLinks cl = new CostOfLinks();

        Set<TopologyEdge> edges = topologyService.getGraph(
                topologyService.currentTopology()).getEdges();
        Link links;

        String previousDevice = null;
        for (String item : devices) {

            for (TopologyEdge edgeIterator : edges) {

                if ((edgeIterator.src().deviceId().toString().equals(previousDevice) &&
                        edgeIterator.dst().deviceId().toString().equals(item)) ||
                        (edgeIterator.src().deviceId().toString().equals(item) &&
                                edgeIterator.dst().deviceId().toString().equals(previousDevice))) {
                    log.info("Counting links {}", edgeIterator.link());
                    m.replace(edgeIterator.link(),
                            (m.get(edgeIterator.link()) - rate.longValue()));

                }

            }
            previousDevice = item;

        }
        log.info("{}", cl.getCost());

    }

    public void CostAtStatup() {

        CostOfLinks cl = new CostOfLinks();
        Iterator<TopologyEdge> edges = topologyService.getGraph(
                topologyService.currentTopology()).getEdges().iterator();
        //Map<Link, Long> m = new HashMap<>();
        edges.forEachRemaining(n -> {
            m.put(n.link(), CalcPortSpeed(n.link()));
            cl.setCost(m);
        });
        //log.info("Links Cost Assigned {}", cl.getCost());

    }

    private long CalcPortSpeed(Link link) {
        long portSpeed;
        portSpeed = (deviceService.getPort(link.src().deviceId(),
                link.src().port()).portSpeed());
        return portSpeed;
    }
}
