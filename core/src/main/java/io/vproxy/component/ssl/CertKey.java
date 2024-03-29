package io.vproxy.component.ssl;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.ringbuffer.ssl.VSSLContext;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public X509Certificate[] setInto(KeyStore keystore) throws Exception {
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

        return x509certs;
    }

    public void setInto(VSSLContext vsslContext) throws Exception {
        // create ctx
        SSLContext ctx = SSLContext.getInstance("TLS");
        // create empty key store
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null);
        // init keystore
        X509Certificate[] certs = this.setInto(keyStore);
        // retrieve key manager array
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, "changeit".toCharArray());
        KeyManager[] km = kmf.getKeyManagers();
        // init ctx
        ctx.init(km, null, null);

        vsslContext.sslContextHolder.add(ctx, certs);
    }

    public SSLContext buildSSLContext() throws Exception {
        // create ctx
        SSLContext ctx = SSLContext.getInstance("TLS");
        // create empty key store
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null);
        // init keystore
        this.setInto(keyStore);
        // retrieve key manager array
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, "changeit".toCharArray());
        KeyManager[] km = kmf.getKeyManagers();
        // init ctx
        ctx.init(km, null, null);
        return ctx;
    }

    public Tuple<String, String> ensureCertKeyFile() throws IOException {
        String cert = null;
        String key = null;

        if (certPaths != null && certPaths.length == 1) {
            cert = certPaths[0];
            if (!Files.exists(Path.of(cert))) {
                assert Logger.lowLevelDebug("cert file " + cert + " is present but not found");
                cert = null;
            }
        }
        if (cert == null) {
            cert = writeCertTempFile();
        }
        if (keyPath != null) {
            key = keyPath;
            if (!Files.exists(Path.of(key))) {
                assert Logger.lowLevelDebug("key file " + key + " is present but not found");
                key = null;
            }
        }
        if (key == null) {
            key = writeKeyTempFile();
        }

        return new Tuple<>(cert, key);
    }

    private String writeCertTempFile() throws IOException {
        Path f = Files.createTempFile(alias + "-cert-", ".pem");
        assert Logger.lowLevelDebug("will write cert " + alias + " to " + f);
        try {
            var sb = new StringBuilder();
            for (var c : certs) {
                sb.append(c).append("\n");
            }
            Files.writeString(f, sb.toString());
            return f.toAbsolutePath().toString();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            f.toFile().delete();
            throw e;
        }
    }

    private String writeKeyTempFile() throws IOException {
        Path f = Files.createTempFile(alias + "-key-", ".pem");
        assert Logger.lowLevelDebug("will write key " + alias + " to " + f);
        try {
            Files.writeString(f, key);
            return f.toAbsolutePath().toString();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            f.toFile().delete();
            throw e;
        }
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
