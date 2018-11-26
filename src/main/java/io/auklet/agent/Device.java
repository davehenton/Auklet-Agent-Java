package io.auklet.agent;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.util.Scanner;
import java.nio.file.Files;

public final class Device {

    private Device(){ }

    static private Logger logger = LoggerFactory.getLogger(Device.class);

    // AppId is 22 bytes but AES is a 128-bit block cipher supporting keys of 128, 192, and 256 bits.
    private static final Key aesKey = new SecretKeySpec(Auklet.AppId.substring(0,16).getBytes(), "AES");

    private static String client_id;
    private static String client_username;
    private static String client_password;
    private static String organization;

    public static boolean register_device(String folderPath) {
        String filename = "/.AukletAuth";

        try {
            Path fileLocation = Paths.get(folderPath + filename);
            byte[] data = Files.readAllBytes(fileLocation);
            logger.info("Auklet auth file content length: {}", data.length);

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            String decrypted = new String(cipher.doFinal(data));
            setCreds(new JSONObject(decrypted));

        } catch (FileNotFoundException | NoSuchFileException e) {
            logger.info("Creating a new Auklet auth file");
            JSONObject newObject = create_device();
            if (newObject != null) {
                setCreds(newObject);
                writeCreds(folderPath + filename);
            } else return false;

        } catch (Exception e) {
            logger.error("Error during device registration", e);
            return false;
        }
        return true;
    }

    private static JSONObject create_device() {
        HttpClient httpClient = HttpClientBuilder.create().build();

        try {
            JSONObject obj = new JSONObject();
            obj.put("mac_address_hash", Util.getMacAddressHash());
            obj.put("application", Auklet.AppId);

            HttpPost request = new HttpPost(Auklet.getBaseUrl() + "/private/devices/");
            StringEntity params = new StringEntity(obj.toString());

            request.addHeader("content-type", "application/json");
            request.addHeader("Authorization", "JWT "+Auklet.ApiKey);
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);

            String contents = Util.readContents(response);

            if(response.getStatusLine().getStatusCode() == 201 && contents != null) {
                return new JSONObject(contents);
            } else {
                logger.error("Error while creating device: {}: {}", response.getStatusLine(), contents);
            }

        } catch (Exception ex) {
            logger.error("Error while posting device info", ex);
        }
        return null;
    }

    private static void setCreds(JSONObject jsonObject) {
        client_password = jsonObject.getString("client_password");
        client_username = jsonObject.getString("id");
        client_id = jsonObject.getString("client_id");
        organization = jsonObject.getString("organization");
    }

    private static void writeCreds(String filename) {
        JSONObject obj = new JSONObject();
        obj.put("client_password", client_password);
        obj.put("id", client_username);
        obj.put("client_id", client_id);
        obj.put("organization", organization);

        try (FileOutputStream file = new FileOutputStream(filename)) {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] encrypted = cipher.doFinal(obj.toString().getBytes());

            file.write(encrypted);
            file.flush();

        } catch (Exception e) {
            logger.error("Error while writing Auklet auth credentials", e);
        }
    }

    public static String getClient_Username(){
        return client_username;
    }

    public static String getClient_Password(){
        return client_password;
    }

    public static String getClient_Id(){
        return client_id;
    }

    public static String getOrganization(){
        return organization;
    }

    public static boolean get_Certs(String folderPath) {
        try {
            File file = new File(folderPath + "/CA");
            if (file.createNewFile()) {
                HttpClient httpClient = HttpClientBuilder.create().build();
                URL newUrl = new URL(Auklet.getBaseUrl() + "/private/devices/certificates/");
                HttpURLConnection con = (HttpURLConnection) newUrl.openConnection();

                con.setRequestProperty("Authorization", "JWT " + Auklet.ApiKey);
                con.setDoInput(true);
                con.setRequestMethod("GET");
                con.setInstanceFollowRedirects(true);

                con.getResponseCode();

                HttpGet request = new HttpGet(con.getURL().toURI());
                HttpResponse response = httpClient.execute(request);

                String contents = Util.readContents(response);

                if (response.getStatusLine().getStatusCode() == 200 && contents != null) {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(folderPath + "/CA"));
                    writer.write(contents);
                    writer.close();
                    logger.info("CA file is created!");
                } else{
                    logger.error("Error while getting certs: {}: {}",
                            response.getStatusLine(), contents);
                    if(file.delete()){
                        logger.info("CA file deleted");
                        return false;
                    }
                }
            } else {
                logger.info("CA file already exists");
            }
        } catch (Exception e) {
            logger.error("Error while getting CA cert", e);
            return false;
        }
        return true;
    }
}
