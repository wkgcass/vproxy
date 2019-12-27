#!/bin/bash
openssl genrsa -out ca.key 2048
openssl req -new -x509 -extensions v3_ca -days 36500 -key ca.key -out ca.crt -config <(cat openssl.cnf v3_ca.cnf)
