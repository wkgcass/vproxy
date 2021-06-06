package vproxy.base.component.check;

import vproxy.base.Config;
import vproxy.base.util.Annotations;

public class AnnotatedHcConfig {
    private static final boolean[] defaultHttpStatus = new boolean[]{false, // 0 not used
        true, // 1xx
        true, // 2xx
        true, // 3xx
        true, // 4xx
        false // 5xx rejected
    };

    private String httpMethod;
    private String httpUrl;
    private String httpHost;
    private boolean[] httpStatus;
    private String dnsDomain;

    public void set(Annotations annos) {
        httpMethod = annos.ServerGroup_HCHttpMethod;
        httpUrl = annos.ServerGroup_HCHttpUrl;
        httpHost = annos.ServerGroup_HCHttpHost;
        httpStatus = annos.ServerGroup_HCHttpStatus;
        dnsDomain = annos.ServerGroup_HCDnsDomain;
    }

    public String getHttpMethod() {
        return httpMethod == null ? "GET" : httpMethod;
    }

    public String getHttpHost() {
        return httpHost;
    }

    public boolean[] getHttpStatus() {
        return httpStatus == null ? defaultHttpStatus : httpStatus;
    }

    public String getHttpUrl() {
        return httpUrl == null ? "/" : httpUrl;
    }

    public String getDnsDomain() {
        return dnsDomain == null ? Config.domainWhichShouldResolve : dnsDomain;
    }
}
