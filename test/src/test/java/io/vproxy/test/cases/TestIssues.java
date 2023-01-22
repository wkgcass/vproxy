package io.vproxy.test.cases;

import io.vproxy.dep.vjson.CharStream;
import io.vproxy.dep.vjson.JSON;
import io.vproxy.dep.vjson.cs.LineColCharStream;
import io.vproxy.lib.docker.entity.Network;
import org.junit.Test;

public class TestIssues {
    @Test
    public void dockerPluginDeserialize() {
        var str = "[{\"Name\":\"host\",\"Id\":\"45d39ff910ef8c13bf26e504ef8754fbf20371dade719a914dcb483d3c3f4c25\",\"Created\":\"2023-01-22T09:07:06.284942791Z\",\"Scope\":\"local\",\"Driver\":\"host\",\"EnableIPv6\":false,\"IPAM\":{\"Driver\":\"default\",\"Options\":null,\"Config\":[]},\"Internal\":false,\"Attachable\":false,\"Ingress\":false,\"ConfigFrom\":{\"Network\":\"\"},\"ConfigOnly\":false,\"Containers\":{},\"Options\":{},\"Labels\":{}},{\"Name\":\"bridge\",\"Id\":\"1523aa22fbbe2da8f8801ca7133d563b88a73d43a4e0a3c082c771385802a9db\",\"Created\":\"2023-01-22T12:16:34.029594176Z\",\"Scope\":\"local\",\"Driver\":\"bridge\",\"EnableIPv6\":false,\"IPAM\":{\"Driver\":\"default\",\"Options\":null,\"Config\":[{\"Subnet\":\"172.17.0.0/16\",\"Gateway\":\"172.17.0.1\"}]},\"Internal\":false,\"Attachable\":false,\"Ingress\":false,\"ConfigFrom\":{\"Network\":\"\"},\"ConfigOnly\":false,\"Containers\":{},\"Options\":{\"com.docker.network.bridge.default_bridge\":\"true\",\"com.docker.network.bridge.enable_icc\":\"true\",\"com.docker.network.bridge.enable_ip_masquerade\":\"true\",\"com.docker.network.bridge.host_binding_ipv4\":\"0.0.0.0\",\"com.docker.network.bridge.name\":\"docker0\",\"com.docker.network.driver.mtu\":\"1500\"},\"Labels\":{}},{\"Name\":\"none\",\"Id\":\"62fa13e13515a19f6d8261685cab90fe4f6c70704d588866259fe88084fc75e7\",\"Created\":\"2023-01-22T09:07:06.269911333Z\",\"Scope\":\"local\",\"Driver\":\"null\",\"EnableIPv6\":false,\"IPAM\":{\"Driver\":\"default\",\"Options\":null,\"Config\":[]},\"Internal\":false,\"Attachable\":false,\"Ingress\":false,\"ConfigFrom\":{\"Network\":\"\"},\"ConfigOnly\":false,\"Containers\":{},\"Options\":{},\"Labels\":{}}]";
        JSON.deserialize(new LineColCharStream(CharStream.from(str), ""), Network.Companion.getArrayRule());
    }
}
