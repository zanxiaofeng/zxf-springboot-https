package zxf.https.client.apache;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class ApacheClientApp {
    public static void main(String[] args) {
        //System.setProperty("javax.net.debug", "all");
        //System.setProperty("java.security.debug", "all");
        //System.setProperty("org.apache.commons.logging.diagnostics.dest", "STDOUT");

        try {
            System.out.println("*****Https Server with safe ApacheHttpClient:");
            try (CloseableHttpClient httpClient = ApacheClientFactory.getSafeHttpClient()) {
                HttpGet request = new HttpGet("https://localhost:8080/home");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    System.out.println(IOUtils.readLines(response.getEntity().getContent()));
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("\n*****Https Server with unsafe ApacheHttpClient:");
            try (CloseableHttpClient httpClient = ApacheClientFactory.getUnsafeHttpClient()) {
                HttpGet request = new HttpGet("https://localhost:8080/home");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    System.out.println(IOUtils.readLines(response.getEntity().getContent()));
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("\n*****Https Server with default ApacheHttpClient:");
            try (CloseableHttpClient httpClient = HttpClients.custom().build()) {
                HttpGet request = new HttpGet("https://localhost:8080/home");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    System.out.println(IOUtils.readLines(response.getEntity().getContent()));
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("*****Https Server(Client Auth) with safe ApacheHttpClient:");
            try (CloseableHttpClient httpClient = ApacheClientFactory.getSafeHttpClientWithClientAuth()) {
                HttpGet request = new HttpGet("https://localhost:8082/home");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    System.out.println(IOUtils.readLines(response.getEntity().getContent()));
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("\n*****Https Server(Client Auth) with unsafe ApacheHttpClient:");
            try (CloseableHttpClient httpClient = ApacheClientFactory.getUnsafeOkHttpClientWithClientAuth()) {
                HttpGet request = new HttpGet("https://localhost:8082/home");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    System.out.println(IOUtils.readLines(response.getEntity().getContent()));
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("\n*****Https Server(Client Auth) with default ApacheHttpClient:");
            try (CloseableHttpClient httpClient = HttpClients.custom().build()) {
                HttpGet request = new HttpGet("https://localhost:8082/home");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    System.out.println(IOUtils.readLines(response.getEntity().getContent()));
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }
}
