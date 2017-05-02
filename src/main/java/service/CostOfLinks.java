package service;

import org.onosproject.net.Link;

import java.util.Map;

/**
 * Created by root on 5/1/17.
 */
public class CostOfLinks {
    private static Map<Link, Long> cost;

    public static Map<Link, Long> getCost() {
        return cost;
    }

    public void setCost(Map<Link, Long> cost) {
        this.cost = cost;
    }
}
