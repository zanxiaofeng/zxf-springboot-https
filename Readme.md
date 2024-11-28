# Test specific cipher suites for a TLS connection
- nmap --script ssl-enum-ciphers -p 443 www.google.com

# Test TLS Connection Ciphers TLS Version and Certificate with OpenSSL Command Line
## Use -connect <host>:<port> to connect to a TLS server(See TLS handshake process, server certificate)
- openssl s_client -connect www.google.com:443
## Use -showcerts to show all certificates in the chain
- openssl s_client -connect dns.google:853 -showcerts
## Use -servername to pass server name (SNI) to openssl s_client
- openssl s_client -connect 93.184.216.34:443 -servername example.com
- curl -v https://www.example.com --resolve www.example.com:443:93.184.216.34
## Use -tls** to test TLS version support
- openssl s_client -connect dns.google:853 -tls1
- openssl s_client -connect dns.google:853 -tls1_1
- openssl s_client -connect dns.google:853 -tls1_2
- openssl s_client -connect dns.google:853 -tls1_3
## Test specific cipher suites for a TLS connection
- openssl ciphers -v
- openssl s_client -connect www.cloudflare.com:443 -tls1_3 -ciphersuites 'TLS_AES_256_GCM_SHA384'
## Extract server public certificate into a PEM encoded file
- echo -n | openssl s_client -connect www.example.com:443 -servername www.example.com | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > cert.pem
## Decode PEM encoded certificate file
- openssl x509 -in cert.pem -text -noout

# 如何知道一个Jar包所用的日志系统，比如apache httpclient
- 查看jar包的pom.xml中直接依赖的日志系统，当然也有可能是依赖的依赖。
- 查看代码。
