package zxf.https.client.apache;

import javax.net.ssl.*;
import java.net.*;
import java.util.*;

public class EnumTlsCiphers {
    private static final List<String> PROTOCOLS = List.of("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1");

    public static void main(String[] args) throws Exception {
        String host = "www.163.com";
        int port = 443;

        for (String protocol : PROTOCOLS) {
            System.out.println("=== " + protocol + " ===");

            try {
                SSLContext ctx = SSLContext.getInstance(protocol);
                ctx.init(null, new TrustManager[]{new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }}, new java.security.SecureRandom());

                SSLSocketFactory factory = ctx.getSocketFactory();
                String[] supportedCiphers = factory.getSupportedCipherSuites();

                boolean anySupported = false;
                for (String cipher : supportedCiphers) {
                    try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                        socket.setEnabledProtocols(new String[]{protocol});
                        socket.setEnabledCipherSuites(new String[]{cipher});
                        socket.setSoTimeout(5000);
                        socket.startHandshake();
                        System.out.println(" SUPPORTED: " + cipher);
                        anySupported = true;
                    } catch (Exception e) {
                        // Handshake failed — cipher not supported for this protocol
                    }
                }

                if (!anySupported) {
                    System.out.println(" (No ciphers supported or protocol disabled)");
                }
            } catch (Exception e) {
                System.out.println(" Protocol not available in this JVM");
            }
        }
    }
}