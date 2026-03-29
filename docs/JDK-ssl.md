获取 JDK 支持的 SSL 密码套件列表，有编程和配置两种方式。

1. 编程方式获取

在 Java 代码中，可以使用 SSLServerSocketFactory 类直接获取：

```java
import javax.net.ssl.SSLServerSocketFactory;
import java.util.Arrays;

public class JDKCipherList {
    public static void main(String[] args) {
        SSLServerSocketFactory factory = 
            (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        
        // 获取 JDK 支持的所有密码套件
        String[] supported = factory.getSupportedCipherSuites();
        System.out.println("支持的套件总数: " + supported.length);
        System.out.println("支持的套件: " + Arrays.toString(supported));
        
        // 获取 JDK 默认启用的密码套件
        String[] defaults = factory.getDefaultCipherSuites();
        System.out.println("默认启用的套件: " + Arrays.toString(defaults));
    }
}
```

关键 API 说明：

· getSupportedCipherSuites()：返回 JDK 支持的所有密码套件
· getDefaultCipherSuites()：返回 JDK 默认启用的套件（仅包含满足最低安全要求的套件）

对于 SSLSocket 或 SSLServerSocket 实例，还可以使用 getEnabledCipherSuites() 获取当前实际启用的套件。

2. 配置文件方式

JDK 通过安全配置文件控制密码套件的全局禁用规则，文件位于：

```
$JAVA_HOME/conf/security/java.security
```

关键配置项是 jdk.tls.disabledAlgorithms：

```properties
jdk.tls.disabledAlgorithms=SSLv3, TLSv1, TLSv1.1, RC4, MD5withRSA, \
    DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, DESede_CBC, \
    AES_256_CBC, RSA keySize < 2048, DHE keySize < 2048
```

重要说明：

· 被此参数禁用的算法不会出现在 getSupportedCipherSuites() 返回列表中
· 修改此文件后，所有使用该 JDK 的应用都会受影响
· 如需为单个应用覆盖配置，可使用 JVM 参数：
  ```bash
  java -Djava.security.properties==/path/to/custom.properties MyApp
  ```
（使用 == 会完全覆盖系统配置）

3. 通过系统属性指定默认套件

JDK 8u261 及更高版本支持通过系统属性设置客户端/服务端的默认密码套件：

· jdk.tls.client.cipherSuites：客户端默认启用的套件
· jdk.tls.server.cipherSuites：服务端默认启用的套件

使用示例：

```bash
java -Djdk.tls.client.cipherSuites="TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" MyApp
```

4. 各方式对比总结

方式 获取内容 适用场景
getSupportedCipherSuites() JDK 全部可用套件（受算法限制策略影响） 了解 JDK 能力上限
getDefaultCipherSuites() JDK 默认启用的安全套件 了解默认安全配置
java.security 文件 查看被全局禁用的弱算法 安全审计、合规检查
系统属性 自定义默认套件 应用级别覆盖默认配置

注意：不同 JDK 版本和提供商（Oracle JDK、OpenJDK、IBM JDK）的默认套件列表可能不同，且随着安全策略更新会发生变化。如需精确了解生产环境的配置，建议直接在目标 JDK 上运行上述代码验证。