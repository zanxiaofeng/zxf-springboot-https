获取 JDK 支持的 SSL Cipher List 有以下几种方式：

1. 使用 `keytool` 命令（JDK 自带）

```bash
# 查看 JDK 默认的 SSL 配置
keytool -printcert -sslserver example.com:443

# 查看 JDK 支持的 cipher suites（通过系统属性）
java -Djavax.net.debug=ssl:handshake -version 2>&1 | head -20
```

2. 使用 Java 代码获取

```java
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLContext;
import java.util.Arrays;

public class CipherListDemo {
    
    public static void main(String[] args) throws Exception {
        // 方法1: 通过 SSLServerSocketFactory 获取默认支持的 cipher suites
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        String[] serverDefaultCiphers = serverFactory.getDefaultCipherSuites();
        String[] serverSupportedCiphers = serverFactory.getSupportedCipherSuites();
        
        System.out.println("=== SSLServerSocketFactory 默认 Cipher Suites ===");
        Arrays.stream(serverDefaultCiphers).forEach(System.out::println);
        
        System.out.println(" === SSLServerSocketFactory 所有支持的 Cipher Suites ===");
        Arrays.stream(serverSupportedCiphers).forEach(System.out::println);
        
        // 方法2: 通过 SSLSocketFactory 获取
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        String[] socketDefaultCiphers = socketFactory.getDefaultCipherSuites();
        String[] socketSupportedCiphers = socketFactory.getSupportedCipherSuites();
        
        System.out.println(" === SSLSocketFactory 默认 Cipher Suites ===");
        Arrays.stream(socketDefaultCiphers).forEach(System.out::println);
        
        System.out.println(" === SSLSocketFactory 所有支持的 Cipher Suites ===");
        Arrays.stream(socketSupportedCiphers).forEach(System.out::println);
        
        // 方法3: 通过 SSLParameters 获取（JDK 11+ 推荐）
        SSLContext sslContext = SSLContext.getDefault();
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        
        System.out.println(" === SSLParameters 默认 Cipher Suites ===");
        Arrays.stream(sslParams.getCipherSuites()).forEach(System.out::println);
        
        // 方法4: 获取 SSLParameters 支持的所有协议版本
        System.out.println(" === 支持的协议版本 ===");
        Arrays.stream(sslParams.getProtocols()).forEach(System.out::println);
    }
}
```

3. 使用 Spring Boot 获取（结合你的技术栈）

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.net.ssl.SSLServerSocketFactory;
import java.util.Arrays;
import java.util.List;

@RestController
public class CipherInfoController {
    
    @GetMapping("/api/ciphers")
    public CipherInfo getCipherInfo() {
        SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        
        return CipherInfo.builder()
            .defaultCiphers(Arrays.asList(factory.getDefaultCipherSuites()))
            .supportedCiphers(Arrays.asList(factory.getSupportedCipherSuites()))
            .jdkVersion(System.getProperty("java.version"))
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CipherInfo {
        private List<String> defaultCiphers;
        private List<String> supportedCiphers;
        private String jdkVersion;
    }
}
```

4. 通过系统属性启用调试输出

```bash
# 启动应用时添加 JVM 参数，查看 SSL 握手详情（包含 cipher 协商过程）
java -Djavax.net.debug=ssl:handshake:verbose -jar your-app.jar

# 或查看所有 SSL 调试信息
java -Djavax.net.debug=all -jar your-app.jar
```

5. 使用 `security` 工具查看 Provider 信息

```bash
# 列出所有安全 provider
java -version # 确认 JDK 版本

# 编写简单程序查看 Provider 详情
cat > ListProviders.java << 'EOF'
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;

public class ListProviders {
    public static void main(String[] args) {
        Provider[] providers = Security.getProviders();
        for (Provider p : providers) {
            System.out.println("Provider: " + p.getName() + " " + p.getVersion());
            // 查看 SSL 相关属性
            p.stringPropertyNames().stream()
                .filter(k -> k.toLowerCase().contains("ssl") || k.toLowerCase().contains("cipher"))
                .forEach(k -> System.out.println(" " + k + " = " + p.getProperty(k)));
        }
    }
}
EOF
java ListProviders.java
```

不同 JDK 版本的差异

JDK 版本 默认启用 TLSv1.3 默认 Cipher 特点
JDK 8 否（需显式启用） 以 TLSv1.2 cipher 为主
JDK 11 是 支持 TLSv1.3，默认 cipher 更严格
JDK 17+ 是 禁用弱 cipher，优先 AEAD 算法
JDK 21+ 是 进一步收紧安全策略

配置自定义 Cipher List（Spring Boot）

在你的 `application.yml` 或 `application.properties` 中：

```yaml
server:
  ssl:
    enabled: true
    # 显式指定允许的 cipher suites
    ciphers: TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384
    # 指定协议版本
    protocol: TLS
    enabled-protocols: TLSv1.2,TLSv1.3
```

或者通过 Java 代码配置（Spring Boot 3.x）：

```java
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SslConfig {
    
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> sslCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            // 获取并打印当前 connector 的 SSL 配置
            System.out.println("SSL Protocol: " + connector.getProtocol());
            // 可通过 connector 的 SSLHostConfig 进一步定制 cipher
        });
    }
}
```

快速验证脚本

```bash
#!/bin/bash
# 保存为 check-ciphers.sh
cat > /tmp/ListCiphers.java << 'JAVA'
import javax.net.ssl.*;
import java.util.Arrays;

public class ListCiphers {
    public static void main(String[] args) throws Exception {
        System.out.println("JDK Version: " + System.getProperty("java.version"));
        System.out.println("Java Home: " + System.getProperty("java.home"));
        
        SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        
        System.out.println(" === 默认启用的 Cipher Suites (" + factory.getDefaultCipherSuites().length + "个) ===");
        Arrays.stream(factory.getDefaultCipherSuites()).forEach(System.out::println);
        
        System.out.println(" === 所有支持的 Cipher Suites (" + factory.getSupportedCipherSuites().length + "个) ===");
        Arrays.stream(factory.getSupportedCipherSuites()).forEach(System.out::println);
        
        // TLSv1.3 特定 cipher
        System.out.println(" === TLSv1.3 标准 Cipher Suites ===");
        String[] tls13Ciphers = {
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256", 
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_128_CCM_SHA256",
            "TLS_AES_128_CCM_8_SHA256"
        };
        Arrays.stream(tls13Ciphers).forEach(c -> 
            System.out.println(c + (Arrays.asList(factory.getSupportedCipherSuites()).contains(c) ? " [支持]" : " [不支持]"))
        );
    }
}
JAVA

java /tmp/ListCiphers.java
```

运行 `bash check-ciphers.sh` 即可查看当前 JDK 的所有 cipher 支持情况。

如果你需要针对特定 JDK 版本（如 JDK 21）或特定场景（如 Spring Boot 4.0）的详细配置，可以告诉我，我帮你生成更精确的代码。