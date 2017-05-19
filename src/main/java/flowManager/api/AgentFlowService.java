package flowManager.api;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.FlowId;

import java.util.Set;

/**
 * Created by root on 5/15/17.
 */
public interface AgentFlowService {
    public void installFlows(DeviceId deviceId, PortNumber inPort, PortNumber outPort, String srcIP, String dstIP,
                             String srcPort, String dstPort, Double rate, Set<FlowId> fId);

    public void removeFlowsByAppId();
}
