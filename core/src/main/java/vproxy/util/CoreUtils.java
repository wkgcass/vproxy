package vproxy.util;

import vproxy.base.connection.Connector;
import vproxy.base.dns.Resolver;
import vproxy.base.socks.AddressType;
import vproxy.base.util.Callback;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.component.ssl.CertKey;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class CoreUtils {
    public static void directConnect(AddressType type, String address, int port, Consumer<Connector> providedCallback) {
        if (type == AddressType.domain) { // resolve if it's domain
            Resolver.getDefault().resolve(address, new Callback<>() {
                @Override
                protected void onSucceeded(IP value) {
                    providedCallback.accept(new Connector(new IPPort(value, port)));
                }

                @Override
                protected void onFailed(UnknownHostException err) {
                    // resolve failed
                    assert Logger.lowLevelDebug("resolve for " + address + " failed in socks5 server" + err);
                    providedCallback.accept(null);
                }
            });
        } else {
            if (!IP.isIpLiteral(address)) {
                assert Logger.lowLevelDebug("client request with an invalid ip " + address);
                providedCallback.accept(null);
                return;
            }
            IP remote = IP.from(address);
            providedCallback.accept(new Connector(new IPPort(remote, port)));
        }
    }

    private static List<String> readFile(String path) throws Exception {
        path = Utils.filename(path);
        File f = new File(path);
        if (!f.exists())
            throw new FileNotFoundException("file not found: " + path);
        if (f.isDirectory())
            throw new Exception("input path is directory: " + path);
        if (!f.canRead())
            throw new Exception("input file is not readable: " + path);
        List<String> lines = new LinkedList<>();
        try (FileReader fr = new FileReader(f); BufferedReader br = new BufferedReader(fr)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                line = line.trim();
                lines.add(line);
            }
        }
        return lines;
    }

    private static List<String> getCertsFrom(String path) throws Exception {
        List<String> lines = readFile(path);
        if (lines.isEmpty()) {
            throw new Exception("file is blank or empty: " + path);
        }
        boolean begin = false;
        List<String> ret = new LinkedList<>();
        StringBuilder cert = null;
        for (String line : lines) {
            if (begin) {
                if (line.equals("-----END CERTIFICATE-----")) {
                    begin = false;
                    cert.append("\n").append(line);
                    ret.add(cert.toString());
                    cert = null;
                } else {
                    cert.append("\n").append(line);
                }
            } else {
                if (line.equals("-----BEGIN CERTIFICATE-----")) {
                    begin = true;
                    cert = new StringBuilder();
                    cert.append(line);
                }
            }
        }
        if (ret.isEmpty()) {
            throw new Exception("the file does not contain any certificate: " + path);
        }
        return ret;
    }

    private static String getKeyFrom(String path) throws Exception {
        List<String> lines = readFile(path);
        if (lines.isEmpty()) {
            throw new Exception("file is blank or empty: " + path);
        }
        boolean begin = false;
        StringBuilder key = null;
        for (String line : lines) {
            if (begin) {
                if (line.equals("-----END PRIVATE KEY-----")) {
                    begin = false;
                    key.append("\n").append(line);
                } else {
                    key.append("\n").append(line);
                }
            } else {
                if (line.equals("-----BEGIN PRIVATE KEY-----")) {
                    if (key != null) {
                        throw new Exception("the file contains multiple keys: " + path);
                    }
                    begin = true;
                    key = new StringBuilder();
                    key.append(line);
                }
            }
        }
        if (key == null) {
            throw new Exception("the file does not contain any private key. note that only -----BEGIN PRIVATE KEY----- encapsulation is supported: " + path);
        }
        return key.toString();
    }

    public static CertKey readCertKeyFromFile(String alias, String[] certFilePathList, String keyFilePath) throws Exception {
        List<String> certs = new ArrayList<>();
        for (String certFilePath : certFilePathList) {
            certs.addAll(getCertsFrom(certFilePath));
        }
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        String[] certsArray = certs.toArray(new String[certs.size()]);
        String key = getKeyFrom(keyFilePath);
        CertKey ck = new CertKey(alias, certsArray, key, certFilePathList, keyFilePath);
        ck.validate();
        return ck;
    }
}
