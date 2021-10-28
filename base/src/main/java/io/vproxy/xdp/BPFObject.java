package io.vproxy.xdp;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.net.Nic;
import io.vproxy.vfd.MacAddress;

import java.io.IOException;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BPFObject {
    public static final String PREBUILT_SKIP_DST_MAC = ":prebuilt:skip-dst-mac";
    public static final String PREBUILT_HANDLE_ALL = ":prebuilt:handle-all";

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
        if (filepath == null || filepath.equals(PREBUILT_SKIP_DST_MAC)) {
            genfilename = generateDefault(nicName);
        } else if (filepath.equals(PREBUILT_HANDLE_ALL)) {
            byte[] bytes = BPFObject.handleAllProgram().toJavaArray();
            genfilename = Utils.writeTemporaryFile("kern", "o", bytes);
        }

        long bpfobj = NativeXDP.get().loadAndAttachBPFProgramToNic(genfilename, programName, nicName, mode.mode, forceAttach);
        return new BPFObject(nicName, filepath, programName, mode, bpfobj);
    }

    private static String generateDefault(String nicname) throws IOException {
        List<Nic> nics;
        try {
            nics = Utils.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new IOException("failed to retrieve nics, err: " + Utils.formatErr(e), e);
        }
        Nic chosenNic = null;
        for (var nic : nics) {
            if (nic.name.equals(nicname)) {
                chosenNic = nic;
                break;
            }
        }
        if (chosenNic == null) {
            throw new IOException("nic " + nicname + " not found");
        }
        var mac = chosenNic.mac;
        Logger.alert("generating ebpf object for nic " + nicname + " with mac " + mac);

        byte[] bytes = BPFObject.skipDstMacProgram(mac).toJavaArray();
        return Utils.writeTemporaryFile("kern", "o", bytes);
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

    public void release(boolean detach) {
        NativeXDP.get().releaseBPFObject(bpfobj);
        if (detach) {
            try {
                NativeXDP.get().detachBPFProgramFromNic(nic);
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "detaching bpf object from nic " + nic + " failed", e);
            }
        }
    }

    public String toString() {
        return nic + " ->"
            + " path " + filename
            + " prog " + prog
            + " mode " + mode.name();
    }

    public static final String DEFAULT_XDP_PROG_NAME = "xdp_sock";
    public static final String DEFAULT_XSKS_MAP_NAME = "xsks_map";
    public static final String DEFAULT_MAC_MAP_NAME = "mac_map";

    /**
     * <pre>
     * #include &lt;linux/bpf.h&gt;
     * #include &lt;bpf_helpers.h&gt;
     *
     * struct bpf_map_def SEC("maps") xsks_map = {
     *     .type        = BPF_MAP_TYPE_XSKMAP,
     *     .max_entries = 128,
     *     .key_size    = sizeof(int),
     *     .value_size  = sizeof(int)
     * };
     *
     * struct bpf_map_def SEC("maps") mac_map = {
     *     .type = BPF_MAP_TYPE_LRU_HASH,
     *     .max_entries = 4096,
     *     .key_size = sizeof(char[6]),
     *     .value_size = sizeof(int)
     * };
     *
     * struct bpf_map_def SEC("maps") in_mac_map = {
     *     .type = BPF_MAP_TYPE_LRU_HASH,
     *     .max_entries = 4096,
     *     .key_size = sizeof(char[6]),
     *     .value_size = sizeof(int)
     * };
     *
     * inline int redirect_pkt_count_check(struct xdp_md *ctx) {
     *     unsigned char* data_end = (unsigned char*) ((long) ctx-&gt;data_end);
     *     unsigned char* data     = (unsigned char*) ((long) ctx-&gt;data);
     *     unsigned char* pos      = data;
     *     pos += 12;
     *     if (pos &gt; data_end) {
     *         return 0;
     *     }
     *     int* cnt_ptr = bpf_map_lookup_elem(&in_mac_map, data + 6);
     *     int cnt;
     *     if (cnt_ptr == NULL) {
     *         cnt = 1;
     *         bpf_map_update_elem(&in_mac_map, data + 6, &cnt, 0);
     *         return 0;
     *     }
     *     cnt = *cnt_ptr;
     *     *cnt_ptr += 1;
     *     if (cnt % 65536 == 0) {
     *         return 0;
     *     }
     *     return 1;
     * }
     *
     * inline int redirect_pkt_by_mac(struct xdp_md *ctx) {
     *     unsigned char* data_end = (unsigned char*) ((long) ctx-&gt;data_end);
     *     unsigned char* data     = (unsigned char*) ((long) ctx-&gt;data);
     *     unsigned char* pos      = data;
     *     pos += 6;
     *     if (pos &gt; data_end) {
     *         return XDP_DROP;
     *     }
     *     int* output_iface_ptr = bpf_map_lookup_elem(&mac_map, data);
     *     if (output_iface_ptr != NULL) {
     *         int output_iface = *output_iface_ptr;
     *         if (ctx-&gt;ingress_ifindex == output_iface) {
     *             return XDP_DROP;
     *         }
     *
     *         if (redirect_pkt_count_check(ctx)) {
     *             return bpf_redirect(output_iface, 0);
     *         }
     *     }
     *     return XDP_DROP;
     * }
     *
     * SEC("xdp_sock") int xdp_sock_prog(struct xdp_md *ctx)
     * {
     *     int redirect_res = redirect_pkt_by_mac(ctx);
     *     if (redirect_res != XDP_DROP) {
     *         return redirect_res;
     *     }
     *
     *     unsigned char* data_end = (unsigned char*) ((long) ctx-&gt;data_end);
     *     unsigned char* data     = (unsigned char*) ((long) ctx-&gt;data);
     *     unsigned char* pos = data;
     *     pos += 6;
     *     if (pos &gt; data_end) {
     *         return XDP_DROP;
     *     }
     *     if (data[0] == (unsigned char) 0x00 && // xg
     *         data[1] == (unsigned char) 0x00 && // xh
     *         data[2] == (unsigned char) 0x00 && // xi
     *         data[3] == (unsigned char) 0x00 && // xj
     *         data[4] == (unsigned char) 0x00 && // xk
     *         data[5] == (unsigned char) 0x00) { // xl
     *         return XDP_PASS;
     *     }
     *     return bpf_redirect_map(&xsks_map, ctx-&gt;rx_queue_index, XDP_DROP);
     * }
     * </pre>
     */
    private static final String SKIP_DST_MAC_EBPF_OBJECT = "" +
        "7f454c46020101000000000000000000" +
        "0100f700010000000000000000000000" +
        "00000000000000007804000000000000" +
        "00000000400000000000400007000100" +
        "bf160000000000006161040000000000" +
        "6162000000000000bf23000000000000" +
        "07030000060000002d132b0000000000" +
        "18010000000000000000000000000000" +
        "85000000010000001500270000000000" +
        "610700000000000061610c0000000000" +
        "1d712400000000006161040000000000" +
        "6168000000000000bf82000000000000" +
        "070200000c0000002d121f0000000000" +
        "07080000060000001801000000000000" +
        "0000000000000000bf82000000000000" +
        "850000000100000055000a0000000000" +
        "b701000001000000631afcff00000000" +
        "bfa300000000000007030000fcffffff" +
        "18010000000000000000000000000000" +
        "bf82000000000000b704000000000000" +
        "850000000200000005000f0000000000" +
        "6101000000000000631afcff00000000" +
        "61010000000000000701000001000000" +
        "631000000000000061a1fcff00000000" +
        "57010000ffff00001501070000000000" +
        "bf71000000000000b702000000000000" +
        "8500000017000000bf01000000000000" +
        "67010000200000007701000020000000" +
        "5501180001000000b700000001000000" +
        "61620400000000006161000000000000" +
        "bf130000000000000703000006000000" +
        "2d231200000000007112000000000000" +
        "55020b00xg0000007112010000000000" +
        "55020900xh0000007112020000000000" +
        "55020700xi0000007112030000000000" +
        "55020500xj0000007112040000000000" +
        "55020300xk000000b700000002000000" +
        "711105000000000015010500xl000000" +
        "61621000000000001801000000000000" +
        "0000000000000000b703000001000000" +
        "85000000330000009500000000000000" +
        "11000000040000000400000080000000" +
        "00000000090000000600000004000000" +
        "00100000000000000900000006000000" +
        "04000000001000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000003b0000000400f1ff" +
        "00000000000000000000000000000000" +
        "6f000000000003002002000000000000" +
        "00000000000000006700000000000300" +
        "48020000000000000000000000000000" +
        "60000000000003001001000000000000" +
        "00000000000000005900000000000300" +
        "88010000000000000000000000000000" +
        "15000000110005002800000000000000" +
        "14000000000000001800000011000500" +
        "14000000000000001400000000000000" +
        "2d000000120003000000000000000000" +
        "50020000000000000c00000011000500" +
        "00000000000000001400000000000000" +
        "30000000000000000100000007000000" +
        "98000000000000000100000006000000" +
        "e0000000000000000100000006000000" +
        "28020000000000000100000009000000" +
        "002e74657874006d6170730078736b73" +
        "5f6d617000696e5f6d61635f6d617000" +
        "2e72656c7864705f736f636b00786470" +
        "5f736f636b5f70726f670073616d706c" +
        "655f6b65726e2e63002e737472746162" +
        "002e73796d746162004c4242305f3800" +
        "4c4242305f36004c4242305f3136004c" +
        "4242305f313500000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000004900000003000000" +
        "00000000000000000000000000000000" +
        "00040000000000007700000000000000" +
        "00000000000000000100000000000000" +
        "00000000000000000100000001000000" +
        "06000000000000000000000000000000" +
        "40000000000000000000000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000002400000001000000" +
        "06000000000000000000000000000000" +
        "40000000000000005002000000000000" +
        "00000000000000000800000000000000" +
        "00000000000000002000000009000000" +
        "00000000000000000000000000000000" +
        "c0030000000000004000000000000000" +
        "06000000030000000800000000000000" +
        "10000000000000000700000001000000" +
        "03000000000000000000000000000000" +
        "90020000000000003c00000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000005100000002000000" +
        "00000000000000000000000000000000" +
        "d002000000000000f000000000000000" +
        "01000000060000000800000000000000" +
        "1800000000000000";

    public static ByteArray skipDstMacProgram(MacAddress mac) {
        String ebpfHex = SKIP_DST_MAC_EBPF_OBJECT
            .replace("xg", toHexStr(mac.bytes.get(0)))
            .replace("xh", toHexStr(mac.bytes.get(1)))
            .replace("xi", toHexStr(mac.bytes.get(2)))
            .replace("xj", toHexStr(mac.bytes.get(3)))
            .replace("xk", toHexStr(mac.bytes.get(4)))
            .replace("xl", toHexStr(mac.bytes.get(5)));
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
     * #define ETHER_TYPE_OFF  (12)
     * #define IP4_OFF         (14)
     * #define IP4_IHL_OFF     (IP4_OFF)
     * #define IP4_PROTO_OFF   (IP4_OFF  + 9)
     * #define TCPUDP4_OFF     (IP4_OFF  + 20)
     * #define TCPUDP4_DST_OFF (TCPUDP4_OFF + 2)
     * #define IP6_OFF         (14)
     * #define IP6_PROTO_OFF   (IP6_OFF  + 6)
     * #define TCPUDP6_OFF     (IP6_OFF  + 40)
     * #define TCPUDP6_DST_OFF (TCPUDP6_OFF + 2)
     *
     * #define ETHER_TYPE_IPv4 (0x0800)
     * #define ETHER_TYPE_IPv6 (0x86dd)
     * #define IP_PROTO_TCP    (6)
     * #define IP_PROTO_UDP    (17)
     *
     * #define HANDLED_PORT (1111)
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
     *         if (proto != IP_PROTO_TCP &amp;&amp; proto != IP_PROTO_UDP) {
     *             return XDP_PASS;
     *         }
     *         pos = data + TCPUDP4_DST_OFF + 2;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int dst = ((data[TCPUDP4_DST_OFF] &amp; 0xff) &lt;&lt; 8) | (data[TCPUDP4_DST_OFF + 1] &amp; 0xff);
     *         if (dst != HANDLED_PORT) {
     *             return XDP_PASS;
     *         }
     *     } else if (ether_type == ETHER_TYPE_IPv6) {
     *         pos = data + IP6_PROTO_OFF + 1;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int proto = data[IP6_PROTO_OFF] &amp; 0xff;
     *         if (proto != IP_PROTO_TCP &amp;&amp; proto != IP_PROTO_UDP) {
     *             return XDP_PASS;
     *         }
     *         pos = data + TCPUDP6_DST_OFF + 2;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int dst = ((data[TCPUDP6_DST_OFF] &amp; 0xff) &lt;&lt; 8) | (data[TCPUDP6_DST_OFF + 1] &amp; 0xff);
     *         if (dst != HANDLED_PORT) {
     *             return XDP_PASS;
     *         }
     *     } else {
     *         return XDP_PASS;
     *     }
     *     return bpf_redirect_map(&xsks_map, ctx-&gt;rx_queue_index, XDP_PASS);
     * }
     * </pre>
     */
    private static final String HANDLE_DST_PORT_EBPF_OBJECT = "" +
        "7f454c46020101000000000000000000" +
        "0100f700010000000000000000000000" +
        "00000000000000006803000000000000" +
        "00000000400000000000400007000100" +
        "b7000000020000006113040000000000" +
        "6112000000000000bf24000000000000" +
        "070400000e0000002d342f0000000000" +
        "71250d000000000071240c0000000000" +
        "67040000080000004f54000000000000" +
        "57040000ffff000015041600dd860000" +
        "5504280000080000bf24000000000000" +
        "070400000f0000002d34250000000000" +
        "bf240000000000000704000018000000" +
        "2d3422000000000071240e0000000000" +
        "570400000f00000055041f0005000000" +
        "71241700000000001504010011000000" +
        "55041c0006000000bf24000000000000" +
        "07040000260000002d34190000000000" +
        "71232500000000007122240000000000" +
        "67020000080000004f32000000000000" +
        "15020f00llhh00000500130000000000" +
        "bf240000000000000704000015000000" +
        "2d341000000000007124140000000000" +
        "150401001100000055040d0006000000" +
        "bf24000000000000070400003a000000" +
        "2d340a00000000007123390000000000" +
        "71223800000000006702000008000000" +
        "4f3200000000000055020500llhh0000" +
        "61121000000000001801000000000000" +
        "0000000000000000b703000002000000" +
        "85000000330000009500000000000000" +
        "11000000040000000400000080000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "300000000400f1ff0000000000000000" +
        "00000000000000006d00000000000300" +
        "10010000000000000000000000000000" +
        "65000000000003004001000000000000" +
        "00000000000000005d00000000000300" +
        "80010000000000000000000000000000" +
        "5500000000000300a801000000000000" +
        "00000000000000004e00000000000300" +
        "c8000000000000000000000000000000" +
        "22000000120003000000000000000000" +
        "b0010000000000000c00000011000500" +
        "00000000000000001400000000000000" +
        "88010000000000000100000008000000" +
        "002e74657874006d6170730078736b73" +
        "5f6d6170002e72656c7864705f736f63" +
        "6b007864705f736f636b5f70726f6700" +
        "73616d706c655f6b65726e2e63002e73" +
        "7472746162002e73796d746162004c42" +
        "42305f38004c4242305f3136004c4242" +
        "305f3135004c4242305f3133004c4242" +
        "305f3130000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000003e00000003000000" +
        "00000000000000000000000000000000" +
        "f0020000000000007500000000000000" +
        "00000000000000000100000000000000" +
        "00000000000000000100000001000000" +
        "06000000000000000000000000000000" +
        "40000000000000000000000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000001900000001000000" +
        "06000000000000000000000000000000" +
        "4000000000000000b001000000000000" +
        "00000000000000000800000000000000" +
        "00000000000000001500000009000000" +
        "00000000000000000000000000000000" +
        "e0020000000000001000000000000000" +
        "06000000030000000800000000000000" +
        "10000000000000000700000001000000" +
        "03000000000000000000000000000000" +
        "f0010000000000001400000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000004600000002000000" +
        "00000000000000000000000000000000" +
        "0802000000000000d800000000000000" +
        "01000000070000000800000000000000" +
        "1800000000000000";

    public static ByteArray handleDstPortProgram(int port) {
        String ebpfHex = HANDLE_DST_PORT_EBPF_OBJECT
            .replace("ll", toHexStr((byte) (port & 0xff)))
            .replace("hh", toHexStr((byte) ((port >> 8) & 0xff)));
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
     * struct bpf_map_def SEC("maps") mac_map = {
     *     .type = BPF_MAP_TYPE_LRU_HASH,
     *     .max_entries = 4096,
     *     .key_size = sizeof(char[6]),
     *     .value_size = sizeof(int)
     * };
     *
     * struct bpf_map_def SEC("maps") in_mac_map = {
     *     .type = BPF_MAP_TYPE_LRU_HASH,
     *     .max_entries = 4096,
     *     .key_size = sizeof(char[6]),
     *     .value_size = sizeof(int)
     * };
     *
     * inline int redirect_pkt_count_check(struct xdp_md *ctx) {
     *     unsigned char* data_end = (unsigned char*) ((long) ctx-&gt;data_end);
     *     unsigned char* data     = (unsigned char*) ((long) ctx-&gt;data);
     *     unsigned char* pos      = data;
     *     pos += 12;
     *     if (pos &gt; data_end) {
     *         return 0;
     *     }
     *     int* cnt_ptr = bpf_map_lookup_elem(&in_mac_map, data + 6);
     *     int cnt;
     *     if (cnt_ptr == NULL) {
     *         cnt = 1;
     *         bpf_map_update_elem(&in_mac_map, data + 6, &cnt, 0);
     *         return 0;
     *     }
     *     cnt = *cnt_ptr;
     *     *cnt_ptr += 1;
     *     if (cnt % 65536 == 0) {
     *         return 0;
     *     }
     *     return 1;
     * }
     *
     * inline int redirect_pkt_by_mac(struct xdp_md *ctx) {
     *     unsigned char* data_end = (unsigned char*) ((long) ctx-&gt;data_end);
     *     unsigned char* data     = (unsigned char*) ((long) ctx-&gt;data);
     *     unsigned char* pos      = data;
     *     pos += 6;
     *     if (pos &gt; data_end) {
     *         return XDP_DROP;
     *     }
     *     int* output_iface_ptr = bpf_map_lookup_elem(&mac_map, data);
     *     if (output_iface_ptr != NULL) {
     *         int output_iface = *output_iface_ptr;
     *         if (ctx-&gt;ingress_ifindex == output_iface) {
     *             return XDP_DROP;
     *         }
     *
     *         if (redirect_pkt_count_check(ctx)) {
     *             return bpf_redirect(output_iface, 0);
     *         }
     *     }
     *     return XDP_DROP;
     * }
     *
     * SEC("xdp_sock") int xdp_sock_prog(struct xdp_md *ctx)
     * {
     *     int redirect_result = redirect_pkt_by_mac(ctx);
     *     if (redirect_result != XDP_DROP) {
     *         return redirect_result;
     *     }
     *     return bpf_redirect_map(&xsks_map, ctx-&gt;rx_queue_index, XDP_DROP);
     * }
     * </pre>
     */
    private static final ByteArray HANDLE_ALL_EBPF_OBJECT = ByteArray.fromHexString("" +
        "7f454c46020101000000000000000000" +
        "0100f700010000000000000000000000" +
        "0000000000000000c003000000000000" +
        "00000000400000000000400007000100" +
        "bf160000000000006161040000000000" +
        "6162000000000000bf23000000000000" +
        "07030000060000002d132b0000000000" +
        "18010000000000000000000000000000" +
        "85000000010000001500270000000000" +
        "610700000000000061610c0000000000" +
        "1d712400000000006161040000000000" +
        "6168000000000000bf82000000000000" +
        "070200000c0000002d121f0000000000" +
        "07080000060000001801000000000000" +
        "0000000000000000bf82000000000000" +
        "850000000100000055000a0000000000" +
        "b701000001000000631afcff00000000" +
        "bfa300000000000007030000fcffffff" +
        "18010000000000000000000000000000" +
        "bf82000000000000b704000000000000" +
        "850000000200000005000f0000000000" +
        "6101000000000000631afcff00000000" +
        "61010000000000000701000001000000" +
        "631000000000000061a1fcff00000000" +
        "57010000ffff00001501070000000000" +
        "bf71000000000000b702000000000000" +
        "8500000017000000bf01000000000000" +
        "67010000200000007701000020000000" +
        "55010500010000006162100000000000" +
        "18010000000000000000000000000000" +
        "b7030000010000008500000033000000" +
        "95000000000000001100000004000000" +
        "04000000800000000000000009000000" +
        "06000000040000000010000000000000" +
        "09000000060000000400000000100000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "3b0000000400f1ff0000000000000000" +
        "00000000000000006700000000000300" +
        "10010000000000000000000000000000" +
        "60000000000003008801000000000000" +
        "00000000000000005900000000000300" +
        "b0010000000000000000000000000000" +
        "15000000110005002800000000000000" +
        "14000000000000001800000011000500" +
        "14000000000000001400000000000000" +
        "2d000000120003000000000000000000" +
        "b8010000000000000c00000011000500" +
        "00000000000000001400000000000000" +
        "30000000000000000100000006000000" +
        "98000000000000000100000005000000" +
        "e0000000000000000100000005000000" +
        "90010000000000000100000008000000" +
        "002e74657874006d6170730078736b73" +
        "5f6d617000696e5f6d61635f6d617000" +
        "2e72656c7864705f736f636b00786470" +
        "5f736f636b5f70726f670073616d706c" +
        "655f6b65726e2e63002e737472746162" +
        "002e73796d746162004c4242305f3900" +
        "4c4242305f38004c4242305f36000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "49000000030000000000000000000000" +
        "00000000000000005003000000000000" +
        "6e000000000000000000000000000000" +
        "01000000000000000000000000000000" +
        "01000000010000000600000000000000" +
        "00000000000000004000000000000000" +
        "00000000000000000000000000000000" +
        "04000000000000000000000000000000" +
        "24000000010000000600000000000000" +
        "00000000000000004000000000000000" +
        "b8010000000000000000000000000000" +
        "08000000000000000000000000000000" +
        "20000000090000000000000000000000" +
        "00000000000000001003000000000000" +
        "40000000000000000600000003000000" +
        "08000000000000001000000000000000" +
        "07000000010000000300000000000000" +
        "0000000000000000f801000000000000" +
        "3c000000000000000000000000000000" +
        "04000000000000000000000000000000" +
        "51000000020000000000000000000000" +
        "00000000000000003802000000000000" +
        "d8000000000000000100000005000000" +
        "08000000000000001800000000000000").persist();

    public static ByteArray handleAllProgram() {
        return HANDLE_ALL_EBPF_OBJECT;
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
