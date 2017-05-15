package flowManager.Impl;

import flowManager.api.AgentFlowService;
import init.config.InitConfigService;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.*;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
@Service
public class AgentFlowServiceImpl implements AgentFlowService {

    public static final int PRIORITY=10;
    public static final int TIME_OUT=120;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InitConfigService initConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    ApplicationId appId;
    @Activate
    protected void activate(ComponentContext context) {

        appId = initConfigService.getAppId();
        log.info("Flow Manager Service Activated");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Flow Manager Stopped");
    }

    @Override
    public void installFlows(DeviceId deviceId, PortNumber inPort, PortNumber outPort,
                             String srcIP, String dstIP,
                             String srcPort, String dstPort, Double rate) {
        log.info("\n Device IDs {}, srcIP {}, dstIP {}, srcPort {}, dstPort {}, rate {}",
                deviceId, srcIP, dstIP, srcPort, dstPort, rate);


        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort)
                .build();

        TrafficSelector.Builder sbuilder;
        FlowRuleOperations.Builder rules = FlowRuleOperations.builder();

        sbuilder = DefaultTrafficSelector.builder();


        sbuilder.matchIPSrc(IpPrefix.valueOf((IpAddress.valueOf(srcIP)), 32))
                .matchIPDst(IpPrefix.valueOf((IpAddress.valueOf(dstIP)), 32))
                .matchEthType((short) 0x800)
                .matchInPort(inPort);


        FlowRule addRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .withSelector(sbuilder.build())
                .withTreatment(treatment)
                .withPriority(PRIORITY)
                .makePermanent()
                .fromApp(appId)
                .build();

        rules.add(addRule);

        flowRuleService.apply(rules.build());
    }

    @Override
    public void removeFlowsByAppId() {
        flowRuleService.removeFlowRulesById(appId);
    }
}
