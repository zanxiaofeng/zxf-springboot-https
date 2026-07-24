# Java HTTPS 通信诊断工具（HttpsTroubleshooter）

一个单文件 Java 工具，覆盖 HTTPS 排障的四个层面：

| 模式 | 功能 |
|---|---|
| `env` | 客户端环境报告：JVM 版本、SSL 系统属性、禁用算法、默认启用协议与 cipher suites、信任库 CA 数量 |
| `diag` | 握手诊断：默认握手协商结果、证书链详情、各 TLS 版本可达性、SNI 影响、ALPN 协商、主机名校验 |
| `scan` | 服务器端枚举：按 TLS 版本枚举服务器支持的全部 cipher suites（并发探测） |
| `mtls` | mTLS 双向证书：探测服务器是否要求客户端证书（need/want/无），并验证客户端证书握手 |

**环境要求**：JDK 11+（ALPN 相关方法需要 JDK 9+），单文件无第三方依赖。

---

## 完整源码

```java
import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Principal;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.*;
import java.util.concurrent.*;

/**
 * HttpsTroubleshooter —— Java HTTPS 通信一站式诊断工具
 *
 * 用法:
 *   java HttpsTroubleshooter env                                # 客户端环境报告
 *   java HttpsTroubleshooter diag <host> [port]                 # 握手诊断(协商结果+证书链+SNI+ALPN)
 *   java HttpsTroubleshooter scan <host> [port]                 # 枚举服务器 TLS 版本与 cipher suites
 *   java HttpsTroubleshooter mtls <host> [port] [ks] [pwd]      # mTLS 双向证书诊断
 *   java HttpsTroubleshooter all  <host> [port]                 # env + diag + scan
 *
 * 适用 JDK 11+（ALPN 相关方法需要 9+）。
 */
public class HttpsTroubleshooter {

    private static final int TIMEOUT_MS = 5000;
    private static final int PARALLELISM = 16;
    private static final String[] PROTOCOLS = {"TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3"};

    // ================= 1. 客户端环境报告 =================

    static void printClientEnv() {
        section("客户端环境 (Client Environment)");

        kv("java.version", System.getProperty("java.version"));
        kv("java.vendor",  System.getProperty("java.vendor"));
        kv("java.home",    System.getProperty("java.home"));
        System.out.println();

        subSection("SSL/TLS 相关系统属性");
        for (String p : new String[]{
                "https.protocols", "jdk.tls.client.protocols",
                "javax.net.ssl.trustStore", "javax.net.ssl.trustStoreType",
                "javax.net.ssl.keyStore", "javax.net.ssl.keyStoreType",
                "jdk.tls.server.cipherSuites", "jdk.tls.client.cipherSuites",
                "jdk.tls.namedGroups", "jdk.certpath.disabledAlgorithms",
                "com.sun.net.ssl.checkRevocation", "ocsp.enable"}) {
            String v = System.getProperty(p);
            kv(p, v != null ? v : "(未设置)");
        }
        System.out.println();

        subSection("安全策略 (java.security)");
        kv("jdk.tls.disabledAlgorithms", Security.getProperty("jdk.tls.disabledAlgorithms"));
        kv("jdk.certpath.disabledAlgorithms", Security.getProperty("jdk.certpath.disabledAlgorithms"));
        kv("jdk.tls.legacyAlgorithms", Security.getProperty("jdk.tls.legacyAlgorithms"));
        System.out.println();

        subSection("默认 SSLContext");
        try {
            SSLContext ctx = SSLContext.getDefault();
            kv("默认协议", ctx.getProtocol());
            SSLParameters dp = ctx.getDefaultSSLParameters();
            kv("默认启用协议", String.join(", ", dp.getProtocols()));
            kv("默认启用套件数", String.valueOf(dp.getCipherSuites().length));
            kv("默认启用套件", String.join("\n      ", dp.getCipherSuites()));

            SSLParameters sp = ctx.getSupportedSSLParameters();
            kv("JVM 支持协议", String.join(", ", sp.getProtocols()));
            kv("JVM 支持套件数", String.valueOf(sp.getCipherSuites().length));
        } catch (Exception e) {
            System.out.println("  获取 SSLContext 失败: " + e);
        }
        System.out.println();

        subSection("默认信任库 CA 数量");
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((java.security.KeyStore) null);
            int count = ((X509TrustManager) tmf.getTrustManagers()[0])
                    .getAcceptedIssuers().length;
            kv("受信 CA 证书数", String.valueOf(count));
        } catch (Exception e) {
            System.out.println("  读取信任库失败: " + e);
        }
    }

    // ================= 2. 握手诊断 =================

    static void diagnose(String host, int port) {
        section("握手诊断 (Handshake Diagnosis): " + host + ":" + port);

        // 2.1 默认配置握手
        subSection("默认握手（使用 JVM 默认协议/套件/信任库）");
        try {
            SSLSocket socket = connect(host, port, null, null, host);
            printNegotiated(socket);
            printCertificateChain(socket.getSession());
            printHostnameVerification(host, socket.getSession());
            socket.close();
        } catch (Exception e) {
            System.out.println("  [失败] " + describeFailure(e));
        }

        // 2.2 各协议版本能否握手
        System.out.println();
        subSection("各协议版本可达性");
        for (String protocol : PROTOCOLS) {
            try (SSLSocket socket = connect(host, port, protocol, null, host)) {
                System.out.printf("  %-9s 支持  -> 协商: %s / %s%n", protocol,
                        socket.getSession().getProtocol(),
                        socket.getSession().getCipherSuite());
            } catch (Exception e) {
                System.out.printf("  %-9s 不支持 (%s)%n", protocol, shortReason(e));
            }
        }

        // 2.3 SNI 影响测试
        System.out.println();
        subSection("SNI 影响测试");
        try (SSLSocket withSni = connect(host, port, null, null, host)) {
            System.out.println("  带 SNI    : 成功 (" + withSni.getSession().getCipherSuite() + ")");
        } catch (Exception e) {
            System.out.println("  带 SNI    : 失败 - " + shortReason(e));
        }
        try (SSLSocket noSni = connect(host, port, null, null, null)) {
            System.out.println("  不带 SNI  : 成功 (" + noSni.getSession().getCipherSuite() + ")");
        } catch (Exception e) {
            System.out.println("  不带 SNI  : 失败 - " + shortReason(e)
                    + "  (服务器强制要求 SNI)");
        }

        // 2.4 ALPN 协商
        System.out.println();
        subSection("ALPN 协商");
        try {
            SSLSocket socket = connect(host, port, null, null, host);
            SSLParameters params = socket.getSSLParameters();
            params.setApplicationProtocols(new String[]{"h2", "http/1.1"});
            socket.setSSLParameters(params);
            socket.startHandshake();
            String alpn = socket.getApplicationProtocol();
            System.out.println("  ALPN 结果: " + (alpn == null || alpn.isEmpty()
                    ? "(服务器未协商 ALPN)" : alpn));
            socket.close();
        } catch (Exception e) {
            System.out.println("  ALPN 测试失败: " + shortReason(e));
        }
    }

    private static void printNegotiated(SSLSocket socket) {
        SSLSession s = socket.getSession();
        kv("协商协议", s.getProtocol());
        kv("协商套件", s.getCipherSuite());
        kv("对端主机", s.getPeerHost() + ":" + s.getPeerPort());
        kv("本地套件启用数", String.valueOf(socket.getEnabledCipherSuites().length));
    }

    private static void printCertificateChain(SSLSession session) throws Exception {
        Certificate[] chain = session.getPeerCertificates();
        System.out.println("  证书链 (" + chain.length + " 张):");
        for (int i = 0; i < chain.length; i++) {
            if (!(chain[i] instanceof X509Certificate c)) continue;
            System.out.printf("    [%d] Subject : %s%n", i, c.getSubjectX500Principal());
            System.out.printf("        Issuer  : %s%n", c.getIssuerX500Principal());
            System.out.printf("        有效期  : %s ~ %s%s%n",
                    c.getNotBefore(), c.getNotAfter(),
                    new Date().after(c.getNotAfter()) ? "  <-- 已过期!" : "");
            System.out.printf("        签名算法: %s, 密钥: %s%n",
                    c.getSigAlgName(), keyInfo(c));
            if (i == 0) {
                try {
                    Collection<List<?>> sans = c.getSubjectAlternativeNames();
                    if (sans != null) {
                        StringBuilder sb = new StringBuilder();
                        for (List<?> san : sans)
                            if (Integer.valueOf(2).equals(san.get(0)))
                                sb.append(san.get(1)).append(" ");
                        System.out.printf("        SAN     : %s%n", sb.toString().trim());
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private static void printHostnameVerification(String host, SSLSession session) {
        try {
            HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session);
            System.out.println("  主机名校验: 通过");
        } catch (Exception e) {
            System.out.println("  主机名校验: 失败! 证书 CN/SAN 与 " + host + " 不匹配");
        }
    }

    // ================= 3. 服务器 cipher 枚举 =================

    static void scan(String host, int port) {
        section("服务器 Cipher Suite 枚举: " + host + ":" + port);
        SSLSocketFactory factory = trustAllFactory(); // 枚举时绕过证书校验
        ExecutorService pool = Executors.newFixedThreadPool(PARALLELISM);
        try {
            for (String protocol : PROTOCOLS) {
                String[] candidates = supportedSuites(factory, protocol);
                if (candidates.length == 0) continue;

                List<CompletableFuture<Boolean>> futures = new ArrayList<>();
                for (String suite : candidates)
                    futures.add(CompletableFuture.supplyAsync(
                            () -> probe(factory, host, port, protocol, suite), pool));

                List<String> accepted = new ArrayList<>();
                for (int i = 0; i < candidates.length; i++)
                    if (futures.get(i).join()) accepted.add(candidates[i]);

                System.out.printf("%n  == %s: 支持 %d / 候选 %d 个套件 ==%n",
                        protocol, accepted.size(), candidates.length);
                accepted.forEach(s -> System.out.println("    " + s));
            }
        } finally {
            pool.shutdown();
        }
    }

    private static String[] supportedSuites(SSLSocketFactory factory, String protocol) {
        try (SSLSocket probe = (SSLSocket) factory.createSocket()) {
            probe.setEnabledProtocols(new String[]{protocol});
            return probe.getEnabledCipherSuites();
        } catch (Exception e) {
            return new String[0];
        }
    }

    private static boolean probe(SSLSocketFactory factory, String host, int port,
                                 String protocol, String suite) {
        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            try (SSLSocket s = (SSLSocket) factory.createSocket(raw, host, port, true)) {
                s.setEnabledProtocols(new String[]{protocol});
                s.setEnabledCipherSuites(new String[]{suite});
                s.setSoTimeout(TIMEOUT_MS);
                s.startHandshake();
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    // ================= 4. mTLS（双向证书）诊断 =================

    /**
     * 探测服务器对客户端证书的要求，并验证客户端证书握手。
     *
     * 判定逻辑：
     *   - 无客户端证书握手成功 且 KeyManager 未被回调 -> 服务器不要求客户端证书
     *   - 无客户端证书握手成功 且 KeyManager 被回调    -> 客户端证书可选 (want)
     *   - 无客户端证书握手失败 且 KeyManager 被回调    -> 强制要求客户端证书 (need)
     *   - 无客户端证书握手失败 且 KeyManager 未被回调  -> 失败与 mTLS 无关
     */
    static void diagnoseMtls(String host, int port, String keyStorePath, String keyStorePassword) {
        section("mTLS 双向证书诊断: " + host + ":" + port);

        // 4.1 不带客户端证书探测
        subSection("探测 1: 不提供客户端证书");
        DetectingKeyManager probeKm = new DetectingKeyManager(null);
        boolean noCertOk = handshakeWithKeyManager(host, port, probeKm);
        System.out.println("  握手结果                 : " + (noCertOk ? "成功" : "失败"));
        System.out.println("  服务器 CertificateRequest: " +
                (probeKm.certificateRequested ? "已收到（服务器请求客户端证书）" : "未收到"));

        // 4.2 判定要求级别
        String requirement;
        if (noCertOk && !probeKm.certificateRequested) requirement = "服务器不校验客户端证书（单向 TLS）";
        else if (noCertOk)                             requirement = "客户端证书可选 (want/optional)";
        else if (probeKm.certificateRequested)         requirement = "服务器强制要求客户端证书 (need/mandatory)";
        else                                           requirement = "握手失败但与 mTLS 无关，先用 diag 排查";
        kv("结论", requirement);

        // 4.3 加载客户端证书并握手
        if (keyStorePath == null) {
            System.out.println("\n  未提供客户端证书，跳过验证。用法: mtls <host> <port> <keystore.p12> <password>");
            return;
        }
        System.out.println();
        subSection("探测 2: 使用客户端证书握手");
        try {
            KeyManager[] kms = loadClientKeyManagers(keyStorePath, keyStorePassword);
            printClientCertInfo(kms);
            DetectingKeyManager km = new DetectingKeyManager(
                    (X509ExtendedKeyManager) kms[0]);
            boolean ok = handshakeWithKeyManager(host, port, km);
            kv("带证书握手", ok ? "成功 —— 客户端证书被服务器接受" : "失败");
            if (!ok) {
                System.out.println("  [提示] 常见原因：客户端证书不在服务器信任 CA 列表中、" +
                        "证书已过期、或 EKU 不含 Client Authentication (1.3.6.1.5.5.7.3.2)");
            }
        } catch (Exception e) {
            System.out.println("  [失败] " + describeFailure(e));
        }
    }

    private static boolean handshakeWithKeyManager(String host, int port,
                                                   X509ExtendedKeyManager km) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[]{km}, trustAllManagers(), new java.security.SecureRandom());
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            try (SSLSocket s = (SSLSocket) ctx.getSocketFactory()
                    .createSocket(raw, host, port, true)) {
                s.setSoTimeout(TIMEOUT_MS);
                s.startHandshake();
                return true;
            }
        } catch (Exception e) {
            System.out.println("  (" + shortReason(e) + ")");
            return false;
        }
    }

    /** 加载客户端 keystore（支持 PKCS12 / JKS，按扩展名推断） */
    private static KeyManager[] loadClientKeyManagers(String path, String password)
            throws Exception {
        String type = path.toLowerCase().endsWith(".jks") ? "JKS" : "PKCS12";
        java.security.KeyStore ks = java.security.KeyStore.getInstance(type);
        try (InputStream in = new FileInputStream(path)) {
            ks.load(in, password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        return kmf.getKeyManagers();
    }

    private static void printClientCertInfo(KeyManager[] kms) {
        if (!(kms[0] instanceof X509KeyManager xkm)) return;
        String[] aliases = xkm.getClientAliases("RSA", null);
        if (aliases == null || aliases.length == 0)
            aliases = xkm.getClientAliases("EC", null);
        if (aliases == null) return;
        for (String alias : aliases) {
            X509Certificate[] chain = xkm.getCertificateChain(alias);
            if (chain == null || chain.length == 0) continue;
            X509Certificate leaf = chain[0];
            System.out.println("  客户端证书 (alias=" + alias + "):");
            System.out.println("    Subject : " + leaf.getSubjectX500Principal());
            System.out.println("    Issuer  : " + leaf.getIssuerX500Principal());
            System.out.printf("    有效期  : %s ~ %s%s%n",
                    leaf.getNotBefore(), leaf.getNotAfter(),
                    new Date().after(leaf.getNotAfter()) ? "  <-- 已过期!" : "");
            try {
                List<String> eku = leaf.getExtendedKeyUsage();
                System.out.println("    EKU     : " + (eku == null ? "(无限制)" : eku)
                        + (eku != null && !eku.contains("1.3.6.1.5.5.7.3.2")
                           ? "  <-- 缺少 ClientAuthentication!" : ""));
            } catch (Exception ignored) {}
            return; // 只展示第一个可用别名
        }
    }

    private static TrustManager[] trustAllManagers() {
        return new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
    }

    /**
     * 包装型 KeyManager：服务器发送 CertificateRequest 时 JDK 会回调
     * chooseClientAlias/chooseEngineClientAlias，借此探测 mTLS 要求。
     * delegate 为 null 表示客户端不持有任何证书。
     */
    static class DetectingKeyManager extends X509ExtendedKeyManager {
        volatile boolean certificateRequested = false;
        private final X509ExtendedKeyManager delegate;

        DetectingKeyManager(X509ExtendedKeyManager delegate) { this.delegate = delegate; }

        @Override public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            certificateRequested = true;
            return delegate == null ? null : delegate.chooseClientAlias(keyType, issuers, socket);
        }
        @Override public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            certificateRequested = true;
            return delegate == null ? null : delegate.chooseEngineClientAlias(keyType, issuers, engine);
        }
        @Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return delegate == null ? null : delegate.chooseServerAlias(keyType, issuers, socket);
        }
        @Override public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return delegate == null ? null : delegate.chooseEngineServerAlias(keyType, issuers, engine);
        }
        @Override public String[] getClientAliases(String keyType, Principal[] issuers) {
            return delegate == null ? new String[0] : delegate.getClientAliases(keyType, issuers);
        }
        @Override public String[] getServerAliases(String keyType, Principal[] issuers) {
            return delegate == null ? new String[0] : delegate.getServerAliases(keyType, issuers);
        }
        @Override public java.security.PrivateKey getPrivateKey(String alias) {
            return delegate == null ? null : delegate.getPrivateKey(alias);
        }
        @Override public X509Certificate[] getCertificateChain(String alias) {
            return delegate == null ? null : delegate.getCertificateChain(alias);
        }
    }

    // ================= 基础设施 =================

    private static SSLSocket connect(String host, int port,
                                     String protocol, String cipherSuite,
                                     String sniHost) throws Exception {
        SSLContext ctx = SSLContext.getDefault();
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        SSLSocket socket = (SSLSocket) ctx.getSocketFactory()
                .createSocket(raw, host, port, true);
        if (protocol != null) socket.setEnabledProtocols(new String[]{protocol});
        if (cipherSuite != null) socket.setEnabledCipherSuites(new String[]{cipherSuite});
        SSLParameters params = socket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // 启主机名校验
        params.setServerNames(sniHost == null ? List.of()
                : List.of(new SNIHostName(sniHost)));
        socket.setSSLParameters(params);
        socket.setSoTimeout(TIMEOUT_MS);
        socket.startHandshake();
        return socket;
    }

    private static SSLSocketFactory trustAllFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAllManagers(), new java.security.SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ================= 异常归因（排障提示） =================

    static String describeFailure(Throwable e) {
        String msg = String.valueOf(e.getMessage());
        String chain = fullMessage(e);
        String hint;
        if (chain.contains("PKIX path building failed"))
            hint = "证书链不受信 -> 服务器证书不在客户端 truststore 中，或服务器未发送完整证书链（缺中间 CA）";
        else if (chain.contains("certificate_unknown") || chain.contains("bad_certificate"))
            hint = "证书问题 -> 证书过期/自签名/域名不匹配，检查证书链有效期与 SAN；mTLS 场景下也可能是服务器拒绝了客户端证书";
        else if (chain.contains("handshake_failure"))
            hint = "套件/协议不匹配 -> 客户端与服务器无共同 cipher suite，对比 scan 结果与客户端启用套件";
        else if (chain.contains("protocol_version") || chain.contains("no protocols in common"))
            hint = "TLS 版本不匹配 -> 服务器可能要求 TLS1.2+，检查 -Djdk.tls.client.protocols 与 disabledAlgorithms";
        else if (chain.contains("unrecognized_name"))
            hint = "SNI 问题 -> 服务器虚拟主机需要正确 SNI，或发送了 IP 形式的 SNI";
        else if (chain.contains("certificate_required"))
            hint = "mTLS 问题 -> 服务器强制要求客户端证书 (TLS1.3 certificate_required)，请使用 mtls 模式并提供 keystore";
        else if (chain.contains("Hostname") && chain.contains("not verified"))
            hint = "主机名校验失败 -> 证书 CN/SAN 与访问域名不一致";
        else if (chain.contains("Connection reset") || chain.contains("SocketTimeout"))
            hint = "网络层问题 -> 防火墙/代理/WAF 拦截，或服务器直接拒绝老协议";
        else
            hint = "未归类，请查看完整堆栈";
        return e.getClass().getSimpleName() + ": " + msg + "\n  [提示] " + hint;
    }

    private static String fullMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) sb.append(t.getMessage()).append(" | ");
        return sb.toString();
    }

    private static String shortReason(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null)
                return t.getMessage().length() > 60
                        ? t.getMessage().substring(0, 60) + "..." : t.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    private static String keyInfo(X509Certificate c) {
        var pk = c.getPublicKey();
        if (pk instanceof RSAPublicKey rsa) return "RSA " + rsa.getModulus().bitLength();
        if (pk instanceof ECPublicKey ec)   return "EC " + ec.getParams().getCurve().getField().getFieldSize();
        return pk.getAlgorithm();
    }

    // ================= 输出辅助 & 入口 =================

    private static void section(String t) {
        System.out.println("\n========== " + t + " ==========");
    }
    private static void subSection(String t) { System.out.println("-- " + t); }
    private static void kv(String k, String v) { System.out.printf("  %-32s: %s%n", k, v); }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "all";
        String host = args.length > 1 ? args[1] : "www.baidu.com";
        int port    = args.length > 2 ? Integer.parseInt(args[2]) : 443;
        long start = System.currentTimeMillis();

        switch (mode) {
            case "env"  -> printClientEnv();
            case "diag" -> diagnose(host, port);
            case "scan" -> scan(host, port);
            case "mtls" -> diagnoseMtls(host, port,
                    args.length > 3 ? args[3] : null,        // keystore 路径
                    args.length > 4 ? args[4] : "changeit"); // keystore 密码
            case "all"  -> { printClientEnv(); diagnose(host, port); scan(host, port); }
            default     -> System.out.println(
                    "用法: java HttpsTroubleshooter [env|diag|scan|mtls|all] <host> [port] [keystore] [password]");
        }
        System.out.printf("%n总耗时 %d ms%n", System.currentTimeMillis() - start);
    }
}
```

---

## 用法

```bash
javac HttpsTroubleshooter.java

java HttpsTroubleshooter env                                       # 客户端 JVM/SSL 环境
java HttpsTroubleshooter diag api.example.com 443                  # 握手+证书链+SNI+ALPN
java HttpsTroubleshooter scan api.example.com 443                  # 服务器套件枚举
java HttpsTroubleshooter mtls api.example.com 443                  # 探测服务器 mTLS 要求
java HttpsTroubleshooter mtls api.example.com 443 client.p12 pwd   # 客户端证书验证
java HttpsTroubleshooter all  api.example.com 443                  # env + diag + scan
```

## 排障场景速查

| 症状 | 用哪个命令 | 看哪部分 |
|---|---|---|
| `PKIX path building failed` | `diag` | 证书链是否完整、是否过期；`env` 里 truststore 配置 |
| `handshake_failure` | `diag` + `scan` | 各协议可达性 + 服务器支持套件，与 `env` 中"默认启用套件"取交集 |
| 换域名后失败 | `diag` | SNI 影响测试（带/不带 SNI 对比）、主机名校验 |
| 升级到 JDK 17/21 后连不上老系统 | `env` + `scan` | `jdk.tls.disabledAlgorithms`（TLS1.0/1.1、弱套件是否被禁用） |
| 怀疑需要 HTTP/2 | `diag` | ALPN 协商结果是否为 `h2` |
| 走了代理后异常 | `env` | 代理会替换证书，`受信 CA 数量` + 证书链 Issuer 是否为公司 CA |
| `certificate_required` / `bad_certificate` | `mtls` | 服务器是否强制要求客户端证书、客户端证书 EKU 与有效期 |

## 典型排查流程

1. `java HttpsTroubleshooter env` —— 确认客户端 JVM 的协议/套件/禁用算法基线；
2. `java HttpsTroubleshooter diag api.example.com 443` —— 默认握手是否成功、协商出什么、证书链和主机名是否正确；
3. 若失败，`java HttpsTroubleshooter scan api.example.com` —— 拿到服务器端真实支持集合，与客户端启用集合比对交集，定位是协议层还是套件层断裂；
4. 若涉及双向认证，`java HttpsTroubleshooter mtls api.example.com 443 client.p12 pwd` —— 判定服务器要求级别并验证客户端证书。

## mTLS 判读要点

- **CertificateRequest 检测原理**：JDK 只在收到服务器 CertificateRequest 后才回调客户端 KeyManager，工具通过包装型 `DetectingKeyManager` 捕获该回调，是判断 mTLS 要求最可靠的依据。
- **`want` vs `need` 的区分**：不带证书能握手成功但收到了 CertificateRequest，说明服务器配置为可选校验（如 nginx `ssl_verify_client optional`）——这类场景业务层通常再自行判断，排障时别误以为"不需要证书"。
- **EKU 检查**：客户端证书必须含 `ClientAuthentication (1.3.6.1.5.5.7.3.2)`，工具已自动检查并标出，这是 mTLS 失败的高频原因。
- **探测 2 失败但证书有效**：多半是服务器信任 CA 列表不包含客户端证书签发 CA，可让服务端提供 CertificateRequest 中的 acceptable CA 列表比对。

## 注意事项

- `scan` 与 `mtls` 探测使用 trust-all TrustManager 仅为排除证书因素干扰，**不要**将该模式复制到生产代码。
- 逐套件探测会建立数十次 TCP/TLS 连接；目标有 WAF/限流时把 `PARALLELISM` 调低到 4。
- 审计结果可用 `nmap --script ssl-enum-ciphers` 或 `sslyze` 交叉校验。
