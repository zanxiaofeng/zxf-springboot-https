获取 HTTPS URL 支持的 Cipher List（密码套件列表）有多种工具和方法，以下是几种常用的方式：

1. 使用 `testssl.sh`（推荐，最全面）

`testssl.sh` 是检测 SSL/TLS 配置的行业标准工具，可以详细列出服务器支持的所有 cipher suites。

```bash
# 安装
wget https://testssl.sh/testssl.sh && chmod +x testssl.sh

# 基础扫描（包含 cipher 列表）
./testssl.sh example.com

# 仅检测每个 cipher（最详细）
./testssl.sh -e --show-each example.com

# 按协议检测 cipher
./testssl.sh -E --show-each example.com

# 宽格式输出，显示 hexcode、密钥交换、加密位数等
./testssl.sh -e --wide example.com
```

输出示例会包含：
- TLSv1.2 / TLSv1.3 各自支持的 cipher
- 每个 cipher 的 hexcode、OpenSSL 名称、IANA/RFC 名称
- 密钥交换算法、加密位数、安全评级

2. 使用 `nmap`（快速扫描）

```bash
# 基础 cipher 枚举
nmap --script ssl-enum-ciphers -p 443 example.com

# 详细输出（包含 ALPN 等参数）
nmap -p 443 \
     --script ssl-enum-ciphers \
     --script-args 'unsafe=1,ssl.ciphers.alpn={h2,http/1.1}' \
     example.com
```

输出示例：

```
| ssl-enum-ciphers:
| TLSv1.2:
| ciphers:
| TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 (secp256r1) - A
| TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 (secp256r1) - A
| cipher preference: server
|_ least strength: A
``` [^2^][^10^]

## 3. 使用 `openssl`（快速检查，但不够全面）

```bash
# 查看本地 OpenSSL 支持的所有 cipher
openssl ciphers -v | column -t

# 查看特定服务器协商出的 cipher（仅显示最终选中的）
openssl s_client -connect example.com:443 2>/dev/null | grep Cipher

# 测试特定 cipher 是否被支持
openssl s_client -connect example.com:443 -cipher ECDHE-RSA-AES256-GCM-SHA384 2>&1 | grep "Cipher is"
```

注意：`openssl s_client` 默认只显示最终协商成功的那个 cipher，而不是服务器支持的全部列表。要枚举所有支持的 cipher，需要循环测试或使用专业工具 。

4. 使用 `sslyze`（Python 工具，扫描速度快）

```bash
# 安装
pip install sslyze

# 扫描
sslyze example.com:443

# 输出包含：
# - TLS 1.2 Cipher Suites（列出所有接受的 cipher）
# - TLS 1.3 Cipher Suites
# - 前向保密性、密钥交换算法等
```

输出示例：

```
TLS 1.2 Cipher Suites:
  The server accepted the following 2 cipher suites:
    TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 256 ECDH: prime256v1 (256 bits)
    TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 128 ECDH: prime256v1 (256 bits)
``` [^13^]

## 5. 在线工具（无需安装）

- **Qualys SSL Labs**: https://www.ssllabs.com/ssltest/
  - 输入域名即可获取完整的 cipher suite 分析报告
  - 包含安全评级、协议版本、证书详情等 [^5^][^11^]

## 对比总结

| 工具 | 优点 | 适用场景 |
|------|------|----------|
| **testssl.sh** | 最全面，支持 370+ cipher 检测，输出详细 | 安全审计、合规检查 |
| **nmap** | 快速，集成在端口扫描中 | 批量扫描、快速评估 |
| **openssl** | 无需安装，系统自带 | 快速验证单个 cipher |
| **sslyze** | 扫描速度快，输出结构化 | 自动化脚本、CI/CD |
| **SSL Labs** | 可视化报告，权威评级 | 第三方验证、报告生成 |

如果你需要完整的、可用于安全审计的 cipher list，推荐使用 `testssl.sh -e --wide` 或 `sslyze`。