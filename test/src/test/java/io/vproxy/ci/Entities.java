package io.vproxy.ci;

import vjson.JSON;
import io.vproxy.vfd.IPPort;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SuppressWarnings("unused")
public class Entities {
    private Entities() {
    }

    enum LoadBalancingMethod {
        wrr,
        wlc,
        source,
    }

    enum Protocol {
        tcp("tcp"),
        http("http"),
        h2("h2"),
        http1_x("http/1.x"),
        framed_int32("framed-int32"),
        dubbo("dubbo"),
        ;
        public final String name;

        Protocol(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum SecurityGroupProtocol {
        TCP,
        UDP,
    }

    enum Rule {
        allow,
        deny,
    }

    enum IPType {
        v4,
        v6,
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Sticky {
        int value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Optional {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Modifiable {
    }

    static class TcpLB {
        String name;
        IPPort address;
        String backend;
        @Optional Protocol protocol;
        @Optional String acceptorLoopGroup;
        @Optional String workerLoopGroup;
        @Optional @Modifiable int inBufferSize;
        @Optional @Modifiable int outBufferSize;
        @Optional String[] listOfCertKey;
        @Optional @Modifiable String securityGroup;
    }

    static class TcpLBWithTLS {
        String name;
        IPPort address;
        String backend;
        @Optional Protocol protocol;
        @Optional String acceptorLoopGroup;
        @Optional String workerLoopGroup;
        @Optional @Modifiable int inBufferSize;
        @Optional @Modifiable int outBufferSize;
        @Modifiable String[] listOfCertKey;
        @Optional @Modifiable String securityGroup;
    }

    static class Socks5Server {
        String name;
        IPPort address;
        String backend;
        @Optional String acceptorLoopGroup;
        @Optional String workerLoopGroup;
        @Optional @Modifiable int inBufferSize;
        @Optional @Modifiable int outBufferSize;
        @Optional @Modifiable String securityGroup;
        @Optional @Modifiable boolean allowNonBackend;
    }

    static class DNSServer {
        String name;
        IPPort address;
        String rrsets;
        @Optional @Modifiable int ttl;
        @Optional String eventLoopGroup;
        @Optional @Modifiable String securityGroup;
    }

    static class EventLoop {
        String name;
    }

    static class ServerGroupInUpstream {
        String name;
        @Optional @Modifiable int weight;
        @Optional @Modifiable JSON.Object annotations;
    }

    static class Upstream {
        String name;
    }

    static class Server {
        String name;
        IPPort address;
        @Optional @Modifiable int weight;
    }

    static class ServerGroup {
        String name;
        @Modifiable @Sticky(1) int timeout;
        @Modifiable @Sticky(1) int period;
        @Modifiable @Sticky(1) int up;
        @Modifiable @Sticky(1) int down;
        @Modifiable @Sticky(1) String protocol;
        @Modifiable @Optional LoadBalancingMethod method;
        @Modifiable @Optional JSON.Object annotations;
        @Optional String eventLoopGroup;
    }

    // the base code does not support sticky fields with optional tag
    // so create another class without the protocol field
    static class ServerGroupNoProtocol {
        String name;
        @Modifiable @Sticky(1) int timeout;
        @Modifiable @Sticky(1) int period;
        @Modifiable @Sticky(1) int up;
        @Modifiable @Sticky(1) int down;
        @Modifiable @Optional LoadBalancingMethod method;
        @Modifiable @Optional JSON.Object annotations;
        @Optional String eventLoopGroup;
    }

    static class SecurityGroupRule {
        String name;
        String clientNetwork;
        SecurityGroupProtocol protocol;
        int serverPortMin;
        int serverPortMax;
        Rule rule;
    }

    static class SecurityGroup {
        String name;
        @Modifiable Rule defaultRule;
    }

    static class CertKey {
        String name;
        String[] certs;
        String key;
    }

    static class SmartGroupDelegate {
        String name;
        String service;
        String zone;
        String handledGroup;
    }

    static class SmartNodeDelegate {
        String name;
        String service;
        String zone;
        String nic;
        @Optional IPType ipType;
        @Optional int exposedPort;
        @Optional int weight;
    }
}
