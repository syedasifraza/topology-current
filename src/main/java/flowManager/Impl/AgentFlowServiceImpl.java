package flowManager.Impl;

import flowManager.api.AgentFlowService;
import init.config.InitConfigService;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.*;
import org.onosproject.net.meter.*;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@Component(immediate = true)
@Service
public class AgentFlowServiceImpl implements AgentFlowService {

    public static final int PRIORITY=500;
    public static final int TIME_OUT=120;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InitConfigService initConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected Meter meter;

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


        Band band = DefaultBand.builder()
                .ofType(Band.Type.DROP)
                .burstSize(rate.longValue())
                .withRate(rate.longValue())
                .build();

        MeterRequest meterRequest = DefaultMeterRequest.builder()
                .forDevice(deviceId)
                .fromApp(appId)
                .burst()
                .withUnit(Meter.Unit.KB_PER_SEC)
                .withBands(Collections.singleton(band))
                .add();


        MeterId meterId;
        meterId = meterService.submit(meterRequest).id();
        log.info("Meter Id {}", meterId);
        /*try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        //meterService.withdraw(meterRequest, meterId);


        pushFlows(deviceId, inPort, outPort,
                srcIP, dstIP, meterId);
        pushFlows(deviceId, outPort, inPort,
                dstIP, srcIP, meterId);

        log.info("Meters {}", meter.appId());

    }

    @Override
    public void removeFlowsByAppId() {
        flowRuleService.removeFlowRulesById(appId);
    }

    public void pushFlows(DeviceId deviceId, PortNumber inPort, PortNumber outPort,
                          String srcIP, String dstIP, MeterId meterId) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .meter(meterId)
                .setQueue(7)
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

        log.info("Flow id {} @ device Id {}", addRule.id().toString(), deviceId);
    }
}
