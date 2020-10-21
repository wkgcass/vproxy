package vproxyx.websocks.ssl;

import vproxy.component.ssl.CertKey;
import vproxy.util.CoreUtils;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.ringbuffer.ssl.SSLContextHolder;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutoSignSSLContextHolder extends SSLContextHolder {
    private static final List<String[]> commandTemplates = Arrays.asList(
        "openssl genrsa -out $NAME.key 2048".split(" "),
        "openssl pkcs8 -topk8 -nocrypt -in $NAME.key -out $NAME.key.pk8".split(" "),
        "rm $NAME.key".split(" "),
        "mv $NAME.key.pk8 $NAME.key".split(" "),
        ("openssl req -reqexts v3_req -sha256 -new -key $NAME.key -out $NAME.csr -config $NAME.cnf" +
            " -subj /C=CN/ST=Beijing/L=Beijing/O=vproxy/OU=AutoSigned/CN=$NAME"
        ).split(" "),
        "openssl x509 -req -extensions v3_req -days 365 -sha256 -in $NAME.csr -CA $CACRT -CAkey $CAKEY -CAcreateserial -out $NAME.crt -extfile $NAME.cnf".split(" ")
    );
    private static final String opensslConfigTemplate = "" +
        "[ req ]\n" +
        "default_bits\t\t= 2048\n" +
        "default_md\t\t= sha256\n" +
        "distinguished_name\t= req_distinguished_name\n" +
        "attributes\t\t= req_attributes\n" +
        "\n" +
        "[ req_distinguished_name ]\n" +
        "countryName\t\t\t= Country Name (2 letter code)\n" +
        "countryName_min\t\t\t= 2\n" +
        "countryName_max\t\t\t= 2\n" +
        "stateOrProvinceName\t\t= State or Province Name (full name)\n" +
        "localityName\t\t\t= Locality Name (eg, city)\n" +
        "0.organizationName\t\t= Organization Name (eg, company)\n" +
        "organizationalUnitName\t\t= Organizational Unit Name (eg, section)\n" +
        "commonName\t\t\t= Common Name (eg, fully qualified host name)\n" +
        "commonName_max\t\t\t= 64\n" +
        "\n" +
        "[ req_attributes ]\n" +
        "\n" +
        "[ v3_req ]\n" +
        "basicConstraints = CA:FALSE\n" +
        "keyUsage = nonRepudiation, digitalSignature, keyEncipherment\n" +
        "subjectAltName = @alt_names\n" +
        "\n" +
        "[ alt_names ]\n" +
        "DNS.1 = $NAME\n" +
        "";

    private final String caCert;
    private final String caKey;
    private final File workingDirectory;

    public AutoSignSSLContextHolder(String caCert, String caKey, File workingDirectory) {
        this.caCert = caCert;
        this.caKey = caKey;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public SSLContext choose(String sni) {
        if (sni == null) {
            return super.choose(null);
        }
        SSLContext ctx = chooseNoDefault(sni);
        if (ctx != null) {
            return ctx;
        }
        Logger.alert("signing new cert for " + sni);
        ctx = sign(sni);
        if (ctx != null) {
            quickAccess.put(sni, ctx);
        }
        return ctx;
    }

    private SSLContext sign(String sni) {
        if (!prepareConfig(sni)) {
            return null;
        }
        for (String[] tmpl : commandTemplates) {
            if (!runCommand(tmpl, sni)) {
                return null;
            }
        }
        return load(sni);
    }

    private boolean prepareConfig(String sni) {
        String config = opensslConfigTemplate.replace("$NAME", sni);
        File configF = Path.of(workingDirectory.getAbsolutePath(), sni + ".cnf").toFile();
        try {
            //noinspection ResultOfMethodCallIgnored
            configF.createNewFile();
        } catch (IOException e) {
            Logger.error(LogType.FILE_ERROR, "creating file " + configF + " failed");
            return false;
        }
        try (FileOutputStream fos = new FileOutputStream(configF)) {
            fos.write(config.getBytes());
            fos.flush();
        } catch (IOException e) {
            Logger.error(LogType.FILE_ERROR, "writing file " + configF + " failed");
            return false;
        }
        return true;
    }

    private boolean runCommand(String[] template, String sni) {
        String[] cmds = new String[template.length];
        for (int i = 0; i < template.length; ++i) {
            String cmd = template[i];
            cmd = cmd.replace("$CACRT", caCert).replace("$CAKEY", caKey).replace("$NAME", sni);
            cmds[i] = cmd;
        }
        Logger.alert("executing command: " + Arrays.stream(cmds).flatMap(s -> Stream.of(s, " ")).collect(Collectors.joining()));
        ProcessBuilder builder = new ProcessBuilder();
        Process process;
        try {
            process = builder.directory(workingDirectory).command(cmds).start();
        } catch (IOException e) {
            Logger.error(LogType.SYS_ERROR, "starting sub process failed", e);
            return false;
        }
        try {
            process.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
        }
        if (process.isAlive()) {
            Logger.error(LogType.SYS_ERROR, "waiting for sub process for too long");
            process.destroy();
            return false;
        }
        int code = process.exitValue();
        if (code != 0) {
            Logger.error(LogType.SYS_ERROR, "sub process exit code is not zero: " + code);
            return false;
        }
        return true;
    }

    private SSLContext load(String sni) {
        File crt = Path.of(workingDirectory.getAbsolutePath(), sni + ".crt").toFile();
        File key = Path.of(workingDirectory.getAbsolutePath(), sni + ".key").toFile();
        CertKey ck;
        try {
            ck = CoreUtils.readCertKeyFromFile("agent.auto-sign." + sni, new String[]{crt.getAbsolutePath()}, key.getAbsolutePath());
        } catch (Exception e) {
            Logger.error(LogType.FILE_ERROR, "reading crt and key failed: crt=" + crt + ", key=" + key, e);
            return null;
        }
        try {
            return ck.buildSSLContext();
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "loading cert-key for " + sni + " failed", e);
            return null;
        }
    }
}
