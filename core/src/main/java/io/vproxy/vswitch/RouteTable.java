package io.vproxy.vswitch;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.Networks;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv6;

import java.util.List;
import java.util.Objects;

public class RouteTable {
    public static final String defaultRuleName = "default";
    public static final String defaultRuleV6Name = "default-v6";

    private final RouteRule defaultV4Rule;
    private final RouteRule defaultV6Rule;

    private final Networks<RouteRule> rules = new Networks<>();

    public RouteTable() {
        this.defaultV4Rule = null;
        this.defaultV6Rule = null;
    }

    public RouteTable(VirtualNetwork n) {
        this.defaultV4Rule = new RouteRule(defaultRuleName, n.v4network, n.vni);
        RouteRule defaultV6Rule = null;
        if (n.v6network != null) {
            defaultV6Rule = new RouteRule(defaultRuleV6Name, n.v6network, n.vni);
        }
        this.defaultV6Rule = defaultV6Rule;

        try {
            addRule(defaultV4Rule);
        } catch (Throwable t) {
            Logger.shouldNotHappen("adding default v4 rule failed", t);
        }
        if (defaultV6Rule != null) {
            try {
                addRule(defaultV6Rule);
            } catch (Throwable t) {
                Logger.shouldNotHappen("adding default v6 rule failed", t);
            }
        }
    }

    public RouteRule lookup(IP ip) {
        return rules.lookup(ip);
    }

    public List<RouteRule> getRules() {
        return rules.getRules();
    }

    public void addRule(RouteRule r) throws AlreadyExistException, XException {
        rules.forEach(rr -> {
            if (rr.alias.equals(r.alias)) {
                throw new AlreadyExistException("route", r.alias);
            }
            if (rr.rule.equals(r.rule)) {
                throw new AlreadyExistException("route " + rr.alias + " has the same network rule as the adding one: " + r.rule);
            }
        });

        if (r.alias.equals(defaultRuleName)) {
            if (!r.equals(defaultV4Rule))
                throw new XException("validation failed for the rule with name " + r.alias);
        }
        if (r.alias.equals(defaultRuleV6Name)) {
            if (!r.equals(defaultV6Rule))
                throw new XException("validation failed for the rule with name " + r.alias);
        }
        if (r.ip != null) {
            if (defaultV6Rule == null && r.ip instanceof IPv6) {
                throw new XException("this network does not support ipv6");
            }
            if (!defaultV4Rule.rule.contains(r.ip) && (defaultV6Rule == null || !defaultV6Rule.rule.contains(r.ip))) {
                throw new XException("cannot specify an ip out of the network to redirect packets to");
            }
        }

        rules.add(r.rule, r);
    }

    public void delRule(String alias) throws NotFoundException {
        var removed = rules.removeBy(r -> r.alias.equals(alias));
        if (removed == null) {
            throw new NotFoundException("route", alias);
        }
    }

    @Override
    public String toString() {
        return "RouteTable{" + rules + '}';
    }

    public static class RouteRule implements Networks.Rule {
        public final String alias;
        public final Network rule;
        public final int toVni;
        public final IP ip;

        public RouteRule(String alias, Network rule, int toVni) {
            this.alias = alias;
            this.rule = rule;
            this.toVni = toVni;
            this.ip = null;
        }

        public RouteRule(String alias, Network rule, IP ip) {
            this.alias = alias;
            this.rule = rule;
            this.toVni = 0;
            this.ip = ip;
        }

        public boolean isLocalDirect(int currentVni) {
            return ip == null && (toVni == 0 || toVni == currentVni);
        }

        @Override
        public String toString() {
            return alias + " -> network " + rule + (
                ip == null ? (" vni " + toVni) : (" via " + ip.formatToIPString())
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RouteRule routeRule = (RouteRule) o;
            return toVni == routeRule.toVni &&
                Objects.equals(alias, routeRule.alias) &&
                Objects.equals(rule, routeRule.rule) &&
                Objects.equals(ip, routeRule.ip);
        }

        @Override
        public int hashCode() {
            return Objects.hash(alias, rule, toVni, ip);
        }
    }
}
