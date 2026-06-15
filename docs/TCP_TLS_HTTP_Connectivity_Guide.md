# TCP / TLS / HTTP 三层联通性测试指南

> 从网络层到应用层，系统梳理 Java 环境下各层联通性测试的方法、工具与常见陷阱。

---

## 一、TCP 层联通性测试

TCP 层测试验证目标主机的指定端口是否开放、进程是否存活、网络路由是否可达。这是所有上层测试的基础。

### 1.1 基础探测方法

**Java：**

```java
public boolean tcpCheck(String host, int port, int timeoutMs) {
    try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        return true;
    } catch (IOException e) {
        return false;
    }
}
```

**命令行：**

```bash
# nc - 最常用
nc -z -w 3 host port

# Bash 内置（零依赖）
timeout 3 bash -c 'cat < /dev/null > /dev/tcp/host/port'

# nmap - 服务识别
nmap -Pn -p 443 --open host
```

**能验证什么：** 进程是否存活、防火墙是否放行、端口是否监听。  
**局限性：** 无法发现服务假死（如 JVM OOM 后端口仍在但不再响应）。

### 1.2 影响因素：DNS 解析与地址选择

`new Socket(String, int)` 的内部实现会先解析 DNS，且**IPv6 地址（AAAA 记录）优先于 IPv4（A 记录）**：

```java
InetAddress[] addrs = InetAddress.getAllByName(host);
// 返回顺序：IPv6 优先，然后 IPv4

for (InetAddress addr : addrs) {
    try {
        new Socket(addr, port);  // 默认超时 = 0（无限等待！）
        break;
    } catch (IOException e) {
        // 尝试下一个地址
    }
}
```

| 特性 | `new Socket(String, int)` | `HttpsURLConnection` |
|------|--------------------------|---------------------|
| DNS 返回 IPv6 | 先尝试 IPv6，无限等待 | Happy Eyeballs（RFC 8305），并行尝试 |
| 连接超时 | 无默认值（0 = 无限阻塞） | 内部默认超时（10-30 秒）|
| 地址失败回退 | 串行等待，超时极长 | 快速失败并回退 |

**实际场景：** 若网络不通 IPv6（或 IPv6 路由有问题），`new Socket()` 会在第一个 IPv6 地址上挂起直到 OS 级超时（可能数分钟），而 `HttpsURLConnection` 早已回退到 IPv4 成功连接。

### 1.3 影响因素：代理配置

即使未显式配置代理，JVM 可能通过以下途径自动使用代理：

```java
System.out.println("http.proxyHost=" + System.getProperty("http.proxyHost"));
System.out.println("https.proxyHost=" + System.getProperty("https.proxyHost"));
System.out.println(ProxySelector.getDefault()
    .select(new URI("https://" + host)));
```

- **HTTPS 请求**：自动读取系统代理，通过 `CONNECT` 隧道连接目标
- **裸 TCP Socket**：直连目标，**绕过代理**，可能被企业防火墙拦截

**显式使用代理（裸 Socket 走 HTTP 代理隧道）：**

```java
Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxyHost", 8080));
Socket socket = new Socket(proxy);
socket.connect(new InetSocketAddress(targetHost, targetPort), timeout);
```

### 1.4 TCP 层测试要点

| 要点 | 说明 |
|------|------|
| **必须显式设置超时** | `socket.connect(addr, timeout)`，否则默认无限阻塞 |
| **注意 IPv6 回退** | 不通 IPv6 的环境应设置较短超时让 fallback 生效 |
| **代理环境需显式处理** | 裸 Socket 不走系统代理，需手动创建 `Proxy` 对象 |
| **443 端口特殊** | CDN / LB 可能要求 TLS，裸 TCP 连上后被重置（见第三章） |

---

## 二、TLS 层联通性测试

TLS 层测试在 TCP 连接建立后，验证 TLS 握手能否成功完成。这是 HTTPS 服务健康检查的关键环节。

### 2.1 基础探测方法

**Java：**

```java
public boolean tlsCheck(String host, int port, int timeoutMs) {
    try {
        SSLSocketFactory factory = (SSLSocketFactory)
            SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.startHandshake();

            // 可选：证书验证
            SSLSession session = socket.getSession();
            X509Certificate cert = (X509Certificate)
                session.getPeerCertificates()[0];
            cert.checkValidity();  // 验证未过期
            return true;
        }
    } catch (IOException | CertificateException e) {
        return false;
    }
}
```

**命令行：**

```bash
# TLS 握手 + SNI + 证书链
openssl s_client -connect host:port -servername host </dev/null

# 快速检查（只看握手是否成功）
echo | openssl s_client -connect host:port 2>/dev/null | grep "Verify return code"
```

**能验证什么：** TCP 连通 + TLS 协议协商 + 证书有效性 + SNI 路由。  
**与 TCP 测试的区别：** 发送 TLS Client Hello，服务端识别为合法 TLS 流量，不会触发空闲超时重置。

### 2.2 影响因素：SNI（Server Name Indication）

Cloudflare、AWS CloudFront 等 CDN 使用 **Anycast + 共享 IP**，成千上万域名共享同一 IP。CDN 边缘节点依赖 TLS SNI 扩展识别目标域名。

```
客户端                              CDN Edge
  | ---- Client Hello (无 SNI) ----> |  ← 无法识别目标域名
  |                                  |
  | <---- Alert/Handshake Failure --|  ← 断开或返回默认证书

客户端                              CDN Edge
  | ---- Client Hello (含 SNI=example.com) --> |  ← 查找到对应源站配置
  | <---- Server Hello + 正确证书 --------------|  ← 正常握手
```

**验证命令：**

```bash
# 无 SNI，可能失败
openssl s_client -connect <ip>:443

# 有 SNI，成功
openssl s_client -connect <ip>:443 -servername example.com
```

> Java `SSLSocket` 在 `createSocket(host, port)` 时会**自动携带 SNI**，无需手动设置。

### 2.3 影响因素：中间安全设备

企业环境中，防火墙 / DLP 设备对 HTTPS 流量进行 SSL Inspection（解密检查）：

| | 实际连接目标 | 证书 |
|--|-------------|------|
| 浏览器 / HTTPS 客户端 | 中间安全设备 | 企业自签名证书 |
| 裸 TCP Socket | 目标服务器 | 无 TLS 握手 |
| `SSLSocket` | 目标服务器 | 真实服务器证书 |

**影响：** 中间设备可能只允许特定 TLS 版本 / 密码套件，或要求客户端证书。`SSLSocket` 使用默认配置可能握手失败，而浏览器因内置了企业根证书而通过。

**如何识别：** 在 TcpDebug Phase 3 的输出中检查证书签发者：

```
Cert: CN=MyCompany-Proxy-CA, O=My Company
      ^^^^^^^^^^^^^^^^^^^^^^ 企业自签名证书，非目标网站证书
```

或通过 openssl：

```bash
openssl s_client -connect host:port -showcerts </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer
# issuer 显示企业 CA 而非正规 CA（DigiCert/Let's Encrypt 等）
```

### 2.4 TLS 层测试要点

| 要点 | 说明 |
|------|------|
| **SSLSocket 自动携带 SNI** | 使用 `createSocket(host, port)` 而非 IP 地址 |
| **证书验证可选** | `cert.checkValidity()` 验证过期，但不验证域名信任链 |
| **中间设备可能干扰** | 企业环境注意观察证书签发者是否为目标网站 |
| **TLS 版本兼容性** | 服务端可能仅接受 TLS 1.2+，旧版 JDK 默认 TLS 1.0 会失败 |

---

## 三、HTTP 层联通性测试

HTTP 层测试在 TCP + TLS（如为 HTTPS）建立后，验证服务端是否能正确解析并响应 HTTP 请求。这是最接近真实业务流量的测试层次。

### 3.1 基础探测方法

**Java（最小请求）：**

```java
public boolean httpCheck(String host, int port, int timeoutMs) {
    try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);

        // 发送最小 HTTP 请求
        socket.getOutputStream().write(
            "GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();

        // 读取响应首行
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String line = reader.readLine();

        // 收到 HTTP/1.x 开头的响应即说明是 HTTP 服务
        return line != null && line.startsWith("HTTP/");
    } catch (IOException e) {
        return false;
    }
}
```

**命令行：**

```bash
# 基础探测（2xx/3xx/4xx 都算 HTTP 服务存活）
curl -sf -o /dev/null http://host:port/ || echo "DOWN"

# HEAD 请求（更轻量，只取响应头）
curl -sfI -o /dev/null http://host:port/ || echo "DOWN"

# HTTPS + 忽略证书
curl -sfk -o /dev/null https://host:port/ || echo "DOWN"
```

**能验证什么：** 服务是否接受 HTTP 协议、是否返回有效 HTTP 响应。  
**与 TLS 测试的区别：** 验证的是应用层协议处理能力，不只是加密通道。

### 3.2 影响因素：服务端协议检测

现代 CDN / 负载均衡器在 443 端口的行为：

```
客户端                        CDN Edge / LB
  | ---- SYN ----------------> |
  | <--- SYN-ACK --------------|
  | ---- ACK ----------------> |  ← TCP 三次握手完成
  |                             |
  | [无后续数据 / 非 TLS 数据]   |
  |                             |
  | <--- RST ------------------|  ← 5-10 秒后主动重置
```

443 端口的服务器期望 TLS Client Hello，若收到的是裸 TCP 或 HTTP 明文，可能：
- **直接 RST**：CDN 将非 TLS 流量视为异常
- **返回 400 Bad Request**：Nginx 等反向代理检测到非 TLS 流量
- **重定向到 HTTPS**：`Location: https://...`

### 3.3 HTTP 层测试要点

| 要点 | 说明 |
|------|------|
| **HTTP 1.0 vs 1.1** | `HTTP/1.0` 不需要 `Host` 头，最小请求更简洁 |
| **响应码解读** | 200/302/400/404 都表示 HTTP 服务活着，不要只认 200 |
| **443 端口注意** | 通过 HTTPS 访问时先完成 TLS 握手，再发 HTTP 请求 |
| **与 curl 对齐** | `curl -v` 可查看完整交互过程，便于比对 |

---

## 四、综合诊断工具：TcpDebug

以下工具将 TCP / TLS / HTTP 三层测试整合为一条诊断流水线，一次性排查全部影响因素。

```java
import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.security.cert.*;

/**
 * TcpDebug - TCP / TLS / HTTP 三层联通性诊断工具
 *
 * 用法: java TcpDebug <host> [port] [timeoutMs]
 * 示例: java TcpDebug example.com 443 5000
 */
public class TcpDebug {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java TcpDebug <host> [port] [timeoutMs]");
            System.out.println("  host:      目标主机名或 IP");
            System.out.println("  port:      目标端口（默认 443）");
            System.out.println("  timeoutMs: 连接超时毫秒（默认 5000）");
            System.exit(1);
        }

        String host = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 443;
        int timeout = args.length > 2 ? Integer.parseInt(args[2]) : 5000;

        System.out.println("========================================");
        System.out.printf("Target: %s:%d (timeout=%dms)%n", host, port, timeout);
        System.out.println("========================================\n");

        phase1Dns(host);
        phase2Tcp(host, port, timeout);
        phase3Tls(host, port, timeout);
        phase4Http(host, port, timeout);
        phase5Proxy(host);
    }

    /** Phase 1: DNS 解析 */
    static void phase1Dns(String host) {
        System.out.println("--- Phase 1: DNS Resolution ---");
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (int i = 0; i < addrs.length; i++) {
                System.out.printf("  [%d] %s (IPv6=%b)%n",
                    i, addrs[i], addrs[i] instanceof Inet6Address);
            }
            if (addrs.length > 0 && addrs[0] instanceof Inet6Address) {
                System.out.println("  [WARN] IPv6 first. Unreachable IPv6 causes infinite block with default timeout=0.");
            }
        } catch (UnknownHostException e) {
            System.out.println("  [FAIL] " + e.getMessage());
        }
        System.out.println();
    }

    /** Phase 2: TCP Connectivity */
    static void phase2Tcp(String host, int port, int timeout) {
        System.out.println("--- Phase 2: TCP Connectivity ---");
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                long start = System.currentTimeMillis();
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(addr, port), timeout);
                    long cost = System.currentTimeMillis() - start;
                    System.out.printf("  [OK]   %s in %dms%n", addr, cost);

                    s.setSoTimeout(timeout);
                    try {
                        int b = s.getInputStream().read();
                        if (b == -1) {
                            System.out.println("       Server closed immediately (EOF)");
                        } else {
                            System.out.println("       Server sent data: " + b);
                        }
                    } catch (SocketException se) {
                        System.out.println("  [WARN] RST after TCP establish -> CDN TLS idle timeout (Ch.3.2)");
                    }
                } catch (IOException e) {
                    System.out.printf("  [FAIL] %s | %s%n", addr, e.getMessage());
                }
            }
        } catch (UnknownHostException e) {
            System.out.println("  [SKIP] DNS failed");
        }
        System.out.println();
    }

    /** Phase 3: TLS Handshake */
    static void phase3Tls(String host, int port, int timeout) {
        System.out.println("--- Phase 3: TLS Handshake ---");
        try {
            SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket s = (SSLSocket) f.createSocket()) {
                s.connect(new InetSocketAddress(host, port), timeout);
                s.setSoTimeout(timeout);

                long start = System.currentTimeMillis();
                s.startHandshake();
                long cost = System.currentTimeMillis() - start;

                SSLSession sess = s.getSession();
                System.out.printf("  [OK]   %dms | %s | %s%n",
                    cost, sess.getProtocol(), sess.getCipherSuite());

                X509Certificate cert = (X509Certificate) sess.getPeerCertificates()[0];
                System.out.printf("       Cert: %s%n", cert.getSubjectX500Principal().getName());
                try {
                    cert.checkValidity();
                    System.out.println("       Cert: VALID");
                } catch (CertificateExpiredException e) {
                    System.out.println("  [WARN] Cert EXPIRED");
                }
            }
        } catch (Exception e) {
            System.out.printf("  [FAIL] %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            System.out.println("         If Phase 2 OK: check SNI (Ch.2.2) or SSL Inspection (Ch.2.3)");
        }
        System.out.println();
    }

    /** Phase 4: HTTP Probe */
    static void phase4Http(String host, int port, int timeout) {
        System.out.println("--- Phase 4: HTTP Probe ---");
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeout);
            s.setSoTimeout(timeout);
            s.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".getBytes());
            s.getOutputStream().flush();

            BufferedReader r = new BufferedReader(
                new InputStreamReader(s.getInputStream()));
            String line = r.readLine();

            if (line != null && line.startsWith("HTTP/")) {
                System.out.println("  [OK]   " + line);
            } else if (line != null) {
                System.out.println("  [WARN] Non-HTTP: " + line);
            } else {
                System.out.println("  [WARN] EOF before response");
            }
        } catch (Exception e) {
            System.out.printf("  [FAIL] %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
        }
        System.out.println();
    }

    /** Phase 5: Proxy Detection */
    static void phase5Proxy(String host) {
        System.out.println("--- Phase 5: Proxy Detection ---");
        System.out.println("  http.proxyHost:  " + System.getProperty("http.proxyHost", "(not set)"));
        System.out.println("  https.proxyHost: " + System.getProperty("https.proxyHost", "(not set)"));
        try {
            ProxySelector ps = ProxySelector.getDefault();
            if (ps != null) {
                var proxies = ps.select(new URI("https://" + host));
                System.out.println("  ProxySelector:   " + proxies);
                if (proxies.size() == 1 && proxies.get(0).type() != Proxy.Type.DIRECT) {
                    System.out.println("  [WARN] Proxy active. Raw Socket bypasses it (Ch.1.3).");
                }
            }
        } catch (Exception e) {
            System.out.println("  Proxy check failed: " + e.getMessage());
        }
        System.out.println();
    }
}
```

### 输出解读

| 模式 | Phase 2 (TCP) | Phase 3 (TLS) | Phase 4 (HTTP) | 根因定位 |
|------|--------------|---------------|----------------|---------|
| IPv6 不通 | 部分 timeout | 通过 | 通过 | Ch.1.2 DNS / 设置 timeout |
| CDN TLS 超时 | OK 后被 RST | 通过 | EOF / RST | Ch.3.2 协议检测 / 443 端口正常现象 |
| SNI 缺失 | OK | 失败 | 未执行 | Ch.2.2 使用 SSLSocket |
| 代理绕过 | 失败 | 通过 | 通过 | Ch.1.3 代理 / 检查 Phase 5 |
| 纯 TCP 服务 | OK | 失败 | 非 HTTP 响应 | 目标不是 TLS/HTTP 服务 |
| 全通 | OK | OK | OK（明文 HTTP 端口）| 三层均正常 |

> **注意：** Phase 4 使用裸 Socket 发送 HTTP 明文。在 **443 端口（HTTPS）上 Phase 4 失败是预期现象**，因为服务端期望 TLS 握手而非 HTTP 明文。判断 HTTP 层健康应看 Phase 3（TLS 通过后，HTTP 层通常也正常）。Phase 4 主要用于检测**明文 HTTP 服务**（如 80 端口或内部服务端口）。 |

---

## 五、方案选型

### 5.1 按测试层次选择

| 你要验证什么 | 测试层次 | 推荐方式 |
|-------------|---------|----------|
| 进程是否存活、端口是否开放 | TCP | `nc -z` / `Socket.connect()` |
| HTTPS 通道是否正常 | TLS | `openssl s_client` / `SSLSocket` |
| HTTP 服务是否响应 | HTTP | `curl` / `GET / HTTP/1.0` |
| 完整链路一次性排查 | 全部 | **TcpDebug** |

### 5.2 按环境选择

| 环境 | 推荐工具 |
|------|----------|
| Linux 命令行快速测试 | `nc` / `curl` / `openssl` |
| 零依赖（无 nc/curl）| Bash `/dev/tcp` |
| Java 代码集成 | TcpDebug 各 phase 方法 |
| Kubernetes | 原生 `tcpSocket` / `httpGet` probe |
| 持续集成/自动化 | Java 单元测试调用 TcpDebug |

### 5.3 分层测试策略

生产环境建议按层次递进，而非每次都做全量测试：

```
健康检查频率
    |
    |-- 5s  ---- TCP Socket.connect() -------- L1 存活探测（低成本）
    |              |
    |              +-- 失败 -> 报警，服务可能崩溃
    |              +-- 成功 -> 继续
    |
    |-- 10s ---- TLS Handshake --------------- L2 通道探测（中成本）
    |              |
    |              +-- 失败 -> 证书过期 / TLS 配置错误
    |              +-- 成功 -> 继续
    |
    +-- 30s ---- HTTP GET /ready ------------- L3 业务探测（高成本）
                   |
                   +-- 失败 -> 依赖项异常（DB/缓存）
                   +-- 成功 -> 服务完全健康
```

| 层级 | 方法 | 频率 | 成本 | 目的 |
|------|------|------|------|------|
| L1 | TCP `connect()` | 5s | 极低 | 进程存活 |
| L2 | TLS `startHandshake()` | 10s | 低 | 证书/加密通道正常 |
| L3 | HTTP `GET /ready` | 30s | 中 | 业务依赖健康 |

---

## 六、各层影响因素速查表

| 影响因素 | 影响层次 | 现象 | 应对 |
|---------|---------|------|------|
| IPv6 优先 + 不可达 | TCP | `new Socket()` 无限阻塞 | 显式设置 timeout |
| 系统代理 | TCP | Socket 直连失败，HTTPS 正常 | 检查 `ProxySelector` |
| CDN TLS 空闲超时 | HTTP | TCP 连上后被 RST | 正常现象，改用 TLS 探测 |
| SNI 缺失 | TLS | 握手失败 | `SSLSocket` 自动携带 SNI |
| SSL Inspection | TLS | 证书签发者非目标网站 | 安装企业根证书到 truststore |
| 443 端口协议检测 | HTTP | 非 TLS 流量被拒绝 | 这是预期行为 |
