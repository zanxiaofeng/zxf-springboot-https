package zxf.springboot.https.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        System.setProperty("javax.net.debug", "all");
        System.setProperty("java.security.debug", "all");
        SpringApplication.run(Application.class, args);
    }

    @PostConstruct
    public void setupTrustStore() {
        //Don't support classpath based filepath
        System.setProperty("javax.net.ssl.trustStore", "./src/main/resources/keystore/truststore-client.jks");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }
}
