package pathmanager.impl;


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import flowManager.api.AgentFlowService;
import init.config.InitConfigService;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.IpAddress;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pathmanager.AgentGraph;
import pathmanager.PathIds;
import pathmanager.SaveCalcPath;
import pathmanager.api.BdePathService;
import service.CostService;

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

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected AgentFlowService agentFlowService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    SaveCalcPath saveCalcPath= new SaveCalcPath();
    Map<Long, List<PathIds>> t = new LinkedHashMap<>();
    long ids = 0;
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
        log.info("SRC {}", src);
        Map<String, Map<Collection<String>, Double>> pathInfo = new HashMap<>();

        //ObjectMapper mapper = new ObjectMapper();
        //JsonNode dtnIp;
        //dtnIp = mapper.readTree(src).get("ip");
        //log.info("SRC {}", dtnIp.asText());
        IpAddress oneIp = IpAddress.valueOf(src);
        log.info("got ip values of device");
        DeviceId dvcOneId = hostService.getHostsByIp(oneIp).iterator()
                .next().location().deviceId();
        log.info("Host loacted in Device ID :" + dvcOneId);

        Multimap<DeviceId, ConnectPoint> multimap = initConfigService.gatewaysInfo();
        DeviceId dvcTwoId = multimap.keySet().iterator().next();
        log.info("Gateways IDs: " + dvcTwoId);
        if (dvcOneId.toString().equals(dvcTwoId.toString())) {
            Collection<String> device = new LinkedHashSet<>();
            Map<Collection<String>, Double> dvcWithCost = new HashMap<>();
            double portspeed1, portspeed2;
            portspeed1 = deviceService.getPort(dvcTwoId,
                    multimap.get(dvcTwoId).iterator().next().port()).portSpeed();
            portspeed2 = deviceService.getPort(dvcOneId,
                    hostService.getHostsByIp(oneIp).iterator().next().location().port()).portSpeed();

            device.add(dvcOneId.toString());
            dvcWithCost.put(device, Math.min(portspeed1, portspeed2));
            pathInfo.put(src, dvcWithCost);
            saveCalcPath.setPathInfo(pathInfo);
        }
        else {
            Iterator<TopologyEdge> edges = topologyService.getGraph(
                    topologyService.currentTopology()).getEdges().iterator();
            log.info("after edges");
            int size = topologyService.getGraph(
                    topologyService.currentTopology()).getEdges().size();
            final AgentGraph.Edge[] Graph = new AgentGraph.Edge[size];
            Multimap<String, String> sd = ArrayListMultimap.create();
            int i = 0;
            log.info("after topology service get graph");
            edges.forEachRemaining(n -> sd.put(n.src().toString(), n.dst().toString()));
            for (Map.Entry<String, String> entry : sd.entries()) {
                Graph[i] = new AgentGraph.Edge(entry.getKey(), entry.getValue(),
                        (int) costService.retriveCost(entry.getKey(), entry.getValue()));
                i++;
            }

            final String START = dvcOneId.toString();
            final String END = dvcTwoId.toString();
            AgentGraph g = new AgentGraph(Graph);
            g.cleanPath();
            g.dijkstra(START);
            g.printPath(END);
            //log.info("Devices in Path {}", g.getDvcInPath());
            //log.info("Path inform {}", pathInfo);
            pathInfo.put(src, g.getDvcInPath());
            saveCalcPath.setPathInfo(pathInfo);
            //log.info("Devices in Path {}", g.getDvcInPath());
            //pathLinks(g.getDvcInPath());

            log.info("Calculated Path: {}", saveCalcPath.getPathInfo());
        }


    }

    @Override
    public Double getPathBW(String ipAddress) {
        double temp = 0.0;
        if(saveCalcPath.getPathInfo().containsKey(ipAddress)) {
            //log.info("Direct Value {}", saveCalcPath.getPathInfo().get(ipAddress).values());
            temp = saveCalcPath.getPathInfo().get(ipAddress).values().iterator().next();
        }


        //log.info("Values IP {}", saveCalcPath.getPathInfo().values().iterator().next().values());
        return temp;
    }

    @Override
    public boolean checkPathId(String pathId) {
        log.info("before condition");
        //log.info("pathID recieved from json msg {}", pathId);
        log.info("pathId recieved from saved {}", saveCalcPath.getPathInfo());
        if(saveCalcPath.getPathInfo() == null) {
            log.info("Return flase: if empty");
            return false;
        }
        if(pathId.equals(saveCalcPath.getPathInfo().keySet().iterator().next().toString().replaceAll("\"", ""))) {
            log.info("Return true: Now I started to install path on devices");
            return true;
        }
        log.info("Return flase: Now I started to install path on devices");
        return false;
    }

    @Override
    public Long setupPath(String pathId, String srcIP, String dstIP,
                          String srcPort, String dstPort, Double rate) {
        //log.info("src IP {} \n dst IP {} \n srcPort {}\n dstPort {}\n rate {}",
        //        srcIP, dstIP, srcPort, dstPort, rate);

        List<PathIds> g = Lists.newArrayList();
        Multimap<DeviceId, Map<PortNumber, PortNumber>> portInfo = ArrayListMultimap.create();
        log.info("In Setup path {}", saveCalcPath.getPathInfo());
        log.info("Path of given pathID {}", saveCalcPath.getPathInfo().get(pathId).keySet().
                iterator().next().iterator().next());

        IpAddress hostIp = IpAddress.valueOf(pathId);
        HostId hostId = hostService.getHostsByIp(hostIp).iterator().next().id();
        PortNumber hostPort = hostService.getHost(hostId).location().port();
        //log.info("Host Port number {}", hostService.getHost(hostId).location().port());

        portInfo = pathLinks(saveCalcPath.getPathInfo().get(pathId).keySet().
                iterator().next(), hostPort);

        for(DeviceId items: portInfo.keySet()) {

            Set<Long> fId = Sets.newLinkedHashSet();
            PortNumber inPort;
            PortNumber outPort;

            for(int i=0; i < portInfo.get(items).size(); i++) {
                inPort = portInfo.get(items).iterator().next().keySet().iterator().next();
                outPort = portInfo.get(items).iterator().next().get(inPort);
                log.info("Device {}, inport {}, outport {}", items, inPort, outPort);
                agentFlowService.installFlows(items, inPort, outPort, srcIP,
                        dstIP, srcPort, dstPort, rate, fId);
            }
            g.add(new PathIds(items, fId));

        }
        t.put(ids, g);
        log.info("Current Available BW of Path {}", saveCalcPath.getPathInfo().get(pathId).
                get(saveCalcPath.getPathInfo().get(pathId).keySet().iterator().next()).doubleValue());
        //log.info("Required BW for Path {}", rate);
        costService.changeCost(saveCalcPath.getPathInfo().get(pathId).keySet().
                iterator().next(), rate);
        log.info("Iam out of for loop {}", saveCalcPath.getPathInfo());


        for(Long k: t.keySet()) {
            log.info("Path ID={}", k);
            t.get(k).iterator().forEachRemaining(p -> {
                log.info("\ndevices {}", p.dvcIds());
                log.info("\nflows IDs {}", p.flwIds());
            });
        }

        ids = ids + 1;
        return (ids - 1);

    }

    @Override
    public boolean releasePathId(Long pathId) {
        if(t.keySet().contains(pathId)) {
            log.info("Path ID in Map");
            t.get(pathId).iterator().forEachRemaining( p -> {
                agentFlowService.removePathId(p.dvcIds(), p.flwIds());
            });
            t.remove(pathId);
            return true;
        }
        else {
            log.info("Path ID is not in Map");
            return false;
        }

    }

    public Multimap<DeviceId, Map<PortNumber, PortNumber>> pathLinks(Collection<String> devices,
                                                                     PortNumber hostPort) {
        String previousDevice = null;
        Set<TopologyEdge> edges = topologyService.getGraph(
                topologyService.currentTopology()).getEdges();
        Multimap<DeviceId, Map<PortNumber, PortNumber>> portsMap = ArrayListMultimap.create();
        Map<PortNumber, PortNumber> ppMap=new HashMap<>();
        PortNumber inport = hostPort;
        DeviceId gwDevice = null;
        Multimap<DeviceId, ConnectPoint> multimap = initConfigService.gatewaysInfo();
        log.info("Iam in for loop of pathLinks function \n Devices size {}", devices.size());

        if(devices.size() == 1) {
            gwDevice = multimap.keys().iterator().next();
            ppMap.put(inport,multimap.get(gwDevice).iterator().next().port());
            portsMap.put(gwDevice, ppMap);

        }
        else {
            for (String item : devices) {

                for (TopologyEdge edgeIterator : edges) {
                    //log.info("previous src {} current src {} ", previousDevice,
                    //        edges.next().src().deviceId());
                    if (edgeIterator.src().deviceId().toString().equals(previousDevice) &&
                            edgeIterator.dst().deviceId().toString().equals(item)) {
                        Map<PortNumber, PortNumber> pMap = new HashMap<>();
                        log.info("OutPort Number {}", edgeIterator.link().src().port());
                        log.info("InPort Number {}", edgeIterator.link().dst().port());

                        pMap.put(inport, edgeIterator.link().src().port());
                        inport = edgeIterator.link().dst().port();
                        gwDevice = edgeIterator.link().dst().deviceId();
                        portsMap.put(edgeIterator.link().src().deviceId(), pMap);
                        break;
                    }

                }
                previousDevice = item;

            }
            ppMap.put(inport, multimap.get(gwDevice).iterator().next().port());

            portsMap.put(gwDevice, ppMap);
        }

        //log.info("\n In Out ports: {} ", portsMap);
        return  portsMap;

    }
}
