package io.vproxy.test.cases;

import io.vproxy.app.plugin.impl.BasePacketFilter;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.bitwise.BitwiseIntMatcher;
import io.vproxy.base.util.bitwise.BitwiseMatcher;
import io.vproxy.base.util.ratelimit.RateLimiter;
import io.vproxy.base.util.ratelimit.SimpleRateLimiter;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.*;
import io.vproxy.vproxyx.pktfiltergen.IfaceHolder;
import io.vproxy.vproxyx.pktfiltergen.flow.Flows;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.plugin.FilterResult;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestFlowGen {
    private static final String EXECUTE = "return execute(helper, pkb, this::action";
    private static final String EXECUTE0 = EXECUTE + "0);";

    private String genTable(int table, String content) {
        String[] split = content.split("\n");
        StringBuilder sb = new StringBuilder();
        sb.append("\n    private FilterResult table").append(table).append("(PacketFilterHelper helper, PacketBuffer pkb) {\n");
        for (String s : split) {
            sb.append(" ".repeat(8)).append(s).append("\n");
        }
        sb.append("    }\n");
        return sb.toString();
    }

    private static final Set<String> ALL_NAMES = new HashSet<>();

    private void fullname(String name) {
        synchronized (ALL_NAMES) {
            if (!ALL_NAMES.add(name)) {
                throw new IllegalStateException(name + " is already used");
            }
        }
        fullname = name;
    }

    private String fullname;
    private List<Class<?>> imports = List.of();
    private String fields = "";
    private String constructor = "";
    private String tables = "";
    private List<String> actions = List.of("return FilterResult.PASS;");
    private String extra = "";

    private void check(String input) throws Exception {
        Flows flows = new Flows();
        flows.add(input);
        String output = flows.gen(fullname);
        String packageName = fullname.substring(0, fullname.lastIndexOf('.'));
        String classSimpleName = fullname.substring(fullname.lastIndexOf('.') + 1);
        StringBuilder expected = new StringBuilder();
        expected
            .append("package ").append(packageName).append(";\n")
            .append("\n");
        var imports = new ArrayList<String>();
        imports.add(BasePacketFilter.class.getName());
        imports.add(FilterResult.class.getName());
        imports.add(PacketFilterHelper.class.getName());
        imports.add(PacketBuffer.class.getName());
        for (Class<?> cls : this.imports) {
            imports.add(cls.getName());
        }
        imports.sort(String::compareTo);
        for (String cls : imports) {
            expected.append("import ").append(cls).append(";\n");
        }
        expected
            .append("\n")
            .append("public class ").append(classSimpleName).append(" extends BasePacketFilter {\n");
        if (!fields.isEmpty()) {
            String[] split = fields.split("\n");
            for (String s : split) {
                expected.append(" ".repeat(4)).append(s).append("\n");
            }
            expected.append("\n");
        }
        expected
            .append("    public ").append(classSimpleName).append("() {\n")
            .append("        super();\n");
        if (!constructor.isEmpty()) {
            String[] splitCons = constructor.split("\n");
            boolean isFirst = true;
            for (String s : splitCons) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    expected.append("\n");
                }
                expected.append(" ".repeat(8)).append(s);
            }
            expected.append("\n");
        }
        expected
            .append("    }\n")
            .append("\n")
            .append("    @Override\n")
            .append("    protected FilterResult handleIngress(PacketFilterHelper helper, PacketBuffer pkb) {\n")
            .append("        return table0(helper, pkb);\n")
            .append("    }\n")
            .append(tables);
        for (int i = 0; i < actions.size(); ++i) {
            String action = actions.get(i);
            expected.append("\n    private FilterResult action").append(i)
                .append("(PacketFilterHelper helper, PacketBuffer pkb) {\n");
            String[] split = action.split("\n");
            for (String s : split) {
                expected.append("        ").append(s).append("\n");
            }
            expected.append("    }\n");
        }
        if (!extra.isEmpty()) {
            for (String line : extra.split("\n")) {
                expected.append("\n    ").append(line);
            }
            expected.append("\n");
        }
        expected
            .append("}\n");
        assertEquals(expected.toString(), output);

        // check run from idea or gradle
        File dir = new File("./src/test/java/io/vproxy/test/gen/packetfilters");
        if (!dir.exists()) {
            dir = new File("./test/src/test/java/io/vproxy/test/gen/packetfilters");
        }
        if (!dir.exists()) { // skip file output
            return;
        }
        File f = new File(dir.getAbsolutePath() + "/" + classSimpleName + ".java");
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        f.createNewFile();
        try (var fos = new FileOutputStream(f)) {
            fos.write(output.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    @Test
    public void empty() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Empty");
        tables = genTable(0, "return FilterResult.DROP;\n");
        actions = List.of();
        check("");
    }

    @Test
    public void table() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Table");
        tables = genTable(0, "return table1(helper, pkb);")
            + genTable(1, EXECUTE0);
        actions = List.of("return FilterResult.DROP;");
        check("" +
            "action=goto_table:1\n" +
            "table=1,action=drop");
        check("" +
            "table=1,action=drop\n" +
            "action=goto_table:1");
    }

    @Test
    public void in_port() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.InPort");
        imports = List.of(IfaceHolder.class);
        fields = "private final IfaceHolder[] ifaces = new IfaceHolder[]{\n" +
            "    new IfaceHolder(\"xdp:veth0\", null)\n" +
            "};";
        constructor = "" +
            "registerIfaceHolder(this.ifaces[0]);";
        tables = genTable(0, "" +
            "if (pkb.devin == ifaces[0].iface) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;\n"
        );
        check("in_port=xdp:veth0,action=normal");
    }

    @Test
    public void dl_dst() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.DLDst");
        imports = List.of(BitwiseMatcher.class, ByteArray.class);
        fields = "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"abcdef012345\"), " +
            "ByteArray.fromHexString(\"ffffffffffff\"));";
        tables = genTable(0, "" +
            "if (BITWISE_MATCHER_HOLDER_0.match(pkb.pkt.getDst())) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("dl_dst=ab:cd:ef:01:23:45,action=normal");
    }

    @Test
    public void dl_src() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.DLSrc");
        imports = List.of(BitwiseMatcher.class, ByteArray.class);
        fields = "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"abcdef012345\"), " +
            "ByteArray.fromHexString(\"ffffffffffff\"));";
        tables = genTable(0, "" +
            "if (BITWISE_MATCHER_HOLDER_0.match(pkb.pkt.getSrc())) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("dl_src=ab:cd:ef:01:23:45,action=normal");
    }

    @Test
    public void dl_type() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.DLType");
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == " + 0x1234 + ") {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("dl_type=0x1234,action=normal");
    }

    @Test
    public void arp_op() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ArpOp");
        imports = List.of(ArpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2054 && ((ArpPacket) pkb.pkt.getPacket()).getOpcode() == 1) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("arp_op=1,action=normal");
    }

    @Test
    public void arp_spa() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ArpSpa");
        imports = List.of(
            ArpPacket.class,
            BitwiseMatcher.class,
            ByteArray.class
        );
        fields = "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"ac100001\"), " +
            "ByteArray.fromHexString(\"ffffffff\"));";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2054 && BITWISE_MATCHER_HOLDER_0.match(((ArpPacket) pkb.pkt.getPacket()).getSenderIp())) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("arp_spa=172.16.0.1,action=normal");
    }

    @Test
    public void arp_tpa() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ArpTpa");
        imports = List.of(
            ArpPacket.class,
            BitwiseMatcher.class,
            ByteArray.class
        );
        fields = "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"ac100001\"), " +
            "ByteArray.fromHexString(\"ffffffff\"));";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2054 && BITWISE_MATCHER_HOLDER_0.match(((ArpPacket) pkb.pkt.getPacket()).getTargetIp())) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("arp_tpa=172.16.0.1,action=normal");
    }

    @Test
    public void arp_sha() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ArpSha");
        imports = List.of(
            ArpPacket.class,
            BitwiseMatcher.class,
            ByteArray.class
        );
        fields = "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"abcdef012345\"), " +
            "ByteArray.fromHexString(\"ffffffffffff\"));";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2054 && BITWISE_MATCHER_HOLDER_0.match(((ArpPacket) pkb.pkt.getPacket()).getSenderMac())) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("arp_sha=ab:cd:ef:01:23:45,action=normal");
    }

    @Test
    public void arp_tha() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ArpTha");
        imports = List.of(
            ArpPacket.class,
            BitwiseMatcher.class,
            ByteArray.class
        );
        fields = "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"abcdef012345\"), " +
            "ByteArray.fromHexString(\"ffffffffffff\"));";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2054 && BITWISE_MATCHER_HOLDER_0.match(((ArpPacket) pkb.pkt.getPacket()).getTargetMac())) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("arp_tha=ab:cd:ef:01:23:45,action=normal");
    }

    @Test
    public void nw_src() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.NwSrc");
        imports = List.of(
            Ipv4Packet.class,
            AbstractIpPacket.class,
            BitwiseMatcher.class,
            ByteArray.class
        );
        fields = "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"ac100001\"), " +
            "ByteArray.fromHexString(\"ffffffff\"));";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_0.match(((AbstractIpPacket) pkb.pkt.getPacket()).getSrc())) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("nw_src=172.16.0.1,action=normal");
    }

    @Test
    public void nw_dst() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.NwDst");
        imports = List.of(
            Ipv4Packet.class,
            AbstractIpPacket.class,
            BitwiseMatcher.class,
            ByteArray.class
        );
        fields = "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"ac100001\"), " +
            "ByteArray.fromHexString(\"ffffffff\"));";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_0.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst())) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("nw_dst=172.16.0.1,action=normal");
    }

    @Test
    public void nw_proto_ipv4() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.NwProtoIpv4");
        imports = List.of(Ipv4Packet.class, AbstractIpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == " + 0x123 + ") {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("nw_proto=0x123,action=normal");
    }

    @Test
    public void nw_proto_ipv6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.NwProtoIpv6");
        imports = List.of(Ipv6Packet.class, AbstractIpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == " + 0x123 + ") {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("ipv6,nw_proto=0x123,action=normal");
    }

    @Test
    public void tp_src_tcp() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.TpSrcTcp");
        imports = List.of(
            AbstractIpPacket.class,
            Ipv4Packet.class,
            TcpPacket.class,
            BitwiseIntMatcher.class
        );
        fields = "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = " +
            "BitwiseIntMatcher.from(80, -1);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet " +
            "&& ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6 " +
            "&& BITWISE_INT_MATCHER_HOLDER_0.match(" +
            "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()" +
            ")) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("tcp,tp_src=80,action=normal");
    }

    @Test
    public void tp_dst_tcp() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.TpDstTcp");
        imports = List.of(
            AbstractIpPacket.class,
            Ipv4Packet.class,
            TcpPacket.class,
            BitwiseIntMatcher.class
        );
        fields = "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = " +
            "BitwiseIntMatcher.from(80, -1);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet " +
            "&& ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6 " +
            "&& BITWISE_INT_MATCHER_HOLDER_0.match(" +
            "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()" +
            ")) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("tcp,tp_dst=80,action=normal");
    }

    @Test
    public void tp_src_tcp6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.TpSrcTcp6");
        imports = List.of(
            Ipv6Packet.class,
            AbstractIpPacket.class,
            TcpPacket.class,
            BitwiseIntMatcher.class
        );
        fields = "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = " +
            "BitwiseIntMatcher.from(80, -1);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet " +
            "&& ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6 " +
            "&& BITWISE_INT_MATCHER_HOLDER_0.match(" +
            "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()" +
            ")) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("tcp6,tp_src=80,action=normal");
    }

    @Test
    public void tp_dst_tcp6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.TpDstTcp6");
        imports = List.of(
            Ipv6Packet.class,
            AbstractIpPacket.class,
            TcpPacket.class,
            BitwiseIntMatcher.class
        );
        fields = "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = " +
            "BitwiseIntMatcher.from(80, -1);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet " +
            "&& ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6 " +
            "&& BITWISE_INT_MATCHER_HOLDER_0.match(" +
            "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()" +
            ")) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("tcp6,tp_dst=80,action=normal");
    }

    @Test
    public void tp_src_udp() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.TpSrcUdp");
        imports = List.of(
            AbstractIpPacket.class,
            Ipv4Packet.class,
            UdpPacket.class,
            BitwiseIntMatcher.class
        );
        fields = "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = " +
            "BitwiseIntMatcher.from(80, -1);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet " +
            "&& ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 " +
            "&& BITWISE_INT_MATCHER_HOLDER_0.match(" +
            "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()" +
            ")) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("udp,tp_src=80,action=normal");
    }

    @Test
    public void tp_dst_udp() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.TpDstUdp");
        imports = List.of(
            AbstractIpPacket.class,
            Ipv4Packet.class,
            UdpPacket.class,
            BitwiseIntMatcher.class
        );
        fields = "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = " +
            "BitwiseIntMatcher.from(80, -1);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet " +
            "&& ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 " +
            "&& BITWISE_INT_MATCHER_HOLDER_0.match(" +
            "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()" +
            ")) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("udp,tp_dst=80,action=normal");
    }

    @Test
    public void tp_src_udp6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.TpSrcUdp6");
        imports = List.of(
            Ipv6Packet.class,
            AbstractIpPacket.class,
            UdpPacket.class,
            BitwiseIntMatcher.class
        );
        fields = "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = " +
            "BitwiseIntMatcher.from(80, -1);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet " +
            "&& ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 " +
            "&& BITWISE_INT_MATCHER_HOLDER_0.match(" +
            "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()" +
            ")) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("udp6,tp_src=80,action=normal");
    }

    @Test
    public void tp_dst_udp6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.TpDstUdp6");
        imports = List.of(
            Ipv6Packet.class,
            AbstractIpPacket.class,
            UdpPacket.class,
            BitwiseIntMatcher.class
        );
        fields = "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = " +
            "BitwiseIntMatcher.from(80, -1);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet " +
            "&& ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 " +
            "&& BITWISE_INT_MATCHER_HOLDER_0.match(" +
            "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()" +
            ")) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("udp6,tp_dst=80,action=normal");
    }

    @Test
    public void vni() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Vni");
        tables = genTable(0, "" +
            "if (pkb.network != null && pkb.network.vni == 1) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("vni=1,action=normal");
    }

    @Test
    public void predicate() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Predicate");
        tables = genTable(0, "" +
            "if (predicate_myMethod(helper, pkb)) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        extra = "" +
            "protected boolean predicate_myMethod(PacketFilterHelper helper, PacketBuffer pkb) {\n" +
            "    return false;\n" +
            "}";
        check("predicate=myMethod,action=normal");
    }

    @Test
    public void multiMatchers() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.MultiMatchers");
        imports = List.of(
            BitwiseMatcher.class,
            ByteArray.class,
            IfaceHolder.class,
            Ipv4Packet.class
        );
        fields = "" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"abcdef012345\"), " +
            "ByteArray.fromHexString(\"ffffffffffff\"));\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_1 = " +
            "BitwiseMatcher.from(" +
            "ByteArray.fromHexString(\"fedcba098765\"), " +
            "ByteArray.fromHexString(\"ffffffffffff\"));\n" +
            "private final IfaceHolder[] ifaces = new IfaceHolder[]{\n" +
            "    new IfaceHolder(\"xdp:veth0\", null),\n" +
            "    new IfaceHolder(\"xdp:veth1\", null)\n" +
            "};";
        constructor = "" +
            "registerIfaceHolder(this.ifaces[0]);\n" +
            "registerIfaceHolder(this.ifaces[1]);";
        tables = genTable(0, "" +
            "if (pkb.devin == ifaces[0].iface) {\n" +
            "    " + EXECUTE + "0);\n" +
            "}\n" +
            "if (pkb.devin == ifaces[1].iface) {\n" +
            "    " + EXECUTE + "1);\n" +
            "}\n" +
            "if (BITWISE_MATCHER_HOLDER_0.match(pkb.pkt.getDst())) {\n" +
            "    return table1(helper, pkb);\n" +
            "}\n" +
            EXECUTE + "2);\n") +
            genTable(1, "" +
                "if (BITWISE_MATCHER_HOLDER_1.match(pkb.pkt.getSrc())) {\n" +
                "    return table3(helper, pkb);\n" +
                "}\n" +
                "return table2(helper, pkb);") +
            genTable(2, "" +
                "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
                "    " + EXECUTE + "3);\n" +
                "}\n" +
                "return table3(helper, pkb);\n") +
            genTable(3, "" +
                EXECUTE + "4);\n");
        actions = List.of(
            "return FilterResult.PASS;",
            "return FilterResult.PASS;",
            "return FilterResult.PASS;",
            "return FilterResult.PASS;",
            "return FilterResult.PASS;"
        );
        check("" +
            "table=0,priority=900,in_port=xdp:veth0,action=normal\n" +
            "table=0,priority=500,in_port=xdp:veth1,action=normal\n" +
            "table=0,priority=100,dl_dst=ab:cd:ef:01:23:45,action=goto_table:1\n" +
            "table=0,priority=0,action=normal\n" +
            "table=1,priority=900,dl_src=fe:dc:ba:09:87:65,action=goto_table:3\n" +
            "table=1,priority=0,action=goto_table:2\n" +
            "table=2,priority=900,ip,action=normal\n" +
            "table=2,priority=0,action=goto_table:3\n" +
            "table=3,action=normal" +
            "");
    }

    @Test
    public void normal() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Normal");
        tables = genTable(0, EXECUTE0);
        actions = List.of("return FilterResult.PASS;");
        check("action=normal");
        imports = List.of(Ipv4Packet.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of("return FilterResult.PASS;");
        check("ip,action=normal");
    }

    @Test
    public void drop() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Drop");
        tables = genTable(0, EXECUTE0);
        actions = List.of("return FilterResult.DROP;");
        check("action=drop");
        imports = List.of(Ipv4Packet.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of("return FilterResult.DROP;");
        check("ip,action=drop");
    }

    @Test
    public void mod_dl_dst() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModDLDst");
        imports = List.of(MacAddress.class);
        fields = "private static final MacAddress MAC_HOLDER_ab_cd_ef_01_23_45 = new MacAddress(\"ab:cd:ef:01:23:45\");";
        tables = genTable(0, EXECUTE0);
        actions = List.of(
            "" +
                "pkb.pkt.setDst(MAC_HOLDER_ab_cd_ef_01_23_45);\n" +
                "return FilterResult.PASS;"
        );
        check("actions=mod_dl_dst:ab:cd:ef:01:23:45,normal");

        imports = List.of(MacAddress.class, Ipv4Packet.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;\n");
        actions = List.of(
            "" +
                "pkb.pkt.setDst(MAC_HOLDER_ab_cd_ef_01_23_45);\n" +
                "return FilterResult.PASS;"
        );
        check("ip,actions=mod_dl_dst:ab:cd:ef:01:23:45,normal");
    }

    @Test
    public void mod_dl_src() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModDLSrc");
        imports = List.of(MacAddress.class);
        fields = "private static final MacAddress MAC_HOLDER_ab_cd_ef_01_23_45 = new MacAddress(\"ab:cd:ef:01:23:45\");";
        tables = genTable(0, EXECUTE0);
        actions = List.of(
            "" +
                "pkb.pkt.setSrc(MAC_HOLDER_ab_cd_ef_01_23_45);\n" +
                "return FilterResult.PASS;"
        );
        check("actions=mod_dl_src:ab:cd:ef:01:23:45,normal");

        imports = List.of(Ipv4Packet.class, MacAddress.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;\n");
        actions = List.of(
            "" +
                "pkb.pkt.setSrc(MAC_HOLDER_ab_cd_ef_01_23_45);\n" +
                "return FilterResult.PASS;"
        );
        check("ip,actions=mod_dl_src:ab:cd:ef:01:23:45,normal");
    }

    @Test
    public void mod_nw_src_v4() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModNwSrcV4");
        imports = List.of(Ipv4Packet.class, IPv4.class, IP.class);
        fields = "private static final IPv4 IPv4_HOLDER_172_16_0_1 = (IPv4) IP.from(\"172.16.0.1\");";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((Ipv4Packet) pkb.pkt.getPacket()).setSrc(IPv4_HOLDER_172_16_0_1);\n" +
                "return FilterResult.PASS;"
        );
        check("ip,actions=mod_nw_src:172.16.0.1,normal");
    }

    @Test
    public void mod_nw_dst_v4() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModNwDstV4");
        imports = List.of(Ipv4Packet.class, IPv4.class, IP.class);
        fields = "private static final IPv4 IPv4_HOLDER_172_16_0_1 = (IPv4) IP.from(\"172.16.0.1\");";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((Ipv4Packet) pkb.pkt.getPacket()).setDst(IPv4_HOLDER_172_16_0_1);\n" +
                "return FilterResult.PASS;"
        );
        check("ip,actions=mod_nw_dst:172.16.0.1,normal");
    }

    @Test
    public void mod_nw_src_v6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModNwSrcV6");
        imports = List.of(Ipv6Packet.class, IPv6.class, IP.class);
        fields = "private static final IPv6 IPv6_HOLDER_fd00_abcd__1 = (IPv6) IP.from(\"fd00:abcd::1\");";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((Ipv6Packet) pkb.pkt.getPacket()).setSrc(IPv6_HOLDER_fd00_abcd__1);\n" +
                "return FilterResult.PASS;"
        );
        check("ipv6,actions=mod_nw_src:fd00:abcd::1,normal");
    }

    @Test
    public void mod_nw_dst_v6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModNwDstV6");
        imports = List.of(Ipv6Packet.class, IPv6.class, IP.class);
        fields = "private static final IPv6 IPv6_HOLDER_fd00_abcd__1 = (IPv6) IP.from(\"fd00:abcd::1\");";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((Ipv6Packet) pkb.pkt.getPacket()).setDst(IPv6_HOLDER_fd00_abcd__1);\n" +
                "return FilterResult.PASS;"
        );
        check("ipv6,actions=mod_nw_dst:fd00:abcd::1,normal");
    }

    @Test
    public void mod_tp_src_tcp() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModTPSrcTcp");
        imports = List.of(Ipv4Packet.class, AbstractIpPacket.class, TcpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).setSrcPort(1234);\n" +
                "return FilterResult.PASS;"
        );
        check("tcp,actions=mod_tp_src:1234,normal");
    }

    @Test
    public void mod_tp_dst_tcp() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModTPDstTcp");
        imports = List.of(Ipv4Packet.class, AbstractIpPacket.class, TcpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).setDstPort(1234);\n" +
                "return FilterResult.PASS;");
        check("tcp,actions=mod_tp_dst:1234,normal");
    }

    @Test
    public void mod_tp_src_tcp6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModTPSrcTcp6");
        imports = List.of(Ipv6Packet.class, AbstractIpPacket.class, TcpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).setSrcPort(1234);\n" +
                "return FilterResult.PASS;"
        );
        check("tcp6,actions=mod_tp_src:1234,normal");
    }

    @Test
    public void mod_tp_dst_tcp6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModTPDstTcp6");
        imports = List.of(Ipv6Packet.class, AbstractIpPacket.class, TcpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).setDstPort(1234);\n" +
                "return FilterResult.PASS;"
        );
        check("tcp6,actions=mod_tp_dst:1234,normal");
    }

    @Test
    public void mod_tp_src_udp() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModTPSrcUdp");
        imports = List.of(Ipv4Packet.class, AbstractIpPacket.class, UdpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).setSrcPort(1234);\n" +
                "return FilterResult.PASS;"
        );
        check("udp,actions=mod_tp_src:1234,normal");
    }

    @Test
    public void mod_tp_dst_udp() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModTPDstUdp");
        imports = List.of(Ipv4Packet.class, AbstractIpPacket.class, UdpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).setDstPort(1234);\n" +
                "return FilterResult.PASS;");
        check("udp,actions=mod_tp_dst:1234,normal");
    }

    @Test
    public void mod_tp_src_udp6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModTPSrcUdp6");
        imports = List.of(Ipv6Packet.class, AbstractIpPacket.class, UdpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).setSrcPort(1234);\n" +
                "return FilterResult.PASS;"
        );
        check("udp6,actions=mod_tp_src:1234,normal");
    }

    @Test
    public void mod_tp_dst_udp6() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.ModTPDstUdp6");
        imports = List.of(AbstractIpPacket.class, Ipv6Packet.class, UdpPacket.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        actions = List.of(
            "" +
                "((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).setDstPort(1234);\n" +
                "return FilterResult.PASS;"
        );
        check("udp6,actions=mod_tp_dst:1234,normal");
    }

    @Test
    public void output() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Output");
        imports = List.of(IfaceHolder.class);
        fields = "private final IfaceHolder[] ifaces = new IfaceHolder[]{\n" +
            "    new IfaceHolder(\"xdp:veth0\", null)\n" +
            "};";
        constructor = "" +
            "registerIfaceHolder(this.ifaces[0]);";
        tables = genTable(0, EXECUTE0);
        actions = List.of("return helper.redirect(pkb, ifaces[0].iface);");
        check("action=output:xdp:veth0");

        imports = List.of(IfaceHolder.class, Ipv4Packet.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("ip,action=output:xdp:veth0");
    }

    @Test
    public void multiOutput() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.MultiOutput");
        imports = List.of(IfaceHolder.class);
        fields = "private final IfaceHolder[] ifaces = new IfaceHolder[]{\n" +
            "    new IfaceHolder(\"xdp:veth0\", null),\n" +
            "    new IfaceHolder(\"xdp:veth1\", null)\n" +
            "};";
        constructor = "" +
            "registerIfaceHolder(this.ifaces[0]);\n" +
            "registerIfaceHolder(this.ifaces[1]);";
        tables = genTable(0, EXECUTE0);
        actions = List.of("" +
            "helper.sendPacket(pkb, ifaces[0].iface);\n" +
            "helper.sendPacket(pkb, ifaces[1].iface);\n" +
            "return FilterResult.DROP;");
        check("actions=output:xdp:veth0,output:xdp:veth1");

        imports = List.of(IfaceHolder.class, Ipv4Packet.class);
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet) {\n" +
            "    " + EXECUTE0 + "\n" +
            "}\n" +
            "return FilterResult.DROP;");
        check("ip,actions=output:xdp:veth0,output:xdp:veth1");
    }

    @Test
    public void ratelimit() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.RateLimit");
        imports = List.of(RateLimiter.class, SimpleRateLimiter.class);
        fields = "private final RateLimiter[] ratelimiters = new RateLimiter[]{\n" +
            "    new SimpleRateLimiter(1048576, 1049),\n" +
            "    new SimpleRateLimiter(1000000, 1000)\n" +
            "};";
        actions = List.of("" +
            "if (!helper.ratelimitByBitsPerSecond(pkb, ratelimiters[0])) {\n" +
            "    return FilterResult.DROP;\n" +
            "}\n" +
            "if (!helper.ratelimitByPacketsPerSecond(pkb, ratelimiters[1])) {\n" +
            "    return FilterResult.DROP;\n" +
            "}\n" +
            "return FilterResult.PASS;");

        tables = genTable(0, EXECUTE0);
        check("actions=limit_bps:1048576,limit_pps:1000000,normal");
    }

    @Test
    public void run() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Run");
        actions = List.of("" +
            "run_myMethod(helper, pkb);\n" +
            "return FilterResult.DROP;");
        tables = genTable(0, EXECUTE0);
        extra = "" +
            "protected void run_myMethod(PacketFilterHelper helper, PacketBuffer pkb) {\n" +
            "}";
        check("actions=run:myMethod,drop");
    }

    @Test
    public void invoke() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.Invoke");
        actions = List.of("return invoke_myMethod(helper, pkb);");
        tables = genTable(0, EXECUTE0);
        extra = "" +
            "protected FilterResult invoke_myMethod(PacketFilterHelper helper, PacketBuffer pkb) {\n" +
            "    return FilterResult.DROP;\n" +
            "}";
        check("actions=invoke:myMethod");
    }

    @Test
    public void realworld() throws Exception {
        fullname("io.vproxy.test.gen.packetfilters.RealWorld");
        imports = List.of(
            AbstractIpPacket.class,
            UdpPacket.class,
            ArpPacket.class,
            Ipv4Packet.class,
            Ipv6Packet.class,
            MacAddress.class,
            BitwiseMatcher.class,
            ByteArray.class,
            BitwiseIntMatcher.class,
            IfaceHolder.class
        );
        fields = "" +
            "private static final MacAddress MAC_HOLDER_d6_62_92_ee_ce_bf = new MacAddress(\"d6:62:92:ee:ce:bf\");\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = BitwiseMatcher.from(ByteArray.fromHexString(\"c0a80108\"), ByteArray.fromHexString(\"ffffffff\"));\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_1 = BitwiseMatcher.from(ByteArray.fromHexString(\"ffffffffffff\"), ByteArray.fromHexString(\"ffffffffffff\"));\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_2 = BitwiseMatcher.from(ByteArray.fromHexString(\"01005e000000\"), ByteArray.fromHexString(\"ffffff800000\"));\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_3 = BitwiseMatcher.from(ByteArray.fromHexString(\"58b623ed6fbd\"), ByteArray.fromHexString(\"ffffffffffff\"));\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_4 = BitwiseMatcher.from(ByteArray.fromHexString(\"c0a80100\"), ByteArray.fromHexString(\"ffffff00\"));\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_5 = BitwiseMatcher.from(ByteArray.fromHexString(\"1875322e59e8\"), ByteArray.fromHexString(\"ffffffffffff\"));\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_6 = BitwiseMatcher.from(ByteArray.fromHexString(\"c0a80101\"), ByteArray.fromHexString(\"ffffffff\"));\n" +
            "private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_7 = BitwiseMatcher.from(ByteArray.fromHexString(\"64600000\"), ByteArray.fromHexString(\"ffe00000\"));\n" +
            "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_0 = BitwiseIntMatcher.from(68, -1);\n" +
            "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_1 = BitwiseIntMatcher.from(67, -1);\n" +
            "private static final BitwiseIntMatcher BITWISE_INT_MATCHER_HOLDER_2 = BitwiseIntMatcher.from(53, -1);\n" +
            "private final IfaceHolder[] ifaces = new IfaceHolder[]{\n" +
            "    new IfaceHolder(\"enp1s0\", null),\n" +
            "    new IfaceHolder(\"gw0-in-br\", null),\n" +
            "    new IfaceHolder(\"vp-veth0-in-br\", null),\n" +
            "    new IfaceHolder(\"wifi0-in-br\", null),\n" +
            "    new IfaceHolder(\"wifi1-in-br\", null),\n" +
            "    new IfaceHolder(\"enp5s0\", null)\n" +
            "};";
        constructor = "" +
            "registerIfaceHolder(this.ifaces[0]);\n" +
            "registerIfaceHolder(this.ifaces[1]);\n" +
            "registerIfaceHolder(this.ifaces[2]);\n" +
            "registerIfaceHolder(this.ifaces[3]);\n" +
            "registerIfaceHolder(this.ifaces[4]);\n" +
            "registerIfaceHolder(this.ifaces[5]);";
        tables = genTable(0, "" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet) {\n" +
            "    " + EXECUTE + "0);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 58) {\n" +
            "    " + EXECUTE + "1);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2054) {\n" +
            "    return table1(helper, pkb);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_1.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    return table1(helper, pkb);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_1.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    return table1(helper, pkb);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 1) {\n" +
            "    return table1(helper, pkb);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_2.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort())) {\n" +
            "    return table1(helper, pkb);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_2.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    return table1(helper, pkb);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_0.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst())) {\n" +
            "    return table2(helper, pkb);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_0.match(((AbstractIpPacket) pkb.pkt.getPacket()).getSrc())) {\n" +
            "    return table2(helper, pkb);\n" +
            "}\n" +
            "if (BITWISE_MATCHER_HOLDER_1.match(pkb.pkt.getDst())) {\n" +
            "    " + EXECUTE + "2);\n" +
            "}\n" +
            "if (BITWISE_MATCHER_HOLDER_2.match(pkb.pkt.getDst())) {\n" +
            "    " + EXECUTE + "3);\n" +
            "}\n" +
            "return table1(helper, pkb);")
            + genTable(1, "" +
            "if (BITWISE_MATCHER_HOLDER_3.match(pkb.pkt.getSrc()) && pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_4.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst())) {\n" +
            "    " + EXECUTE + "4);\n" +
            "}\n" +
            "if (BITWISE_MATCHER_HOLDER_3.match(pkb.pkt.getDst()) && pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_4.match(((AbstractIpPacket) pkb.pkt.getPacket()).getSrc())) {\n" +
            "    " + EXECUTE + "5);\n" +
            "}\n" +
            "return table2(helper, pkb);")
            + genTable(2, "" +
            "if (BITWISE_MATCHER_HOLDER_5.match(pkb.pkt.getDst()) && BITWISE_MATCHER_HOLDER_5.match(pkb.pkt.getSrc())) {\n" +
            "    " + EXECUTE + "6);\n" +
            "}\n" +
            "if (BITWISE_MATCHER_HOLDER_1.match(pkb.pkt.getDst()) && BITWISE_MATCHER_HOLDER_5.match(pkb.pkt.getSrc())) {\n" +
            "    " + EXECUTE + "7);\n" +
            "}\n" +
            "if (BITWISE_MATCHER_HOLDER_5.match(pkb.pkt.getDst()) && pkb.pkt.getType() == 2054) {\n" +
            "    " + EXECUTE + "8);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_1.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    " + EXECUTE + "9);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_1.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    " + EXECUTE + "10);\n" +
            "}\n" +
            "if (pkb.devin == ifaces[5].iface && pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_1.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    " + EXECUTE + "11);\n" +
            "}\n" +
            "if (pkb.devin == ifaces[5].iface && pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_1.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    " + EXECUTE + "12);\n" +
            "}\n" +
            "if (pkb.devin == ifaces[2].iface && pkb.pkt.getType() == 2054 && BITWISE_MATCHER_HOLDER_6.match(((ArpPacket) pkb.pkt.getPacket()).getSenderIp())) {\n" +
            "    " + EXECUTE + "13);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2054 && BITWISE_MATCHER_HOLDER_6.match(((ArpPacket) pkb.pkt.getPacket()).getTargetIp())) {\n" +
            "    " + EXECUTE + "14);\n" +
            "}\n" +
            "if (pkb.devin == ifaces[2].iface && pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_6.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst()) && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_2.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    " + EXECUTE + "15);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_7.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst())) {\n" +
            "    " + EXECUTE + "16);\n" +
            "}\n" +
            "if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_6.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst()) && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_2.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {\n" +
            "    " + EXECUTE + "17);\n" +
            "}\n" +
            "if (BITWISE_MATCHER_HOLDER_5.match(pkb.pkt.getDst())) {\n" +
            "    " + EXECUTE + "18);\n" +
            "}\n" +
            EXECUTE + "19);");
        actions = List.of(
            // 0
            "return FilterResult.DROP;",
            // 1
            "return FilterResult.DROP;",
            // 2
            "return FilterResult.DROP;",
            // 3
            "return FilterResult.DROP;",
            // 4
            "return FilterResult.DROP;",
            // 5
            "return FilterResult.DROP;",
            // 6
            "return FilterResult.DROP;",
            // 7
            "" +
                "helper.sendPacket(pkb, ifaces[0].iface);\n" +
                "helper.sendPacket(pkb, ifaces[1].iface);\n" +
                "helper.sendPacket(pkb, ifaces[2].iface);\n" +
                "helper.sendPacket(pkb, ifaces[3].iface);\n" +
                "helper.sendPacket(pkb, ifaces[4].iface);\n" +
                "return FilterResult.DROP;",
            // 8
            "" +
                "helper.sendPacket(pkb, ifaces[5].iface);\n" +
                "helper.sendPacket(pkb, ifaces[1].iface);\n" +
                "return FilterResult.DROP;",
            // 9
            "return helper.redirect(pkb, ifaces[1].iface);",
            // 10
            "" +
                "helper.sendPacket(pkb, ifaces[0].iface);\n" +
                "helper.sendPacket(pkb, ifaces[3].iface);\n" +
                "helper.sendPacket(pkb, ifaces[4].iface);\n" +
                "return FilterResult.DROP;",
            // 11
            "return FilterResult.DROP;",
            // 12
            "return FilterResult.DROP;",
            // 13
            "return FilterResult.DROP;",
            // 14
            "return helper.redirect(pkb, ifaces[5].iface);",
            // 15
            "return helper.redirect(pkb, ifaces[5].iface);",
            // 16
            "" +
                "pkb.pkt.setDst(MAC_HOLDER_d6_62_92_ee_ce_bf);\n" +
                "return helper.redirect(pkb, ifaces[2].iface);",
            // 17
            "" +
                "pkb.pkt.setDst(MAC_HOLDER_d6_62_92_ee_ce_bf);\n" +
                "return helper.redirect(pkb, ifaces[2].iface);",
            // 18
            "return helper.redirect(pkb, ifaces[5].iface);",
            // 19
            "return FilterResult.PASS;"
        );
        check(TestFlowParser.TEST_FLOW);
    }
}
