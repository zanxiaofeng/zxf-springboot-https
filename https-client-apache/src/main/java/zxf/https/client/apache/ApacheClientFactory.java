package zxf.https.client.apache;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.*;

public class ApacheClientFactory {
    private static String KEY_STORE_PATH = "/keystore/keystore-client.p12";
    private static String KEY_STORE_PASS_PHRASE = "changeit";
    private static String TRUST_STORE_PATH = "/keystore/truststore-client.p12";
    private static String TRUST_STORE_PASS_PHRASE = "changeit";

    public static CloseableHttpClient getSafeHttpClient() {
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(ApacheClientFactory.class.getResource(TRUST_STORE_PATH), TRUST_STORE_PASS_PHRASE.toCharArray())
                    .build();

            return HttpClients.custom().setSSLContext(sslContext).build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static CloseableHttpClient getSafeHttpClientWithClientAuth() {
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(ApacheClientFactory.class.getResource(KEY_STORE_PATH), KEY_STORE_PASS_PHRASE.toCharArray(), KEY_STORE_PASS_PHRASE.toCharArray())
                    .loadTrustMaterial(ApacheClientFactory.class.getResource(TRUST_STORE_PATH), TRUST_STORE_PASS_PHRASE.toCharArray())
                    .build();

            return HttpClients.custom().setSSLContext(sslContext).build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static CloseableHttpClient getUnsafeHttpClient() {
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial((chain, authType) -> true)
                    .build();
            return HttpClients.custom().setSSLContext(sslContext).build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static CloseableHttpClient getUnsafeOkHttpClientWithClientAuth() {
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(ApacheClientFactory.class.getResource(KEY_STORE_PATH), KEY_STORE_PASS_PHRASE.toCharArray(), KEY_STORE_PASS_PHRASE.toCharArray())
                    .loadTrustMaterial((chain, authType) -> true)
                    .build();
            return HttpClients.custom().setSSLContext(sslContext).build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
