package pathmanager;

import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.FlowId;

import java.util.Objects;
import java.util.Set;

/**
 * Created by root on 5/19/17.
 */
public class PathIds {

    private final DeviceId deviceIds;
    private final Set<FlowId> flowIds;


    public PathIds(DeviceId deviceIds, Set<FlowId> flowIds) {
        this.deviceIds = deviceIds;
        this.flowIds = flowIds;

    }

    public DeviceId dvcIds() {
        return deviceIds;
    }


    public Set<FlowId> flwIds() {
        return flowIds;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PathIds) {
            PathIds that = (PathIds) obj;
            return Objects.equals(deviceIds, that.deviceIds) &&
                    Objects.equals(flowIds, that.flowIds);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceIds, flowIds);
    }

}
