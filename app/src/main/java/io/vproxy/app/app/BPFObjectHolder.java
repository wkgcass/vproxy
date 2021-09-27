package io.vproxy.app.app;

import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.xdp.BPFMode;
import io.vproxy.xdp.BPFObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BPFObjectHolder {
    private final Map<String, BPFObject> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public BPFObject add(String filepath, String programName, String nicName,
                         BPFMode mode, boolean forceAttach) throws AlreadyExistException, IOException {
        if (map.containsKey(nicName))
            throw new AlreadyExistException("bpf-object", nicName);
        var bpf = BPFObject.loadAndAttachToNic(filepath, programName, nicName, mode, forceAttach);
        map.put(bpf.nic, bpf);
        return bpf;
    }

    public BPFObject get(String nicName) throws NotFoundException {
        BPFObject bpfobj = map.get(nicName);
        if (bpfobj == null)
            throw new NotFoundException("bpf-object", nicName);
        return bpfobj;
    }

    public void removeAndRelease(String alias, boolean detach) throws NotFoundException {
        BPFObject g = map.remove(alias);
        if (g == null)
            throw new NotFoundException("bpf-object", alias);
        g.release(detach);
    }
}
