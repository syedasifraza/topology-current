package flowManager.api;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

/**
 * Created by root on 5/15/17.
 */
public interface AgentFlowService {
    public void installFlows(DeviceId deviceId, PortNumber inPort, PortNumber outPort, String srcIP, String dstIP,
                             String srcPort, String dstPort, Double rate);
    public void removeFlowsByAppId();
}
