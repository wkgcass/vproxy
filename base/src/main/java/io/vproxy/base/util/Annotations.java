package io.vproxy.base.util;

import vjson.util.ObjectBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Annotations {
    public final String ServerGroup_HintHost;
    public final int ServerGroup_HintPort;
    public final String ServerGroup_HintUri;
    public final String ServerGroup_HCHttpMethod;
    public final String ServerGroup_HCHttpUrl;
    public final String ServerGroup_HCHttpHost;
    public final boolean[] ServerGroup_HCHttpStatus;
    public final String ServerGroup_HCDnsDomain;
    public final boolean EventLoopGroup_PreferPoll;
    public final boolean EventLoopGroup_UseMsQuic;
    public final long EventLoop_CoreAffinity;

    public final Map<String, String> other;

    private final Map<String, String> raw;

    public Annotations() {
        this(Map.of());
    }

    public Annotations(Map<String, String> annotations) {
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        Map<String, String> other = new HashMap<>();
        for (var kv : annotations.entrySet()) {
            String k = kv.getKey();
            String v = kv.getValue();
            boolean deserialize = false;
            for (var e : AnnotationKeys.values()) {
                if (e.name.equals(k) && e.deserialize) {
                    deserialize = true;
                    break;
                }
            }
            if (!deserialize) {
                other.put(k, v);
            }
        }

        {
            ServerGroup_HintHost = annotations.get(AnnotationKeys.ServerGroup_HintHost.name);
        }
        {
            String ServerGroup_HintPort = annotations.get(AnnotationKeys.ServerGroup_HintPort.name);
            if (ServerGroup_HintPort == null) {
                this.ServerGroup_HintPort = 0;
            } else {
                int n = 0;
                try {
                    n = Integer.parseInt(ServerGroup_HintPort);
                } catch (NumberFormatException e) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid " + AnnotationKeys.ServerGroup_HintPort +
                        ", not a number: " + ServerGroup_HintPort);
                    other.put(AnnotationKeys.ServerGroup_HintPort.name, ServerGroup_HintPort);
                }
                this.ServerGroup_HintPort = n;
            }
        }
        {
            ServerGroup_HintUri = annotations.get(AnnotationKeys.ServerGroup_HintUri.name);
        }
        {
            ServerGroup_HCHttpMethod = annotations.get(AnnotationKeys.ServerGroup_HCHttpMethod.name);
        }
        {
            ServerGroup_HCHttpUrl = annotations.get(AnnotationKeys.ServerGroup_HCHttpUrl.name);
        }
        {
            ServerGroup_HCHttpHost = annotations.get(AnnotationKeys.ServerGroup_HCHttpHost.name);
        }
        {
            String ServerGroup_HCHttpStatus = annotations.get(AnnotationKeys.ServerGroup_HCHttpStatus.name);
            if (ServerGroup_HCHttpStatus == null) {
                this.ServerGroup_HCHttpStatus = null;
            } else {
                String[] split = ServerGroup_HCHttpStatus.split(",");
                boolean[] bools = new boolean[6]; // 0 is not used, 1xx,2xx,3xx,4xx,5xx
                boolean fails = false;
                loop:
                for (String s : split) {
                    s = s.trim();
                    switch (s) {
                        case "1xx":
                            bools[1] = true;
                            break;
                        case "2xx":
                            bools[2] = true;
                            break;
                        case "3xx":
                            bools[3] = true;
                            break;
                        case "4xx":
                            bools[4] = true;
                            break;
                        case "5xx":
                            bools[5] = true;
                            break;
                        default:
                            fails = true;
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid " + AnnotationKeys.ServerGroup_HCHttpStatus +
                                ", unknown status indicator, only supports 1xx,2xx,3xx,4xx,5xx, but got " + s);
                            break loop;
                    }
                }
                if (fails) {
                    this.ServerGroup_HCHttpStatus = null;
                } else {
                    this.ServerGroup_HCHttpStatus = bools;
                }
            }
        }
        {
            ServerGroup_HCDnsDomain = annotations.get(AnnotationKeys.ServerGroup_HCDnsDomain.name);
        }
        {
            EventLoopGroup_PreferPoll = "true".equals(annotations.get(AnnotationKeys.EventLoopGroup_PreferPoll.name));
        }
        {
            EventLoopGroup_UseMsQuic = "true".equals(annotations.get(AnnotationKeys.EventLoopGroup_UseMsQuic.name));
        }
        {
            long coreAffinity = -1;
            String str = annotations.get(AnnotationKeys.EventLoop_CoreAffinity.name);
            if (str != null) {
                if (Utils.isLong(str)) {
                    coreAffinity = Long.parseLong(str);
                }
            }
            EventLoop_CoreAffinity = coreAffinity;
        }

        this.other = Collections.unmodifiableMap(other);
    }

    public Map<String, String> getRaw() {
        return raw;
    }

    public boolean isEmpty() {
        return raw.isEmpty();
    }

    @Override
    public String toString() {
        ObjectBuilder ob = new ObjectBuilder();
        for (Map.Entry<String, String> entry : getRaw().entrySet()) {
            ob.put(entry.getKey(), entry.getValue());
        }
        return ob.build().stringify();
    }
}
