package zxf.https.client.okhttp;

import okhttp3.OkHttpClient;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;

public class OkHttpClientFactory {
    private static String KEY_STORE_PATH = "keystore/keystore-client.jks";
    private static String KEY_STORE_PASS_PHRASE = "changeit";
    private static String TRUST_STORE_PATH = "keystore/truststore-client.jks";
    private static String TRUST_STORE_PASS_PHRASE = "changeit";

    public static OkHttpClient getSafeOkHttpClient() {
        try {
            //Key-Store
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(null, null);

            //Trust-Store
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            try (InputStream trustStoreInputStream = OkHttpClientFactory.class.getClassLoader().getResourceAsStream(TRUST_STORE_PATH)) {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(trustStoreInputStream, TRUST_STORE_PASS_PHRASE.toCharArray());
                trustManagerFactory.init(trustStore);
            }

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

            // Default HostnameVerifier is okhttp3.internal.tls.OKHostnameVerifier
            return new OkHttpClient.Builder().sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagerFactory.getTrustManagers()[0]).build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static OkHttpClient getSafeOkHttpClientWithClientAuth() {
        try {
            //Key-Store
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            try (InputStream trustStoreInputStream = OkHttpClientFactory.class.getClassLoader().getResourceAsStream(KEY_STORE_PATH)) {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(trustStoreInputStream, KEY_STORE_PASS_PHRASE.toCharArray());
                keyManagerFactory.init(keyStore, KEY_STORE_PASS_PHRASE.toCharArray());
            }

            //Trust-Store
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            try (InputStream trustStoreInputStream = OkHttpClientFactory.class.getClassLoader().getResourceAsStream(TRUST_STORE_PATH)) {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(trustStoreInputStream, TRUST_STORE_PASS_PHRASE.toCharArray());
                trustManagerFactory.init(trustStore);
            }

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

            // Default HostnameVerifier is okhttp3.internal.tls.OKHostnameVerifier
            return new OkHttpClient.Builder().sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagerFactory.getTrustManagers()[0]).build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }};

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create a ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // Build OkHttpClient
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
            return builder.build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static OkHttpClient getUnsafeOkHttpClientWithClientAuth() {
        try {
            // Key-Store
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            try (InputStream trustStoreInputStream = OkHttpClientFactory.class.getClassLoader().getResourceAsStream(KEY_STORE_PATH)) {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(trustStoreInputStream, KEY_STORE_PASS_PHRASE.toCharArray());
                keyManagerFactory.init(keyStore, KEY_STORE_PASS_PHRASE.toCharArray());
            }

            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{ new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }};

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustAllCerts, new java.security.SecureRandom());

            // Create a ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // Build OkHttpClient
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
            return builder.build();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
