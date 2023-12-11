# Test by curl
- curl -k -vvvv https://localhost:8080/home

# Test by openssl
- openssl s_client -debug -showcerts -security_debug -connect localhost:8080
