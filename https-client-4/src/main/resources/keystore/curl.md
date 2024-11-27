# Https(Server side auth)
## Ignore server cert verify
- curl -k -vvvv https://localhost:8080/home
## Set ca-cert
- curl --cacert ./keystore/trust.crt.pem -vvvv https://localhost:8080/home
- 
# Https(Client side auth)
## Ignore server cert verify
- curl -k --cert-type P12 --cert ./keystore/keystore-client.p12:changeit -vvvv https://localhost:8082/home
- curl -k --cert-type PEM --cert ./keystore/client.crt.pem --key-type PEM --key ./keystore/client.key.pem -vvvv https://localhost:8082/home
## Set ca-cert
- curl --cacert ./keystore/trust.crt.pem --cert-type P12 --cert ./keystore/keystore-client.p12:changeit -vvvv https://localhost:8082/home
- curl --cacert ./keystore/trust.crt.pem --cert-type PEM --cert ./keystore/client.crt.pem --key-type PEM --key ./keystore/client.key.pem -vvvv https://localhost:8082/home
