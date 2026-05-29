Here are the updated methods that enumerate both supported cipher suites and TLS protocols:

1. Nmap (Full TLS + Cipher Enumeration)

```bash
nmap --script ssl-enum-ciphers -p 443 example.com
```

Output includes per-protocol cipher lists:

```
TLSv1.2:
  ciphers:
    TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 (secp256r1) - A
    TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 (secp256r1) - A
TLSv1.3:
  ciphers:
    TLS_AES_256_GCM_SHA384 - A
```

2. SSLScan (Protocol + Cipher)

```bash
sslscan --show-sigs --show-times example.com:443
```

Shows TLS 1.0/1.1/1.2/1.3 support with ciphers grouped by protocol version.

3. Testssl.sh (Most Detailed)

```bash
testssl.sh -e -E --protocols example.com:443
```

- `-e`: Enumerate each local cipher against the server
- `-E`: Enumerate all server ciphers per protocol
- `--protocols`: Show TLS version support

4. OpenSSL (Protocol-by-Protocol Testing)

```bash
#!/bin/bash
host="example.com"
port="443"

# Test each TLS protocol version
for version in -tls1_3 -tls1_2 -tls1_1 -tls1; do
    protocol=${version#-}
    echo "=== Testing $protocol ==="
    
    # Get ciphers available for this protocol
    ciphers=$(openssl ciphers 'ALL:eNULL' $version 2>/dev/null | tr ':' ' ')
    
    for cipher in $ciphers; do
        result=$(openssl s_client -connect "$host:$port" $version -cipher "$cipher" </dev/null 2>/dev/null)
        if echo "$result" | grep -q "Cipher is ${cipher}"; then
            echo " SUPPORTED: $cipher"
        fi
    done
done
```

Sample output:

```
=== Testing tls1_3 ===
  SUPPORTED: TLS_AES_256_GCM_SHA384
  SUPPORTED: TLS_CHACHA20_POLY1305_SHA256
=== Testing tls1_2 ===
  SUPPORTED: ECDHE-RSA-AES128-GCM-SHA256
  SUPPORTED: ECDHE-RSA-AES256-GCM-SHA384
=== Testing tls1_1 ===
  (none - server disabled)
=== Testing tls1 ===
  (none - server disabled)
```

5. Java (Protocol + Cipher Enumeration)

```java
import javax.net.ssl.*;
import java.net.*;
import java.util.*;

public class TlsCipherEnum {
    
    private static final Map<String, String> PROTOCOL_MAP = Map.of(
        "TLSv1.3", "TLSv1.3",
        "TLSv1.2", "TLSv1.2", 
        "TLSv1.1", "TLSv1.1",
        "TLSv1", "TLSv1"
    );

    public static void main(String[] args) throws Exception {
        String host = "example.com";
        int port = 443;

        for (Map.Entry<String, String> entry : PROTOCOL_MAP.entrySet()) {
            String protocolName = entry.getKey();
            String protocolValue = entry.getValue();
            
            System.out.println("=== " + protocolValue + " ===");
            
            try {
                SSLContext ctx = SSLContext.getInstance(protocolName);
                ctx.init(null, new TrustManager[]{new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }}, new java.security.SecureRandom());

                SSLSocketFactory factory = ctx.getSocketFactory();
                String[] supportedCiphers = factory.getSupportedCipherSuites();
                
                boolean anySupported = false;
                for (String cipher : supportedCiphers) {
                    try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                        socket.setEnabledProtocols(new String[]{protocolValue});
                        socket.setEnabledCipherSuites(new String[]{cipher});
                        socket.setSoTimeout(5000);
                        socket.startHandshake();
                        System.out.println(" SUPPORTED: " + cipher);
                        anySupported = true;
                    } catch (Exception e) {
                        // Handshake failed — cipher not supported for this protocol
                    }
                }
                
                if (!anySupported) {
                    System.out.println(" (No ciphers supported or protocol disabled)");
                }
                
            } catch (Exception e) {
                System.out.println(" Protocol not available in this JVM");
            }
        }
    }
}
```

Maven dependency (if you need to run standalone):

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
```

6. One-Liner Summary with OpenSSL

```bash
echo "TLS 1.3:"; openssl s_client -connect example.com:443 -tls1_3 </dev/null 2>/dev/null | grep "Protocol\|Cipher"; \
echo -e " TLS 1.2:"; openssl s_client -connect example.com:443 -tls1_2 </dev/null 2>/dev/null | grep "Protocol\|Cipher"; \
echo -e " TLS 1.1:"; openssl s_client -connect example.com:443 -tls1_1 </dev/null 2>/dev/null | grep "Protocol\|Cipher"; \
echo -e " TLS 1.0:"; openssl s_client -connect example.com:443 -tls1 </dev/null 2>/dev/null | grep "Protocol\|Cipher"
```

Quick Reference: Protocol Version Flags

Protocol OpenSSL Flag Java Protocol String
TLS 1.3 `-tls1_3` `TLSv1.3`
TLS 1.2 `-tls1_2` `TLSv1.2`
TLS 1.1 `-tls1_1` `TLSv1.1`
TLS 1.0 `-tls1` `TLSv1`
SSLv3 `-ssl3` `SSLv3` (deprecated)

Recommendation: Use `testssl.sh` for comprehensive auditing or `sslscan` for quick checks. The Java approach is useful when you need to validate against specific JVM configurations (e.g., FIPS compliance).