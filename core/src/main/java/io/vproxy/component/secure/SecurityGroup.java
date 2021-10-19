package io.vproxy.component.secure;

import io.vproxy.base.connection.Protocol;
import io.vproxy.base.util.Networks;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.IP;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SecurityGroup {
    public static final String defaultName = "(allow-all)";
    public static final String defaultDenyName = "(deny-all)";

    public final String alias;
    public boolean defaultAllow;
    private final Networks<SecurityGroupRules> tcpRules = new Networks<>();
    private final Networks<SecurityGroupRules> udpRules = new Networks<>();

    public SecurityGroup(String alias, boolean defaultAllow) {
        this.alias = alias;
        this.defaultAllow = defaultAllow;
    }

    public static SecurityGroup allowAll() {
        return new SecurityGroup(defaultName, true);
    }

    public static SecurityGroup denyAll() {
        return new SecurityGroup(defaultDenyName, true);
    }

    public boolean allow(Protocol protocol, IP address, int port) {
        Networks<SecurityGroupRules> rules;
        if (protocol == Protocol.TCP) {
            rules = tcpRules;
        } else {
            assert protocol == Protocol.UDP;
            rules = udpRules;
        }
        var groups = rules.lookup(address);
        if (groups != null) {
            var rule = groups.lookupByPort(port);
            if (rule != null) {
                return rule.allow;
            }
        }
        return defaultAllow;
    }

    public List<SecurityGroupRule> getRules() {
        List<SecurityGroupRule> rules = new ArrayList<>();
        this.tcpRules.forEach(rr -> rules.addAll(rr.rules));
        this.udpRules.forEach(rr -> rules.addAll(rr.rules));
        return rules;
    }

    public void addRule(SecurityGroupRule rule) throws AlreadyExistException {
        if (getRules().stream().anyMatch(r -> r.alias.equals(rule.alias)))
            throw new AlreadyExistException("security-group-rule in security-group " + this.alias, rule.alias);

        List<SecurityGroupRule> rules;
        if (rule.protocol == Protocol.TCP) {
            var rr = tcpRules.lookup(rule.network);
            if (rr == null) {
                rr = new SecurityGroupRules();
                tcpRules.add(rule.network, rr);
            }
            rules = rr.rules;
        } else {
            assert rule.protocol == Protocol.UDP;
            var rr = udpRules.lookup(rule.network);
            if (rr == null) {
                rr = new SecurityGroupRules();
                udpRules.add(rule.network, rr);
            }
            rules = rr.rules;
        }
        // check ip mask
        for (SecurityGroupRule r : rules) {
            if (r.ipMaskMatch(rule) &&
                r.protocol == rule.protocol &&
                r.minPort == rule.minPort &&
                r.maxPort == rule.maxPort)
                throw new AlreadyExistException("security-group-rule " + r + " already exists in security-group " + this.alias);
        }
        rules.add(rule);
    }

    public void removeRule(String name) throws NotFoundException {
        boolean[] removed = {false};
        tcpRules.forEach(rr -> {
            if (rr.rules.removeIf(r -> r.alias.equals(name))) {
                removed[0] = true;
            }
        });
        udpRules.forEach(rr -> {
            if (rr.rules.removeIf(r -> r.alias.equals(name))) {
                removed[0] = true;
            }
        });
        tcpRules.removeBy(rr -> rr.rules.isEmpty());
        udpRules.removeBy(rr -> rr.rules.isEmpty());

        if (!removed[0])
            throw new NotFoundException("security-group-rule in security-group " + this.alias, name);
    }

    @Override
    public String toString() {
        return alias + " -> default " + (defaultAllow ? "allow" : "deny");
    }

    private static class SecurityGroupRules implements Networks.Rule {
        public final List<SecurityGroupRule> rules = new CopyOnWriteArrayList<>();

        public SecurityGroupRule lookupByPort(int port) {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, size = rules.size(); i < size; ++i) {
                var rule = rules.get(i);
                if (rule.matchByPort(port)) {
                    return rule;
                }
            }
            return null;
        }
    }
}
