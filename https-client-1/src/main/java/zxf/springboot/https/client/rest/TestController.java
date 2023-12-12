package zxf.springboot.https.client.rest;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/test")
public class TestController {
    @Autowired
    private RestTemplate restTemplateWithTrustStore;

    @Autowired
    private RestTemplate restTemplateWithoutTrustStore;

    @Autowired
    private RestTemplate restTemplateWithKeyStoreAndTrustStore;

    @GetMapping("/trust-client-auth")
    public String testKeyStoreAndTrustStore() {
        StringBuffer stringBuffer = new StringBuffer();
        try {
            stringBuffer.append("Https Server with trust store and client auth:\n");
            stringBuffer.append(restTemplateWithTrustStore.getForObject("https://localhost:8082/home", String.class));
        } catch (Throwable ex) {
            stringBuffer.append(ExceptionUtils.getStackTrace(ex));
        }
        return stringBuffer.toString();
    }

    @GetMapping("/trust")
    public String testWithTrustStore() {
        StringBuffer stringBuffer = new StringBuffer();
        try {
            stringBuffer.append("Https Server with trust store:\n");
            stringBuffer.append(restTemplateWithTrustStore.getForObject("https://localhost:8080/home", String.class));
        } catch (Throwable ex) {
            stringBuffer.append(ExceptionUtils.getStackTrace(ex));
        }
        return stringBuffer.toString();
    }

    @GetMapping("/no-trust")
    public String testWithoutTrustStore() {
        StringBuffer stringBuffer = new StringBuffer();
        try {
            stringBuffer.append("Https Server without trust store:\n");
            stringBuffer.append(restTemplateWithoutTrustStore.getForObject("https://localhost:8080/home", String.class));
        } catch (Throwable ex) {
            stringBuffer.append(ExceptionUtils.getStackTrace(ex));
        }
        return stringBuffer.toString();
    }
}
