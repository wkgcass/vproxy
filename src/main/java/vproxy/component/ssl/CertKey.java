package vproxy.component.ssl;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class CertKey {
    public final String alias;
    public final String[] certs;
    public final String key;
    public final String[] certPaths;
    public final String keyPath;

    public CertKey(String alias, String[] certs, String key) {
        this(alias, certs, key, null, null);
    }

    public CertKey(String alias, String[] certs, String key, String[] certPaths, String keyPath) {
        this.alias = alias;
        this.certs = certs;
        this.key = key;
        this.certPaths = certPaths;
        this.keyPath = keyPath;
    }

    public void validate() throws Exception {
        KeyStore k = KeyStore.getInstance("JKS");
        k.load(null);
        setInto(k);
    }

    public void setInto(KeyStore keystore) throws Exception {
        String[] keysplit = this.key
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "").split("\n");
        StringBuilder keyBuilder = new StringBuilder();
        for (String k : keysplit) {
            keyBuilder.append(k.trim());
        }
        String keyStr = keyBuilder.toString();
        byte[] keyBytes = Base64.getDecoder().decode(keyStr);

        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(pkcs8EncodedKeySpec);

        X509Certificate[] x509certs = new X509Certificate[certs.length];
        for (int i = 0; i < certs.length; i++) {
            String strCert = certs[i];
            byte[] certBytes = strCert.getBytes();

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));
            x509certs[i] = cert;

            keystore.setCertificateEntry("cert" + i + ":" + alias, cert);
        }
        keystore.setKeyEntry("key:" + alias, key, "changeit".toCharArray(), x509certs);
    }

    @Override
    public String toString() {
        return "CertKey{" +
            "alias='" + alias + '\'' +
            ", certs=" + Arrays.toString(certs) +
            ", key='" + "******" + '\'' +
            ", certPaths=" + Arrays.toString(certPaths) +
            ", keyPath='" + keyPath + '\'' +
            '}';
    }
}
