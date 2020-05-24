package vswitch;

import vfd.IP;
import vfd.IPv4;
import vfd.IPv6;
import vproxybase.util.Network;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.NotFoundException;
import vproxybase.util.exception.XException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RouteTable {
    public static final String defaultRuleName = "default";
    public static final String defaultRuleV6Name = "default-v6";

    private final RouteRule defaultV4Rule;
    private final RouteRule defaultV6Rule;

    private final List<RouteRule> rulesV4 = new ArrayList<>();
    private final List<RouteRule> rulesV6 = new ArrayList<>();

    public RouteTable() {
        this.defaultV4Rule = null;
        this.defaultV6Rule = null;
    }

    public RouteTable(Table t) {
        this.defaultV4Rule = new RouteRule(defaultRuleName, t.v4network, t.vni);
        RouteRule defaultV6Rule = null;
        if (t.v6network != null) {
            defaultV6Rule = new RouteRule(defaultRuleV6Name, t.v6network, t.vni);
        }
        this.defaultV6Rule = defaultV6Rule;

        rulesV4.add(defaultV4Rule);
        if (defaultV6Rule != null) {
            rulesV6.add(defaultV6Rule);
        }
    }

    public RouteRule lookup(IP ip) {
        if (ip instanceof IPv4) {
            for (RouteRule r : rulesV4) {
                if (r.rule.contains(ip)) {
                    return r;
                }
            }
        } else {
            for (RouteRule r : rulesV6) {
                if (r.rule.contains(ip)) {
                    return r;
                }
            }
        }
        return null;
    }

    public List<RouteRule> getRules() {
        List<RouteRule> ret = new ArrayList<>(rulesV4.size() + rulesV6.size());
        ret.addAll(rulesV4);
        ret.addAll(rulesV6);
        return ret;
    }

    public void addRule(RouteRule r) throws AlreadyExistException, XException {
        for (RouteRule rr : rulesV4) {
            if (rr.alias.equals(r.alias)) {
                throw new AlreadyExistException("route", r.alias);
            }
            if (rr.rule.equals(r.rule)) {
                throw new AlreadyExistException("route " + rr.alias + " has the same network rule as the adding one: " + r.rule);
            }
        }
        for (RouteRule rr : rulesV6) {
            if (rr.alias.equals(r.alias)) {
                throw new AlreadyExistException("route", r.alias);
            }
            if (rr.rule.equals(r.rule)) {
                throw new AlreadyExistException("route " + rr.alias + " has the same network rule as the adding one: " + r.rule);
            }
        }

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

        if (r.rule.getRawIpBytes().length == 4) {
            addRule(r, rulesV4);
        } else {
            addRule(r, rulesV6);
        }
    }

    private void addRule(RouteRule r, List<RouteRule> rules) {
        // try to find rules that contain each other
        int similarRule = -1;
        for (int i = 0; i < rules.size(); ++i) {
            RouteRule ri = rules.get(i);
            if (ri.rule.contains(r.rule) || r.rule.contains(ri.rule)) {
                similarRule = i;
                break;
            }
        }

        if (similarRule == -1) { // no crossing among all rules
            rules.add(r);
            return;
        }

        // find a place to insert the rule
        int insertIndex = 0;
        for (int i = similarRule; i < rules.size(); ++i) {
            RouteRule curr = rules.get(i);
            RouteRule next = (i + 1) < rules.size() ? rules.get(i + 1) : null;
            if (curr.rule.contains(r.rule)) { // the route at [i] contains the adding rule, so the adding rule should be placed before [i] rule
                insertIndex = i;
                break;
            }
            if (r.rule.contains(curr.rule)) { // should be placed after [i] rule
                if (next == null) {
                    insertIndex = i + 1;
                    break;
                }
                if (r.rule.contains(next.rule)) {
                    continue;
                }
                if (next.rule.contains(r.rule)) {
                    insertIndex = i + 1;
                    break;
                }
            }
            // reaches here means the next rule has nothing to do with this network
            // so place the new rule after (next to) [i] rule
            insertIndex = i + 1;
            break;
        }
        rules.add(insertIndex, r);
    }

    public void delRule(String alias) throws NotFoundException {
        for (int i = 0; i < rulesV4.size(); ++i) {
            var ri = rulesV4.get(i);
            if (ri.alias.equals(alias)) {
                rulesV4.remove(i);
                return;
            }
        }
        for (int i = 0; i < rulesV6.size(); ++i) {
            var ri = rulesV6.get(i);
            if (ri.alias.equals(alias)) {
                rulesV6.remove(i);
                return;
            }
        }
        throw new NotFoundException("route", alias);
    }

    @Override
    public String toString() {
        return "RouteTable{" +
            "rulesV4=" + rulesV4 +
            ", rulesV6=" + rulesV6 +
            '}';
    }

    public static class RouteRule {
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
