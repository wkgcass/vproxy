package io.vproxy.vproxyx.nexus.entity;

import vjson.deserializer.rule.ArrayRule;
import vjson.deserializer.rule.ObjectRule;
import vjson.deserializer.rule.Rule;

import java.util.ArrayList;
import java.util.List;

public class NexusConfiguration {
    public List<ProxyInstance> proxies;

    public static final Rule<NexusConfiguration> rule = new ObjectRule<>(NexusConfiguration::new)
        .put("proxies", (o, it) -> o.proxies = it, new ArrayRule<List<ProxyInstance>, ProxyInstance>(
            ArrayList::new, List::add, ProxyInstance.rule
        ));
}
