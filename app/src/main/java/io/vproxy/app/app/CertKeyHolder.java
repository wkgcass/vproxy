package io.vproxy.app.app;

import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.component.ssl.CertKey;
import io.vproxy.util.CoreUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CertKeyHolder {
    private final Map<String, CertKey> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    @SuppressWarnings("DuplicateThrows")
    public void add(String alias, String[] certFilePathList, String keyFilePath) throws AlreadyExistException, Exception {
        if (map.containsKey(alias)) {
            throw new AlreadyExistException("cert-key", alias);
        }
        CertKey ck = CoreUtils.readCertKeyFromFile(alias, certFilePathList, keyFilePath);
        map.put(alias, ck);
    }

    public CertKey get(String alias) throws NotFoundException {
        CertKey ck = map.get(alias);
        if (ck == null) {
            throw new NotFoundException("cert-key", alias);
        }
        return ck;
    }

    public void remove(String alias) throws NotFoundException {
        CertKey ck = map.remove(alias);
        if (ck == null) {
            throw new NotFoundException("cert-key", alias);
        }
    }
}
