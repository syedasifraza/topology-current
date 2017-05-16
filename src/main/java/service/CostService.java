package service;

import java.util.Collection;

/**
 * Created by root on 5/1/17.
 */
public interface CostService {
    public long retriveCost(String src, String dst);
    public void changeCost(Collection<String> devices, Double rate);
}
