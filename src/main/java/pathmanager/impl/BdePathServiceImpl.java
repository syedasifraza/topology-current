package pathmanager.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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
import pathmanager.SaveCalcPath;
import pathmanager.api.BdePathService;
import service.CostService;

import java.io.IOException;
import java.util.*;

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

    SaveCalcPath saveCalcPath= new SaveCalcPath();

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
        Map<String, Map<Collection<String>, Double>> pathInfo = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode dtnIp;
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
            pathInfo.put(mapper.readTree(src).get("ip").toString(), g.getDvcInPath());
            saveCalcPath.setPathInfo(pathInfo);
            log.info("Calculated Path: {}", saveCalcPath.getPathInfo());
            //log.info("Devices in Path {}", g.getDvcInPath());
            //pathLinks(g.getDvcInPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public Double getPathBW(String ipAddress) {
        double temp = 0.0;
        if(saveCalcPath.getPathInfo().containsKey(ipAddress)) {
            log.info("Direct Value {}", saveCalcPath.getPathInfo().get(ipAddress).values());
            temp = saveCalcPath.getPathInfo().get(ipAddress).values().iterator().next();
        }


        //log.info("Values IP {}", saveCalcPath.getPathInfo().values().iterator().next().values());
        return temp;
    }

    public void pathLinks(Map<Collection<String>, Double> devices) {
        String previousDevice = null;
        Set<TopologyEdge> edges = topologyService.getGraph(
                topologyService.currentTopology()).getEdges();

        for(Collection<String> item : devices.keySet()) {
            for(String it :  item) {
                for(TopologyEdge edgeIterator : edges) {
                    //log.info("previous src {} current src {} ", previousDevice,
                    //        edges.next().src().deviceId());
                    if(edgeIterator.src().deviceId().toString().equals(previousDevice) &&
                            edgeIterator.dst().deviceId().toString().equals(it)) {
                        log.info("Path Links {}", edgeIterator.link());
                        break;
                    }

                }
                previousDevice = it;
            }


            log.info("Cost of path {}", devices.get(item));

        }

    }
}
