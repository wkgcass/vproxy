package vproxy.xdp;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.vfd.MacAddress;

import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BPFObject {
    public final String nic;
    public final String filename;
    public final String prog;
    public final BPFMode mode;

    public final long bpfobj;
    private final Map<String, BPFMap> maps = new ConcurrentHashMap<>();

    private BPFObject(String nic, String filename, String prog, BPFMode mode, long bpfobj) {
        this.nic = nic;
        this.filename = filename;
        this.prog = prog;
        this.mode = mode;

        this.bpfobj = bpfobj;
    }

    public static BPFObject loadAndAttachToNic(String filepath, String programName, String nicName,
                                               BPFMode mode, boolean forceAttach) throws IOException {
        String genfilename = filepath;
        if (filepath == null) {
            genfilename = generateDefault(nicName);
        }

        long bpfobj = NativeXDP.get().loadAndAttachBPFProgramToNic(genfilename, programName, nicName, mode.mode, forceAttach);
        return new BPFObject(nicName, filepath, programName, mode, bpfobj);
    }

    private static String generateDefault(String nicname) throws IOException {
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new IOException("failed to retrieve nics, err: " + Utils.formatErr(e), e);
        }
        NetworkInterface chosenNic = null;
        while (nics.hasMoreElements()) {
            var nic = nics.nextElement();
            if (nic.getName().equals(nicname)) {
                chosenNic = nic;
                break;
            }
        }
        if (chosenNic == null) {
            throw new IOException("nic " + nicname + " not found");
        }
        byte[] macBytes = chosenNic.getHardwareAddress();
        if (macBytes == null || macBytes.length != 6) {
            throw new IOException("nic " + nicname + " doesn't have mac or mac is invalid");
        }
        var mac = new MacAddress(macBytes);
        Logger.alert("generating ebpf object for nic " + nicname + " with mac " + mac);

        byte[] bytes = BPFObject.skipDstMacProgram(mac).toJavaArray();
        File f = File.createTempFile("kern", ".o");
        f.deleteOnExit();
        Files.write(f.toPath(), bytes);
        return f.getAbsolutePath();
    }

    public BPFMap getMap(String name) throws IOException {
        if (maps.containsKey(name)) {
            return maps.get(name);
        }
        synchronized (maps) {
            if (maps.containsKey(name)) {
                return maps.get(name);
            }
            long map = NativeXDP.get().findMapByNameInBPF(bpfobj, name);
            var m = new BPFMap(name, map, this);
            maps.put(name, m);
            return m;
        }
    }

    public void release() {
        NativeXDP.get().releaseBPFObject(bpfobj);
    }

    public String toString() {
        return nic + " ->"
            + " path " + filename
            + " prog " + prog
            + " mode " + mode.name();
    }

    /**
     * <pre>
     * #include &lt;linux/bpf.h&gt;
     * #include &lt;bpf_helpers.h&gt;
     *
     * struct bpf_map_def SEC("maps") xsks_map = {
     *     .type        = BPF_MAP_TYPE_XSKMAP,
     *     .max_entries = 1, // xx
     *     .key_size    = sizeof(int),
     *     .value_size  = sizeof(int)
     * };
     *
     * SEC("xdp_sock") int xdp_sock_prog(struct xdp_md *ctx)
     * {
     *         unsigned char* data_end = (unsigned char*) ((long) ctx->data_end);
     *         unsigned char* data     = (unsigned char*) ((long) ctx->data);
     * 	       unsigned char* pos = data;
     *         pos += 6;
     *         if (pos &gt; data_end) {
     *             return XDP_DROP;
     *         }
     *         if (data[0] == (unsigned char) 0x00 && // xg
     *             data[1] == (unsigned char) 0x00 && // xh
     *             data[2] == (unsigned char) 0x00 && // xi
     *             data[3] == (unsigned char) 0x00 && // xj
     *             data[4] == (unsigned char) 0x00 && // xk
     *             data[5] == (unsigned char) 0x00) { // xl
     *         return XDP_PASS;
     *     }
     *     return bpf_redirect_map(&xsks_map, ctx->rx_queue_index, XDP_DROP);
     * }
     * </pre>
     */
    private static final String SKIP_DST_MAC_EBPF_OBJECT = "" +
        "7f454c46020101000000000000000000" +
        "0100f700010000000000000000000000" +
        "00000000000000002002000000000000" +
        "00000000400000000000400007000100" +
        "b7000000010000006113040000000000" +
        "6112000000000000bf24000000000000" +
        "07040000060000002d34120000000000" +
        "712300000000000055030b00xg000000" +
        "712301000000000055030900xh000000" +
        "712302000000000055030700xi000000" +
        "712303000000000055030500xj000000" +
        "712304000000000055030300xk000000" +
        "b7000000020000007122050000000000" +
        "15020500xl0000006112100000000000" +
        "18010000000000000000000000000000" +
        "b7030000010000008500000033000000" +
        "95000000000000001100000004000000" +
        "04000000xx0000000000000000000000" +
        "00000000000000000000000000000000" +
        "0000000000000000300000000400f1ff" +
        "00000000000000000000000000000000" +
        "55000000000003009800000000000000" +
        "00000000000000004e00000000000300" +
        "c0000000000000000000000000000000" +
        "22000000120003000000000000000000" +
        "c8000000000000000c00000011000500" +
        "00000000000000001400000000000000" +
        "a0000000000000000100000005000000" +
        "002e74657874006d6170730078736b73" +
        "5f6d6170002e72656c7864705f736f63" +
        "6b007864705f736f636b5f70726f6700" +
        "73616d706c655f6b65726e2e63002e73" +
        "7472746162002e73796d746162004c42" +
        "42305f38004c4242305f370000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "3e000000030000000000000000000000" +
        "0000000000000000c001000000000000" +
        "5c000000000000000000000000000000" +
        "01000000000000000000000000000000" +
        "01000000010000000600000000000000" +
        "00000000000000004000000000000000" +
        "00000000000000000000000000000000" +
        "04000000000000000000000000000000" +
        "19000000010000000600000000000000" +
        "00000000000000004000000000000000" +
        "c8000000000000000000000000000000" +
        "08000000000000000000000000000000" +
        "15000000090000000000000000000000" +
        "0000000000000000b001000000000000" +
        "10000000000000000600000003000000" +
        "08000000000000001000000000000000" +
        "07000000010000000300000000000000" +
        "00000000000000000801000000000000" +
        "14000000000000000000000000000000" +
        "04000000000000000000000000000000" +
        "46000000020000000000000000000000" +
        "00000000000000002001000000000000" +
        "90000000000000000100000004000000" +
        "08000000000000001800000000000000";

    public static ByteArray skipDstMacProgram(MacAddress mac) {
        String ebpfHex = SKIP_DST_MAC_EBPF_OBJECT
            .replace("xg", toHexStr(mac.bytes.get(0)))
            .replace("xh", toHexStr(mac.bytes.get(1)))
            .replace("xi", toHexStr(mac.bytes.get(2)))
            .replace("xj", toHexStr(mac.bytes.get(3)))
            .replace("xk", toHexStr(mac.bytes.get(4)))
            .replace("xl", toHexStr(mac.bytes.get(5)))
            .replace("xx", toHexStr((byte) 128));
        return ByteArray.fromHexString(ebpfHex);
    }

    /**
     * <pre>
     * #include &lt;linux/bpf.h&gt;
     * #include &lt;bpf_helpers.h&gt;
     *
     * struct bpf_map_def SEC("maps") xsks_map = {
     *     .type = BPF_MAP_TYPE_XSKMAP,
     *     .max_entries = 128,
     *     .key_size = sizeof(int),
     *     .value_size = sizeof(int)
     * };
     *
     * #define ETHER_TYPE_OFF (12)
     * #define IP4_OFF        (14)
     * #define IP4_IHL_OFF    (IP4_OFF)
     * #define IP4_PROTO_OFF  (IP4_OFF  + 9)
     * #define TCP4_OFF       (IP4_OFF  + 20)
     * #define TCP4_DST_OFF   (TCP4_OFF + 2)
     * #define IP6_OFF        (14)
     * #define IP6_PROTO_OFF  (IP6_OFF  + 6)
     * #define TCP6_OFF       (IP6_OFF  + 40)
     * #define TCP6_DST_OFF   (TCP6_OFF + 2)
     *
     * #define ETHER_TYPE_IPv4 (0x0800)
     * #define ETHER_TYPE_IPv6 (0x86dd)
     * #define IP_PROTO_TCP    (6)
     *
     * #define HANDLED_TCP_PORT (1111)
     *
     * SEC("xdp_sock") int xdp_sock_prog(struct xdp_md *ctx)
     * {
     *     unsigned char* data_end = (unsigned char*) ((long) ctx-&gt;data_end);
     *     unsigned char* data     = (unsigned char*) ((long) ctx-&gt;data);
     *     unsigned char* pos = data;
     *     pos += ETHER_TYPE_OFF + 2;
     *     if (pos &gt; data_end) {
     *         return XDP_PASS;
     *     }
     *     int ether_type = ((data[ETHER_TYPE_OFF] &amp; 0xff) &lt;&lt; 8) | (data[ETHER_TYPE_OFF + 1] &amp; 0xff);
     *     if (ether_type == ETHER_TYPE_IPv4) {
     *         pos = data + IP4_IHL_OFF + 1;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int ip_len = data[IP4_IHL_OFF] &amp; 0xf;
     *         if (ip_len != 5) {
     *             return XDP_PASS;
     *         }
     *         pos = data + IP4_PROTO_OFF + 1;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int proto = data[IP4_PROTO_OFF] &amp; 0xff;
     *         if (proto != IP_PROTO_TCP) {
     *             return XDP_PASS;
     *         }
     *         pos = data + TCP4_DST_OFF + 2;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int dst = ((data[TCP4_DST_OFF] &amp; 0xff) &lt;&lt; 8) | (data[TCP4_DST_OFF + 1] &amp; 0xff);
     *         if (dst != HANDLED_TCP_PORT) {
     *             return XDP_PASS;
     *         }
     *     } else if (ether_type == ETHER_TYPE_IPv6) {
     *         pos = data + IP6_PROTO_OFF + 1;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int proto = data[IP6_PROTO_OFF] &amp; 0xff;
     *         if (proto != IP_PROTO_TCP) {
     *             return XDP_PASS;
     *         }
     *         pos = data + TCP6_DST_OFF + 2;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int dst = ((data[TCP6_DST_OFF] &amp; 0xff) &lt;&lt; 8) | (data[TCP6_DST_OFF + 1] &amp; 0xff);
     *         if (dst != HANDLED_TCP_PORT) {
     *             return XDP_PASS;
     *         }
     *     } else {
     *         return XDP_PASS;
     *     }
     *     return bpf_redirect_map(&xsks_map, ctx-&gt;rx_queue_index, XDP_PASS);
     * }
     * </pre>
     */
    private static final String SKIP_TCP_DST_PORT_EBPF_OBJECT = "" +
        "7f454c46020101000000000000000000" +
        "0100f700010000000000000000000000" +
        "00000000000000001803000000000000" +
        "00000000400000000000400007000100" +
        "b7000000020000006113040000000000" +
        "6112000000000000bf24000000000000" +
        "070400000e0000002d342d0000000000" +
        "71250d000000000071240c0000000000" +
        "67040000080000004f54000000000000" +
        "57040000ffff000015041500dd860000" +
        "5504260000080000bf24000000000000" +
        "070400000f0000002d34230000000000" +
        "bf240000000000000704000018000000" +
        "2d3420000000000071240e0000000000" +
        "570400000f00000055041d0005000000" +
        "bf240000000000000704000026000000" +
        "2d341a00000000007123170000000000" +
        "55031800060000007123250000000000" +
        "71222400000000006702000008000000" +
        "4f3200000000000015020e00llhh0000" +
        "0500120000000000bf24000000000000" +
        "07040000150000002d340f0000000000" +
        "bf24000000000000070400003a000000" +
        "2d340c00000000007123140000000000" +
        "55030a00060000007123390000000000" +
        "71223800000000006702000008000000" +
        "4f3200000000000055020500llhh0000" +
        "61121000000000001801000000000000" +
        "0000000000000000b703000002000000" +
        "85000000330000009500000000000000" +
        "11000000040000000400000080000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "300000000400f1ff0000000000000000" +
        "00000000000000005d00000000000300" +
        "70010000000000000000000000000000" +
        "55000000000003009801000000000000" +
        "00000000000000004e00000000000300" +
        "08010000000000000000000000000000" +
        "22000000120003000000000000000000" +
        "a0010000000000000c00000011000500" +
        "00000000000000001400000000000000" +
        "78010000000000000100000006000000" +
        "002e74657874006d6170730078736b73" +
        "5f6d6170002e72656c7864705f736f63" +
        "6b007864705f736f636b5f70726f6700" +
        "73616d706c655f6b65726e2e63002e73" +
        "7472746162002e73796d746162004c42" +
        "42305f39004c4242305f3134004c4242" +
        "305f3133000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000003e00000003000000" +
        "00000000000000000000000000000000" +
        "b0020000000000006500000000000000" +
        "00000000000000000100000000000000" +
        "00000000000000000100000001000000" +
        "06000000000000000000000000000000" +
        "40000000000000000000000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000001900000001000000" +
        "06000000000000000000000000000000" +
        "4000000000000000a001000000000000" +
        "00000000000000000800000000000000" +
        "00000000000000001500000009000000" +
        "00000000000000000000000000000000" +
        "a0020000000000001000000000000000" +
        "06000000030000000800000000000000" +
        "10000000000000000700000001000000" +
        "03000000000000000000000000000000" +
        "e0010000000000001400000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000004600000002000000" +
        "00000000000000000000000000000000" +
        "f801000000000000a800000000000000" +
        "01000000050000000800000000000000" +
        "1800000000000000";

    public static ByteArray skipTcpDstPortProgram(int port) {
        String ebpfHex = SKIP_TCP_DST_PORT_EBPF_OBJECT
            .replace("ll", toHexStr((byte) (port & 0xff)))
            .replace("hh", toHexStr((byte) ((port >> 8) & 0xff)));
        return ByteArray.fromHexString(ebpfHex);
    }

    /**
     * <pre>
     * #include <linux/bpf.h>
     * #include <bpf_helpers.h>
     *
     * struct bpf_map_def SEC("maps") xsks_map = {
     *     .type = BPF_MAP_TYPE_XSKMAP,
     *     .max_entries = 128,
     *     .key_size = sizeof(int),
     *     .value_size = sizeof(int)
     * };
     *
     * #define ETHER_TYPE_OFF (12)
     * #define IP4_OFF        (14)
     * #define IP4_IHL_OFF    (IP4_OFF)
     * #define IP4_PROTO_OFF  (IP4_OFF  + 9)
     * #define TCP4_OFF       (IP4_OFF  + 20)
     * #define TCP4_SRC_OFF   (TCP4_OFF)
     * #define IP6_OFF        (14)
     * #define IP6_PROTO_OFF  (IP6_OFF  + 6)
     * #define TCP6_OFF       (IP6_OFF  + 40)
     * #define TCP6_SRC_OFF   (TCP6_OFF)
     *
     * #define ETHER_TYPE_IPv4 (0x0800)
     * #define ETHER_TYPE_IPv6 (0x86dd)
     * #define IP_PROTO_TCP    (6)
     *
     * #define HANDLED_TCP_PORT (1111)
     *
     * SEC("xdp_sock") int xdp_sock_prog(struct xdp_md *ctx)
     * {
     *     unsigned char* data_end = (unsigned char*) ((long) ctx->data_end);
     *     unsigned char* data     = (unsigned char*) ((long) ctx->data);
     *     unsigned char* pos = data;
     *     pos += ETHER_TYPE_OFF + 2;
     *     if (pos > data_end) {
     *         return XDP_PASS;
     *     }
     *     int ether_type = ((data[ETHER_TYPE_OFF] & 0xff) << 8) | (data[ETHER_TYPE_OFF + 1] & 0xff);
     *     if (ether_type == ETHER_TYPE_IPv4) {
     *         pos = data + IP4_IHL_OFF + 1;
     *         if (pos > data_end) {
     *             return XDP_PASS;
     *         }
     *         int ip_len = data[IP4_IHL_OFF] & 0xf;
     *         if (ip_len != 5) {
     *             return XDP_PASS;
     *         }
     *         pos = data + IP4_PROTO_OFF + 1;
     *         if (pos > data_end) {
     *             return XDP_PASS;
     *         }
     *         int proto = data[IP4_PROTO_OFF] & 0xff;
     *         if (proto != IP_PROTO_TCP) {
     *             return XDP_PASS;
     *         }
     *         pos = data + TCP4_SRC_OFF + 2;
     *         if (pos > data_end) {
     *             return XDP_PASS;
     *         }
     *         int dst = ((data[TCP4_SRC_OFF] & 0xff) << 8) | (data[TCP4_SRC_OFF + 1] & 0xff);
     *         if (dst != HANDLED_TCP_PORT) {
     *             return XDP_PASS;
     *         }
     *     } else if (ether_type == ETHER_TYPE_IPv6) {
     *         pos = data + IP6_PROTO_OFF + 1;
     *         if (pos > data_end) {
     *             return XDP_PASS;
     *         }
     *         int proto = data[IP6_PROTO_OFF] & 0xff;
     *         if (proto != IP_PROTO_TCP) {
     *             return XDP_PASS;
     *         }
     *         pos = data + TCP6_SRC_OFF + 2;
     *         if (pos > data_end) {
     *             return XDP_PASS;
     *         }
     *         int dst = ((data[TCP6_SRC_OFF] & 0xff) << 8) | (data[TCP6_SRC_OFF + 1] & 0xff);
     *         if (dst != HANDLED_TCP_PORT) {
     *             return XDP_PASS;
     *         }
     *     } else {
     *         return XDP_PASS;
     *     }
     *     return bpf_redirect_map(&xsks_map, ctx->rx_queue_index, XDP_PASS);
     * }
     * </pre>
     */
    private static final String SKIP_TCP_SRC_PORT_EBPF_OBJECT = "" +
        "7f454c46020101000000000000000000" +
        "0100f700010000000000000000000000" +
        "00000000000000001803000000000000" +
        "00000000400000000000400007000100" +
        "b7000000020000006113040000000000" +
        "6112000000000000bf24000000000000" +
        "070400000e0000002d342d0000000000" +
        "71250d000000000071240c0000000000" +
        "67040000080000004f54000000000000" +
        "57040000ffff000015041500dd860000" +
        "5504260000080000bf24000000000000" +
        "070400000f0000002d34230000000000" +
        "bf240000000000000704000018000000" +
        "2d3420000000000071240e0000000000" +
        "570400000f00000055041d0005000000" +
        "bf240000000000000704000024000000" +
        "2d341a00000000007123170000000000" +
        "55031800060000007123230000000000" +
        "71222200000000006702000008000000" +
        "4f3200000000000015020e00llhh0000" +
        "0500120000000000bf24000000000000" +
        "07040000150000002d340f0000000000" +
        "bf240000000000000704000038000000" +
        "2d340c00000000007123140000000000" +
        "55030a00060000007123370000000000" +
        "71223600000000006702000008000000" +
        "4f3200000000000055020500llhh0000" +
        "61121000000000001801000000000000" +
        "0000000000000000b703000002000000" +
        "85000000330000009500000000000000" +
        "11000000040000000400000080000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "300000000400f1ff0000000000000000" +
        "00000000000000005d00000000000300" +
        "70010000000000000000000000000000" +
        "55000000000003009801000000000000" +
        "00000000000000004e00000000000300" +
        "08010000000000000000000000000000" +
        "22000000120003000000000000000000" +
        "a0010000000000000c00000011000500" +
        "00000000000000001400000000000000" +
        "78010000000000000100000006000000" +
        "002e74657874006d6170730078736b73" +
        "5f6d6170002e72656c7864705f736f63" +
        "6b007864705f736f636b5f70726f6700" +
        "73616d706c655f6b65726e2e63002e73" +
        "7472746162002e73796d746162004c42" +
        "42305f39004c4242305f3134004c4242" +
        "305f3133000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000003e00000003000000" +
        "00000000000000000000000000000000" +
        "b0020000000000006500000000000000" +
        "00000000000000000100000000000000" +
        "00000000000000000100000001000000" +
        "06000000000000000000000000000000" +
        "40000000000000000000000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000001900000001000000" +
        "06000000000000000000000000000000" +
        "4000000000000000a001000000000000" +
        "00000000000000000800000000000000" +
        "00000000000000001500000009000000" +
        "00000000000000000000000000000000" +
        "a0020000000000001000000000000000" +
        "06000000030000000800000000000000" +
        "10000000000000000700000001000000" +
        "03000000000000000000000000000000" +
        "e0010000000000001400000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000004600000002000000" +
        "00000000000000000000000000000000" +
        "f801000000000000a800000000000000" +
        "01000000050000000800000000000000" +
        "1800000000000000";

    public static ByteArray skipTcpSrcPortProgram(int port) {
        String ebpfHex = SKIP_TCP_SRC_PORT_EBPF_OBJECT
            .replace("ll", toHexStr((byte) (port & 0xff)))
            .replace("hh", toHexStr((byte) ((port >> 8) & 0xff)));
        return ByteArray.fromHexString(ebpfHex);
    }

    private static String toHexStr(byte b) {
        String s = Integer.toHexString(b & 0xff);
        if (s.length() == 1) {
            return "0" + s;
        } else {
            return s;
        }
    }
}
