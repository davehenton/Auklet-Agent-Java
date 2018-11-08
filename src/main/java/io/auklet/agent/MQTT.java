package io.auklet.agent;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Scanner;
import java.util.concurrent.ScheduledExecutorService;

public final class MQTT {

    static private Logger logger = LoggerFactory.getLogger(MQTT.class);

    private MQTT(){ }

    protected static MqttClient connectMqtt(String folderPath, ScheduledExecutorService executorService) {
        JSONObject brokerJSON = getbroker();

        if(brokerJSON != null) {
            String serverUrl = "ssl://" + brokerJSON.getString("brokers") + ":" + brokerJSON.getString("port");
            logger.info("Auklet mqtt connection url: " + serverUrl);
            String caFilePath = folderPath + "/CA";
            logger.info("Auklet mqtt connection looking for CA files at: " + caFilePath);
            String mqttUserName = Device.getClient_Username();
            String mqttPassword = Device.getClient_Password();

            MqttClient client;
            try {
                client = new MqttClient(serverUrl, Device.getClient_Id(), new MemoryPersistence(), executorService);
                MqttConnectOptions options = new MqttConnectOptions();
                options.setUserName(mqttUserName);
                options.setPassword(mqttPassword.toCharArray());

                options.setConnectionTimeout(60);
                options.setKeepAliveInterval(60);
                options.setCleanSession(true);
                options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);

                SSLSocketFactory socketFactory = getSocketFactory(caFilePath);
                options.setSocketFactory(socketFactory);

                logger.info("Auklet starting connect the mqtt server...");
                client.connect(options);
                logger.info("Auklet mqtt client connected!");

                return client;
            } catch (Exception e) {
                logger.error("Error while connecting to mqtt: " + e.getMessage());
            }
        }
        return null;
    }

    private static SSLSocketFactory getSocketFactory (String caFilePath) {
        try {
            X509Certificate caCert = null;

            FileInputStream fis = new FileInputStream(caFilePath);
            BufferedInputStream bis = new BufferedInputStream(fis);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            while (bis.available() > 0) {
                caCert = (X509Certificate) cf.generateCertificate(bis);
            }

            KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
            caKs.load(null, null);
            caKs.setCertificateEntry("ca-certificate", caCert);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(caKs);

            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, tmf.getTrustManagers(), null);

            return context.getSocketFactory();
        } catch (Exception e) {
            logger.error("Error while setting up socket factory: " + e.getMessage());
        }

        logger.error("Auklet MQTT Socket factory is null");

        return null;
    }

    private static JSONObject getbroker() {
        HttpClient httpClient = HttpClientBuilder.create().build();

        try {
            HttpGet request = new HttpGet(Auklet.getBaseUrl() + "/private/devices/config/");
            request.addHeader("content-type", "application/json");
            request.addHeader("Authorization", "JWT " + Auklet.ApiKey);
            HttpResponse response = httpClient.execute(request);

            String text;
            try (Scanner scanner = new Scanner(response.getEntity().getContent(), StandardCharsets.UTF_8.name())) {
                text = scanner.useDelimiter("\\A").next();
            } catch (Exception e) {
                logger.error("Exception occurred during reading brokers info: " + e.getMessage());
                return null;
            }

            if (response.getStatusLine().getStatusCode() == 200) {
                return new JSONObject(text);
            }
            else {
                logger.info("get broker response code: "+ response.getStatusLine());
                logger.info("get broker response body: "+ text);
            }

        }catch(Exception e) {
            logger.error("Error while getting the brokers: " + e.getMessage());
        }
        return null;
    }

}