package vproxyx.websocks;

import java.util.regex.Pattern;

public interface DomainChecker {
    boolean needProxy(String domain, int port);
}

class SuffixDomainChecker implements DomainChecker {
    private final String suffix;

    SuffixDomainChecker(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public boolean needProxy(String domain, int port) {
        return domain.endsWith(suffix);
    }
}

class PatternDomainChecker implements DomainChecker {
    private final Pattern pattern;

    PatternDomainChecker(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean needProxy(String domain, int port) {
        return pattern.matcher(domain).matches();
    }
}

class ABPDomainChecker implements DomainChecker {
    private final ABP abp;

    ABPDomainChecker(ABP abp) {
        this.abp = abp;
    }

    @Override
    public boolean needProxy(String domain, int port) {
        return abp.block(domain);
    }
}

class PortChecker implements DomainChecker {
    private final int port;

    PortChecker(int port) {
        this.port = port;
    }

    @Override
    public boolean needProxy(String domain, int port) {
        return port == this.port;
    }
}
