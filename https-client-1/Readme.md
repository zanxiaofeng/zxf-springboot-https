# Test
- curl -k -vvvv http://localhost:8088/test/trust-client-auth
- curl -k -vvvv http://localhost:8088/test/trust
- curl -k -vvvv http://localhost:8088/test/no-trust

# Key Classes
- javax.net.ssl.SSLContext
- javax.net.ssl.KeyManagerFactory
- javax.net.ssl.TrustManagerFactory
- javax.net.ssl.HostnameVerifier
- javax.net.ssl.SSLSocketFactory
- org.apache.http.ssl.SSLContextBuilder;
- org.apache.http.conn.ssl.SSLConnectionSocketFactory;
- org.apache.http.client.HttpClient[interface]
- org.apache.http.impl.client.HttpClients[facade]
- org.springframework.http.client.ClientHttpRequestFactory
- org.springframework.http.client.InterceptingClientHttpRequestFactory[delegate]
- org.springframework.http.client.HttpComponentsClientHttpRequestFactory[apache http client]
- org.springframework.http.client.OkHttp3ClientHttpRequestFactory[OkHttp]
- org.springframework.http.client.SimpleClientHttpRequestFactory