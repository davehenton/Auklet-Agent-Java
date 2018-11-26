package io.auklet.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.*;

public class SystemMetrics {

    static private Logger logger = LoggerFactory.getLogger(SystemMetrics.class);

    private SystemMetrics() { }

    private static OperatingSystemMXBean operatingSystemMXBean;

    protected static void initSystemMetrics() {
        operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        operatingSystemMXBean.getSystemLoadAverage();
    }

    protected static Map<String, Object> getSystemMetrics() {
        double memUsage;
        try{
            com.sun.management.OperatingSystemMXBean mxBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            memUsage = 100 *
                    (1 - ((double) mxBean.getFreePhysicalMemorySize() / (double) mxBean.getTotalPhysicalMemorySize()));
        }catch (Exception e) {
            // If Exception occurs, it mean we can not use com.sun.management package and
            // we fall back without recording mem usage
            logger.warn("Underlying JVM does not support sun framework implementation");
            memUsage = 0;
        }
        try {
            Map<String, Object> obj = new HashMap<>();
            obj.put("outboundNetwork", 0);
            obj.put("inboundNetwork", 0);
            obj.put("cpuUsage",
                    100 * (operatingSystemMXBean.getSystemLoadAverage() / operatingSystemMXBean.getAvailableProcessors()));
            obj.put("memoryUsage", memUsage);
            return obj;
        } catch (Exception e) {
            logger.error("Error while getting system metrics", e);
        }
        return null;
    }
}
