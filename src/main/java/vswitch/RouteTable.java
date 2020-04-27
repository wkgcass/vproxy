package vswitch;

import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.util.Network;
import vproxy.util.Utils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class RouteTable {
    private final List<RouteRule> rules = new ArrayList<>();

    public RouteRule lookup(InetAddress ip) {
        for (RouteRule r : rules) {
            if (r.rule.contains(ip)) {
                return r;
            }
        }
        return null;
    }

    public List<RouteRule> getRules() {
        return rules;
    }

    public void addRule(RouteRule r) throws AlreadyExistException {
        for (RouteRule rr : rules) {
            if (rr.alias.equals(r.alias)) {
                throw new AlreadyExistException("route", r.alias);
            }
            if (rr.rule.equals(r.rule)) {
                throw new AlreadyExistException("route " + rr.alias + " has the same network rule as the adding one: " + r.rule);
            }
        }

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
        for (int i = 0; i < rules.size(); ++i) {
            var ri = rules.get(i);
            if (ri.alias.equals(alias)) {
                rules.remove(i);
                return;
            }
        }
        throw new NotFoundException("route", alias);
    }

    public static class RouteRule {
        public final String alias;
        public final Network rule;
        public final int toVni;
        public final InetAddress ip;

        public RouteRule(String alias, Network rule, int toVni) {
            this.alias = alias;
            this.rule = rule;
            this.toVni = toVni;
            this.ip = null;
        }

        public RouteRule(String alias, Network rule, InetAddress ip) {
            this.alias = alias;
            this.rule = rule;
            this.toVni = 0;
            this.ip = ip;
        }

        @Override
        public String toString() {
            return alias + " -> network " + rule + (
                ip == null ? (" vni " + toVni) : (" address " + Utils.ipStr(ip))
            );
        }
    }
}
