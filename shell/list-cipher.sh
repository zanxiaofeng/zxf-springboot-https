#!/bin/bash
host="www.163.com"
port="443"

# Test each TLS protocol version
for version in -tls1_3 -tls1_2 -tls1_1 -tls1; do
    protocol=${version#-}
    echo "=== Testing $protocol ==="

    # Get ciphers available for this protocol
    ciphers=$(openssl ciphers $version 'ALL:eNULL' 2>/dev/null | tr ':' ' ')

    for cipher in $ciphers; do
        result=$(openssl s_client -connect "$host:$port" $version -cipher "$cipher" </dev/null 2>/dev/null)
        if echo "$result" | grep -q "Cipher is ${cipher}"; then
            echo " SUPPORTED: $cipher"
        fi
    done
done