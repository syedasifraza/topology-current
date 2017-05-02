package pathmanager.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import init.config.InitConfigService;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.IpAddress;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pathmanager.AgentGraph;
import pathmanager.api.BdePathService;
import service.CostService;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by root on 4/10/17.
 */
@Component(immediate = true)
@Service
public class BdePathServiceImpl implements BdePathService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InitConfigService initConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CostService costService;

    @Activate
    protected void activate(ComponentContext context) {

        log.info("BDE Path Service Activated");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void calcPath(String src) {
        JsonParser parser = new JsonParser();
        JsonObject json = (JsonObject) parser.parse(src);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode dtnIp;
        try {
            dtnIp = mapper.readTree(src).get("ip");
            IpAddress oneIp = IpAddress.valueOf(dtnIp.asText());
            DeviceId dvcOneId = hostService.getHostsByIp(oneIp).iterator()
                    .next().location().deviceId();
            log.info("Host loacted in Device ID :" + dvcOneId);

            Multimap<DeviceId, ConnectPoint> multimap = initConfigService.gatewaysInfo();
            DeviceId dvcTwoId = multimap.keySet().iterator().next();
            log.info("Gateways IDs: " + dvcTwoId);

            Iterator<TopologyEdge> edges = topologyService.getGraph(
                    topologyService.currentTopology()).getEdges().iterator();

            int size = topologyService.getGraph(
                    topologyService.currentTopology()).getEdges().size();
            final AgentGraph.Edge[] Graph = new AgentGraph.Edge[size];
            Multimap<String, String> sd = ArrayListMultimap.create();
            int i = 0;

            edges.forEachRemaining(n -> sd.put(n.src().toString(), n.dst().toString()));
            for (Map.Entry<String, String> entry : sd.entries()) {
                Graph[i] = new AgentGraph.Edge(entry.getKey(), entry.getValue(),
                        (int) costService.retriveCost(entry.getKey(), entry.getValue()));
                i++;
            }

            final String START = dvcOneId.toString();
            final String END = dvcTwoId.toString();
            AgentGraph g = new AgentGraph(Graph);
            g.dijkstra(START);
            g.printPath(END);

        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
