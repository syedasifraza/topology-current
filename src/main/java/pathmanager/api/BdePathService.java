package pathmanager.api;

/**
 * Created by root on 4/10/17.
 */
public interface BdePathService {
    public void calcPath(String src);
    public Double getPathBW(String ipAddress);
    public boolean checkPathId(String pathId);
    public Long setupPath(String pathId, String srcIP, String dstIP,
                          String srcPort, String dstPort, Double rate);
}
