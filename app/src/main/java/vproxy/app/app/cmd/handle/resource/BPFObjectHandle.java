package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Flag;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.ResourceType;
import vproxy.app.app.cmd.handle.param.BPFModeHandle;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.base.util.exception.XException;
import vproxy.vfd.MacAddress;
import vproxy.vswitch.iface.XDPIface;
import vproxy.xdp.BPFMode;
import vproxy.xdp.BPFObject;

import java.io.File;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class BPFObjectHandle {
    private BPFObjectHandle() {
    }

    public static BPFObject add(Command cmd) throws Exception {
        String nic = cmd.resource.alias;
        String path;
        boolean isAutogenEbpf;
        if (cmd.args.containsKey(Param.path)) {
            path = Utils.filename(cmd.args.get(Param.path));
            isAutogenEbpf = false;
        } else {
            path = genDefaultEbpf(nic);
            isAutogenEbpf = true;
        }
        String prog;
        prog = cmd.args.getOrDefault(Param.prog, "xdp_sock");
        BPFMode mode = BPFModeHandle.get(cmd, BPFMode.SKB);
        boolean forceAttach = cmd.flags.contains(Flag.force);

        return Application.get().bpfObjectHolder.add(path, prog, isAutogenEbpf, nic, mode, forceAttach);
    }

    private static String genDefaultEbpf(String nicname) throws Exception {
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new XException("failed to retrieve nics, err: " + Utils.formatErr(e), e);
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
            throw new XException("nic " + nicname + " not found");
        }
        byte[] macBytes = chosenNic.getHardwareAddress();
        if (macBytes == null || macBytes.length != 6) {
            throw new XException("nic " + nicname + " doesn't have mac or mac is invalid");
        }
        Logger.alert("generating ebpf object for nic " + nicname + " with mac " + new MacAddress(macBytes));
        return genDefaultEbpf(macBytes);
    }

    private static String genDefaultEbpf(byte[] mac) throws Exception {
        String ebpfHex = DEFAULT_EBPF_OBJECT
            .replace("xg", toHexStr(mac[0]))
            .replace("xh", toHexStr(mac[1]))
            .replace("xi", toHexStr(mac[2]))
            .replace("xj", toHexStr(mac[3]))
            .replace("xk", toHexStr(mac[4]))
            .replace("xl", toHexStr(mac[5]))
            .replace("xx", toHexStr((byte) 128));
        byte[] bytes = Utils.hexToBytes(ebpfHex);
        File f = File.createTempFile("kern", ".o");
        f.deleteOnExit();
        Files.write(f.toPath(), bytes);
        return f.getAbsolutePath();
    }

    private static String toHexStr(byte b) {
        String s = Integer.toHexString(b & 0xff);
        if (s.length() == 1) {
            return "0" + s;
        } else {
            return s;
        }
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
    private static final String DEFAULT_EBPF_OBJECT = "" +
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

    public static List<String> names() throws Exception {
        return Application.get().bpfObjectHolder.names();
    }

    public static List<BPFObject> list() throws Exception {
        var bpfObjectHolder = Application.get().bpfObjectHolder;
        List<BPFObject> ls = new ArrayList<>();
        for (var name : bpfObjectHolder.names()) {
            ls.add(bpfObjectHolder.get(name));
        }
        return ls;
    }

    public static void preRemoveCheck(Command cmd) throws Exception {
        var bpfObject = Application.get().bpfObjectHolder.get(cmd.resource.alias);
        var swNames = Application.get().switchHolder.names();
        for (var swName : swNames) {
            var sw = Application.get().switchHolder.get(swName);
            var ifaces = sw.getIfaces();
            for (var iface : ifaces) {
                if (!(iface instanceof XDPIface)) {
                    continue;
                }
                var xdp = (XDPIface) iface;
                if (xdp.bpfMap.bpfObject == bpfObject) {
                    throw new XException(ResourceType.bpfobj.fullname + " " + bpfObject.nic
                        + " is used by " + ResourceType.xdp.fullname + " " + xdp.nic
                        + " in " + ResourceType.sw.fullname + " " + sw.alias);
                }
            }
        }
    }

    public static void remove(Command cmd) throws Exception {
        Application.get().bpfObjectHolder.removeAndRelease(cmd.resource.alias);
    }
}
