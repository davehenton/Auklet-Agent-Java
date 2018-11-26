package io.auklet.agent;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class Util {

    static private Logger logger = LoggerFactory.getLogger(Util.class);

    private Util(){ }

    protected static String getMacAddressHash() {
        String machash = "";
        NetworkInterface networkinterface = null;
        try {
            Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
            for (; n.hasMoreElements();) {
                NetworkInterface e = n.nextElement();
                if (!e.isLoopback()) { // Check against network interface "127.0.0.1"
                    networkinterface = e;
                }
                if (e.getHardwareAddress() != null) {
                    break;
                }
            }
            logger.debug("Network Interface: {}", networkinterface);

            byte[] mac = networkinterface.getHardwareAddress();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length; i++) {
                sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
            }

            byte[] macBytes = String.valueOf(sb).getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] macHashByte = md.digest(macBytes);
            machash = Hex.encodeHexString(macHashByte);

        } catch (SocketException | NoSuchAlgorithmException | UnsupportedEncodingException e) {
            logger.error("Error while computing the MAC address hash", e);
        }
        return machash;
    }

    protected static String getIpAddress() {
        String ipAddr = "";
        try {
            URL whatismyip = new URL("http://checkip.amazonaws.com");
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    whatismyip.openStream()));

            ipAddr = in.readLine(); //you get the IP as a String
        } catch (IOException e) {
            logger.error("Error while fetching the IP address", e);
        }
        return ipAddr;
    }

    protected static String createCustomFolder(String sysProperty) {
        String path = System.getProperty(sysProperty) + File.separator + "aukletFiles";
        File newfile = new File(path);
        if (newfile.exists()) {
            logger.debug("Folder already exists");
        } else if (newfile.mkdir()) {
            logger.debug("Folder created");
        } else {
            logger.debug("Folder was not created for {}", sysProperty);
            return null;
        }

        return path;
    }

    protected static String readContents(HttpResponse response) {
        String text;
        try (Scanner scanner = new Scanner(response.getEntity().getContent(), StandardCharsets.UTF_8.name())) {
            text = scanner.useDelimiter("\\A").next();
        } catch (Exception e) {
            logger.error("Error while parsing HTTP response body", e);
            return null;
        }
        return text;
    }
}
