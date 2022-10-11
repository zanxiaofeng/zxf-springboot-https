package zxf.springboot.https.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        System.setProperty("javax.net.debug", "all");
        System.setProperty("java.security.debug", "all");
        SpringApplication.run(Application.class, args);
    }
}
