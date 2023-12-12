package zxf.springboot.https.client.config;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

@Configuration
public class RestTemplateConfig {
    @Value("${key.store}")
    private Resource keyStore;
    @Value("${key.store.password}")
    private String keyStorePassword;
    @Value("${trust.store}")
    private Resource trustStore;
    @Value("${trust.store.password}")
    private String trustStorePassword;

    @Bean
    public RestTemplate restTemplateWithKeyStoreAndTrustStore() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, UnrecoverableKeyException {
        SSLContext sslContext = new SSLContextBuilder().loadKeyMaterial(keyStore.getURL(), keyStorePassword.toCharArray(), keyStorePassword.toCharArray())
                .loadTrustMaterial(trustStore.getURL(), trustStorePassword.toCharArray()).build();
        HttpClient httpClient = HttpClients.custom().setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext)).build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    @Bean
    public RestTemplate restTemplateWithTrustStore() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(trustStore.getURL(), trustStorePassword.toCharArray()).build();
        HttpClient httpClient = HttpClients.custom().setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext)).build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    @Bean
    public RestTemplate restTemplateWithoutTrustStore() {
        return new RestTemplate();
    }
}
