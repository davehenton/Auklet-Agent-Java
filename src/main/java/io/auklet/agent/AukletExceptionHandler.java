package io.auklet.agent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AukletExceptionHandler implements Thread.UncaughtExceptionHandler {

    private Thread.UncaughtExceptionHandler defaultExceptionHandler;
    static private Logger logger = LoggerFactory.getLogger(AukletExceptionHandler.class);

    private AukletExceptionHandler(Thread.UncaughtExceptionHandler defaultExceptionHandler) {
        this.defaultExceptionHandler = defaultExceptionHandler;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable thrown) {
        if (defaultExceptionHandler != null) {
            // call the original handler
            defaultExceptionHandler.uncaughtException(thread, thrown);
        } else if (!(thrown instanceof ThreadDeath)) {
            sendEvent(thrown);
        }
    }

    protected static AukletExceptionHandler setup() {
        logger.info("Auklet Configuring uncaught exception handler");
        Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler != null) {
            logger.info("Default UncaughtExceptionHandler class= {}",
                    currentHandler.getClass().getName());
        }

        AukletExceptionHandler handler = new AukletExceptionHandler(currentHandler);
        Thread.setDefaultUncaughtExceptionHandler(handler);
        return handler;
    }

    protected static synchronized void sendEvent(Throwable thrown) {
        List<Object> list = new ArrayList<>();

        for (StackTraceElement se : thrown.getStackTrace()) {
            Map<String, Object> map = new HashMap<>();
            map.put("functionName", se.getMethodName());
            map.put("className", se.getClassName());
            map.put("filePath", se.getFileName());
            map.put("lineNumber", se.getLineNumber());
            list.add(map);
        }
        setStackTrace(list, thrown.toString());

        try {
            byte[] bytesToSend = Messages.createMessagePack();
            MqttMessage message = new MqttMessage(bytesToSend);
            message.setQos(2);
            Auklet.client.publish("java/events/" + Device.getOrganization() + "/" +
                    Device.getClient_Username(), message);
            logger.info("Duplicate message published: {}", message.isDuplicate());

        } catch (MqttException | NullPointerException e) {
            logger.error("Error while publishing the MQTT message", e);
        }
    }

    private static void setStackTrace(List<Object> stackTraceList, String exceptionMessage) {
        Messages.map.put("stackTrace", stackTraceList);
        Messages.map.put("timestamp", System.currentTimeMillis());
        Messages.map.put("excType", exceptionMessage);
    }

}
