package net.cassite.vproxy.component.secure;

import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.connection.Protocol;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class SecurityGroup {
    public static final String defaultName = "(allow all)";

    public final String alias;
    public final boolean defaultAllow;
    private LinkedList<SecurityGroupRule> tcpRules = new LinkedList<>();
    private LinkedList<SecurityGroupRule> udpRules = new LinkedList<>();

    public SecurityGroup(String alias, boolean defaultAllow) {
        this.alias = alias;
        this.defaultAllow = defaultAllow;
    }

    public static SecurityGroup allowAll() {
        return new SecurityGroup(defaultName, true);
    }

    public boolean allow(Protocol protocol, InetSocketAddress address) {
        LinkedList<SecurityGroupRule> rules;
        if (protocol == Protocol.TCP) {
            rules = tcpRules;
        } else {
            assert protocol == Protocol.UDP;
            rules = udpRules;
        }
        if (rules.isEmpty())
            return defaultAllow;
        for (SecurityGroupRule rule : rules) {
            if (rule.match(address))
                return rule.allow;
        }
        return defaultAllow;
    }

    public List<SecurityGroupRule> getRules() {
        LinkedList<SecurityGroupRule> tcpRules = this.tcpRules;
        LinkedList<SecurityGroupRule> udpRules = this.udpRules;
        List<SecurityGroupRule> rules = new ArrayList<>(tcpRules.size() + udpRules.size());
        rules.addAll(tcpRules);
        rules.addAll(udpRules);
        return rules;
    }

    public void addRule(SecurityGroupRule rule) throws AlreadyExistException {
        if (getRules().stream().anyMatch(r -> r.alias.equals(rule.alias)))
            throw new AlreadyExistException();

        LinkedList<SecurityGroupRule> rules;
        if (rule.protocol == Protocol.TCP) {
            rules = new LinkedList<>(tcpRules);
        } else {
            assert rule.protocol == Protocol.UDP;
            rules = new LinkedList<>(udpRules);
        }
        // check ip mask
        for (SecurityGroupRule r : rules) {
            if (r.ipMaskMatch(rule))
                throw new AlreadyExistException();
        }
        rules.add(rule);
        if (rule.protocol == Protocol.TCP) {
            this.tcpRules = rules;
        } else {
            //noinspection ConstantConditions
            assert rule.protocol == Protocol.UDP;
            this.udpRules = rules;
        }
    }

    public void removeRule(String name) throws NotFoundException {
        LinkedList<SecurityGroupRule> tcpRules = this.tcpRules;
        LinkedList<SecurityGroupRule> udpRules = this.udpRules;

        List<SecurityGroupRule> oldRules = new ArrayList<>(tcpRules.size() + udpRules.size());
        oldRules.addAll(tcpRules);
        oldRules.addAll(udpRules);
        Optional<SecurityGroupRule> optRule = oldRules.stream().filter(r -> r.alias.equals(name)).findFirst();
        if (!optRule.isPresent())
            throw new NotFoundException();
        if (optRule.get().protocol == Protocol.TCP) {
            tcpRules.remove(optRule.get());
            this.tcpRules = tcpRules;
        } else {
            assert optRule.get().protocol == Protocol.UDP;
            udpRules.remove(optRule.get());
            this.udpRules = udpRules;
        }
    }

    @Override
    public String toString() {
        return alias + " -> default " + (defaultAllow ? "allow" : "deny");
    }
}
