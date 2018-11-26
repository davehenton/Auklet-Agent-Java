package io.auklet.agent;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Auklet {

    static protected String AppId;
    static protected String ApiKey;
    static protected MqttClient client;
    static private Logger logger = LoggerFactory.getLogger(Auklet.class);

    /*
    Ref: https://github.com/eclipse/paho.mqtt.java/issues/402#issuecomment-424686340
     */
    static private ScheduledExecutorService mqttThreadPool = Executors.newScheduledThreadPool(10,
            (Runnable r) -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            });

    private Auklet(){ }

    private static void setup(String appId, String apiKey, boolean handleShutDown){
        ApiKey = apiKey;
        AppId = appId;

        if(handleShutDown) {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {
                        try {
                            logger.info("Auklet agent shutting down");
                            Auklet.shutdown();
                        } catch (Exception e) {
                            logger.error("Error while shutting down Auklet agent", e);
                        }
                    })
            );
        }

        SystemMetrics.initSystemMetrics();

        String folderPath = Util.createCustomFolder("user.dir");
        if (folderPath == null){
            folderPath = Util.createCustomFolder("user.home");
        }
        if (folderPath == null){
            folderPath = Util.createCustomFolder("java.io.tmpdir");
        }
        logger.info("Directory to store creds: " + folderPath);

        if(Device.get_Certs(folderPath) && Device.register_device(folderPath)) {
            client = MQTT.connectMqtt(folderPath, mqttThreadPool);
            if (client != null) {
                AukletExceptionHandler.setup();
            }
        }
    }

    public static void init(String appId, String apiKey){
        setup(appId, apiKey, true);
    }

    public static void init(String appId, String apiKey, boolean handleShutDown){
        setup(appId, apiKey, handleShutDown);
    }

    public static void exception(Throwable thrown){
        AukletExceptionHandler.sendEvent(thrown);
    }

    public static void shutdown() {
        if (client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException e) {
                logger.error("Error while disconnecting MQTT client", e);
                try {
                    client.disconnectForcibly();
                } catch (MqttException e2) {
                }
            }
        }
        try {
            client.close();
        } catch (MqttException e) {
            logger.error("Error while closing MQTT client", e);
        } finally {
            mqttThreadPool.shutdown();
            try {
                mqttThreadPool.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e2) {}
        }
    }

    public static String getBaseUrl() {
      String fromEnv = System.getenv("AUKLET_BASEURL");
      return fromEnv != null ? fromEnv : "https://api.auklet.io";
    }

}
