package pathmanager.api;

/**
 * Created by root on 4/10/17.
 */
public interface BdePathService {
    public void calcPath(String src);
    public Double getPathBW(String ipAddress);
    public void setupPath(String pathId);
}
