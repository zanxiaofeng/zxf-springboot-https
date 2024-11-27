package zxf.https.client.apache;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;

public class ApacheClientApp {
    public static void main(String[] args) throws Exception {
        //System.setProperty("javax.net.debug", "all");
        //System.setProperty("java.security.debug", "all");
        //System.setProperty("org.apache.commons.logging.diagnostics.dest", "STDOUT");
        testClientCert("https://localhost:8082/home", "/keystore/keystore-client.p12", "/keystore/truststore-client.p12", "changeit");
    }

    private static void testClientCert(String targetUrl, String keyStorePath, String trustStorePath, String passwd) throws Exception {
        SSLContext sslContext = SSLContexts.custom()
                .loadKeyMaterial(ApacheClientApp.class.getResource(keyStorePath), passwd.toCharArray(), passwd.toCharArray())
                .loadTrustMaterial(ApacheClientApp.class.getResource(trustStorePath), passwd.toCharArray())
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setSSLContext(sslContext).build()) {
            try (CloseableHttpResponse response = httpClient.execute(new HttpGet(targetUrl))) {
                System.out.println("Response: " + response);
            }
        }
    }
}
