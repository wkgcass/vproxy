package net.cassite.vproxyx.websocks;

import net.cassite.graal.JsContext;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;

import java.util.regex.Pattern;

public interface DomainChecker {
    boolean needProxy(String domain);
}

class SuffixDomainChecker implements DomainChecker {
    private final String suffix;

    SuffixDomainChecker(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public boolean needProxy(String domain) {
        return domain.endsWith(suffix);
    }
}

class PatternDomainChecker implements DomainChecker {
    private final Pattern pattern;

    PatternDomainChecker(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean needProxy(String domain) {
        return pattern.matcher(domain).matches();
    }
}

class PacDomainChecker implements DomainChecker {
    private final JsContext engine;

    PacDomainChecker(JsContext engine) {
        this.engine = engine;
    }

    @Override
    public boolean needProxy(String domain) {
        String result;
        try {
            result = engine.eval("FindProxyForURL('', '" + domain + "')", String.class);
        } catch (Exception e) {
            Logger.error(LogType.IMPROPER_USE, "the pac execution got exception with " + domain, e);
            return false;
        }
        if (result == null) {
            Logger.error(LogType.IMPROPER_USE, "the pac execution with " + domain + ", result is null");
            return false;
        }
        if (result.equals("DIRECT")) {
            assert Logger.lowLevelDebug("pac returns DIRECT for " + domain + ", no need to proxy");
            return false;
        }
        assert Logger.lowLevelDebug("pac returns " + result + " for " + domain + ", do proxy");
        return true;
    }
}
