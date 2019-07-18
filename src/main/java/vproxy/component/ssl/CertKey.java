package vproxy.component.ssl;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class CertKey {
    public final String alias;
    public final String cert;
    public final String key;

    public CertKey(String alias, String cert, String key) {
        this.alias = alias;
        this.cert = cert;
        this.key = key;
    }

    public void validate() throws Exception {
        KeyStore k = KeyStore.getInstance("JKS");
        k.load(null);
        setInto(k);
    }

    public void setInto(KeyStore keystore) throws Exception {
        byte[] certBytes = cert.getBytes();
        String[] keysplit = this.key
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "").split("\n");
        StringBuilder keyBuilder = new StringBuilder();
        for (String k : keysplit) {
            keyBuilder.append(k.trim());
        }
        String keyStr = keyBuilder.toString();
        byte[] keyBytes = Base64.getDecoder().decode(keyStr);

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));

        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(pkcs8EncodedKeySpec);

        keystore.setCertificateEntry("cert:" + alias, cert);
        keystore.setKeyEntry("key:" + alias, key, "changeit".toCharArray(), new Certificate[]{cert});
    }
}
