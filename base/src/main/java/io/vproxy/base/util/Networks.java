package io.vproxy.base.util;

import io.vproxy.base.util.functional.ConsumerEx;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Networks<R extends Networks.Rule> {
    private final List<V4RulesGroupedByMask> v4Rules = new ArrayList<>(32);
    private final List<V6RulesGroupedByMask> v6Rules = new ArrayList<>(128);

    public interface Rule {
    }

    private R cast(Rule r) {
        //noinspection unchecked
        return (R) r;
    }

    public R lookup(Network net) {
        if (net instanceof NetworkV4) {
            int mask = net.getMask();
            for (var rules : v4Rules) {
                if (rules.mask == mask) {
                    return cast(rules.rules.get(((IPv4) net.getIp()).getIPv4Value()));
                }
            }
            return null;
        } else if (net instanceof NetworkV6) {
            int mask = net.getMask();
            for (var rules : v6Rules) {
                if (rules.mask == mask) {
                    return cast(rules.rules.get(((IPv6) net.getIp()).getIPv6Values()));
                }
            }
            return null;
        } else {
            throw new IllegalArgumentException("unexpected network " + net);
        }
    }

    public R lookup(IP ip) {
        if (ip instanceof IPv4) {
            var res = cast(lookupV4((IPv4) ip));
            if (res == null) {
                var v6 = ip.to6();
                if (v6 != null) {
                    res = cast(lookupV6(v6));
                }
            }
            return res;
        } else if (ip instanceof IPv6) {
            var res = cast(lookupV6((IPv6) ip));
            if (res == null) {
                var v4 = ip.to4();
                if (v4 != null) {
                    res = cast(lookupV4(v4));
                }
            }
            return res;
        } else {
            throw new IllegalArgumentException("unexpected ip " + ip);
        }
    }

    private Rule lookupV4(IPv4 ip) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = v4Rules.size(); i < size; ++i) {
            var rules = v4Rules.get(i);
            var v4 = ip.maskValue(rules.mask);
            var rule = rules.rules.get(v4);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }

    private Rule lookupV6(IPv6 ip) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = v6Rules.size(); i < size; ++i) {
            var rules = v6Rules.get(i);
            var v6 = ip.maskValues(rules.mask);
            var rule = rules.rules.get(v6);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }

    public R add(Network net, R rule) {
        if (net instanceof NetworkV4) {
            return cast(addV4(net, rule));
        } else if (net instanceof NetworkV6) {
            return cast(addV6(net, rule));
        } else {
            throw new IllegalArgumentException("unexpected network " + net);
        }
    }

    private Rule addV4(Network net, R rule) {
        int mask = net.getMask();
        V4RulesGroupedByMask group = null;
        for (int i = 0; i < v4Rules.size(); ++i) {
            var rules = v4Rules.get(i);
            if (rules.mask > mask) {
                continue;
            } else if (rules.mask == mask) {
                group = rules;
                break;
            }
            group = new V4RulesGroupedByMask(mask);
            v4Rules.add(i, group);
            break;
        }
        if (group == null) {
            group = new V4RulesGroupedByMask(mask);
            v4Rules.add(group);
        }
        return group.rules.put(((IPv4) net.getIp()).getIPv4Value(), rule);
    }

    private Rule addV6(Network net, R rule) {
        int mask = net.getMask();
        V6RulesGroupedByMask group = null;
        for (int i = 0; i < v6Rules.size(); ++i) {
            var rules = v6Rules.get(i);
            if (rules.mask > mask) {
                continue;
            } else if (rules.mask == mask) {
                group = rules;
                break;
            }
            group = new V6RulesGroupedByMask(mask);
            v6Rules.add(i, group);
            break;
        }
        if (group == null) {
            group = new V6RulesGroupedByMask(mask);
            v6Rules.add(group);
        }
        return group.rules.put(((IPv6) net.getIp()).getIPv6Values(), rule);
    }

    public R remove(Network net) {
        if (net instanceof NetworkV4) {
            return cast(removeV4(net));
        } else if (net instanceof NetworkV6) {
            return cast(removeV6(net));
        } else {
            throw new IllegalArgumentException("unexpected network " + net);
        }
    }

    private Rule removeV4(Network net) {
        int mask = net.getMask();
        for (int i = 0; i < v4Rules.size(); ++i) {
            var rules = v4Rules.get(i);
            if (rules.mask < mask) {
                break;
            } else if (rules.mask > mask) {
                continue;
            }
            var ret = rules.rules.remove(((IPv4) net.getIp()).getIPv4Value());
            if (rules.rules.isEmpty()) {
                v4Rules.remove(i);
            }
            return ret;
        }
        return null;
    }

    private Rule removeV6(Network net) {
        int mask = net.getMask();
        for (int i = 0; i < v6Rules.size(); ++i) {
            var rules = v6Rules.get(i);
            if (rules.mask < mask) {
                break;
            } else if (rules.mask > mask) {
                continue;
            }
            var ret = rules.rules.remove(((IPv6) net.getIp()).getIPv6Values());
            if (rules.rules.isEmpty()) {
                v6Rules.remove(i);
            }
            return ret;
        }
        return null;
    }

    public <EX extends Throwable> void forEach(ConsumerEx<R, EX> f) throws EX {
        for (var rules : v4Rules) {
            for (var rule : rules.rules.values()) {
                f.accept(cast(rule));
            }
        }
        for (var rules : v6Rules) {
            for (var rule : rules.rules.values()) {
                f.accept(cast(rule));
            }
        }
    }

    public R removeBy(Predicate<R> f) {
        for (var iter = v4Rules.iterator(); iter.hasNext(); ) {
            V4RulesGroupedByMask rules = iter.next();
            for (var iterator = rules.rules.values().iterator(); iterator.hasNext(); ) {
                Rule rule = iterator.next();
                if (f.test(cast(rule))) {
                    iterator.remove();
                    if (rules.rules.isEmpty()) {
                        iter.remove();
                    }
                    return cast(rule);
                }
            }
        }
        for (var iter = v6Rules.iterator(); iter.hasNext(); ) {
            V6RulesGroupedByMask rules = iter.next();
            for (var iterator = rules.rules.values().iterator(); iterator.hasNext(); ) {
                Rule rule = iterator.next();
                if (f.test(cast(rule))) {
                    iterator.remove();
                    if (rules.rules.isEmpty()) {
                        iter.remove();
                    }
                    return cast(rule);
                }
            }
        }
        return null;
    }

    public List<R> getRules() {
        List<R> res = new ArrayList<>();
        forEach(res::add);
        return res;
    }

    private static final class V4RulesGroupedByMask {
        final int mask;
        final Map<Integer, Rule> rules = new HashMap<>();

        private V4RulesGroupedByMask(int mask) {
            this.mask = mask;
        }

        @Override
        public String toString() {
            return "{" +
                "mask=" + mask +
                ", rules=" + rules +
                '}';
        }
    }

    private static final class V6RulesGroupedByMask {
        final int mask;
        final Map<IPv6.Values, Rule> rules = new HashMap<>();

        private V6RulesGroupedByMask(int mask) {
            this.mask = mask;
        }
    }
}
