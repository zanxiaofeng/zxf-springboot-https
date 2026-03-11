package zxf.https.client.apache;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;

import javax.net.ssl.*;

public class ApacheClientFactory {
    private static String KEY_STORE_PATH = "/keystore/keystore-client.p12";
    private static String KEY_STORE_PASS_PHRASE = "changeit";
    private static String TRUST_STORE_PATH = "/keystore/truststore-client.p12";
    private static String TRUST_STORE_PASS_PHRASE = "changeit";

    public static CloseableHttpClient getSafeHttpClient() {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(ApacheClientFactory.class.getResource(TRUST_STORE_PATH), TRUST_STORE_PASS_PHRASE.toCharArray())
                    .build();

            return HttpClients.custom()
                    .setConnectionManager(org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
                            .build())
                    .build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static CloseableHttpClient getSafeHttpClientWithClientAuth() {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadKeyMaterial(ApacheClientFactory.class.getResource(KEY_STORE_PATH), KEY_STORE_PASS_PHRASE.toCharArray(), KEY_STORE_PASS_PHRASE.toCharArray())
                    .loadTrustMaterial(ApacheClientFactory.class.getResource(TRUST_STORE_PATH), TRUST_STORE_PASS_PHRASE.toCharArray())
                    .build();

            return HttpClients.custom()
                    .setConnectionManager(org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
                            .build())
                    .build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static CloseableHttpClient getUnsafeHttpClient() {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((chain, authType) -> true)
                    .build();
            return HttpClients.custom()
                    .setConnectionManager(org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
                            .build())
                    .build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static CloseableHttpClient getUnsafeOkHttpClientWithClientAuth() {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadKeyMaterial(ApacheClientFactory.class.getResource(KEY_STORE_PATH), KEY_STORE_PASS_PHRASE.toCharArray(), KEY_STORE_PASS_PHRASE.toCharArray())
                    .loadTrustMaterial((chain, authType) -> true)
                    .build();
            return HttpClients.custom()
                    .setConnectionManager(org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
                            .build())
                    .build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
