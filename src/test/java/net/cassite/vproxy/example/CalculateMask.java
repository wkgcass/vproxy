package net.cassite.vproxy.example;

import net.cassite.vproxy.util.Tuple;
import net.cassite.vproxy.util.Utils;

import java.util.Arrays;

public class CalculateMask {
    public static void main(String[] args) {
        for (int i = 0; i <= 128; ++i) {
            byte[] bytes = Utils.parseMask(i);
            String num;
            if (i < 10) {
                num = "00" + i;
            } else if (i < 100) {
                num = "0" + i;
            } else {
                num = "" + i;
            }
            printBytes("mask " + num + ": ", bytes);
        }
        System.out.println("------------net/mask------------");
        for (Tuple<String, Integer> tuple : Arrays.asList(
            new Tuple<>("10.144.0.0", 11),
            new Tuple<>("10.144.0.0", 12),
            new Tuple<>("10.144.0.0", 13),
            new Tuple<>("[0000:0010:0000:0000:0000:0000:0000:0000]", 27),
            new Tuple<>("[0000:0010:0000:0000:0000:0000:0000:0000]", 28),
            new Tuple<>("[0000:0010:0000:0000:0000:0000:0000:0000]", 29),
            new Tuple<>("[0000:0010:0000:0000:1000:0000:0000:0000]", 67),
            new Tuple<>("[0000:0010:0000:0000:1000:0000:0000:0000]", 68),
            new Tuple<>("[0000:0010:0000:0000:1000:0000:0000:0000]", 69)
        )) {
            String netStr = tuple.left;
            int maskInt = tuple.right;
            byte[] net = Utils.parseAddress(netStr);
            byte[] mask = Utils.parseMask(maskInt);
            printBytes(" net: ", net);
            printBytes("mask: ", mask);
            System.out.println(netStr + "/" + maskInt + " " + (Utils.validNetwork(net, mask) ? "\033[1;32mtrue\033[0m" : "\033[1;31mfalse\033[0m"));
        }
        System.out.println("------------ip net/mask------------");
        for (Tuple<String, String> tuple : Arrays.asList(
            new Tuple<>("10.144.0.1", "10.144.0.0/12"), // true:  input v4, rule v4, mask v4
            new Tuple<>("10.144.0.1", "10.144.0.0/13"), // true:  input v4, rule v4, mask v4
            new Tuple<>("10.152.0.1", "10.144.0.0/12"), // true:  input v4, rule v4, mask v4
            new Tuple<>("127.0.0.1", "[0000:0000:0000:0000:0000:0000:7F00:0000]/112"), // true: input v4, rule v6, mask v6
            new Tuple<>("[0000:0000:0000:0000:0000:0000:7F00:0001]", "127.0.0.1/32"), // true: input v6, rule v4, mask v4
            new Tuple<>("[1111:0000:1000:0000:0000:0000:7F00:0001]", "127.0.0.1/32"), // true: input v6, rule v4, mask v4
            new Tuple<>("10.152.0.1", "10.144.0.0/13"), // false: input v4, rule v4, mask v4
            new Tuple<>("255.255.255.255", "[0000:0010:0000:0000:0000:0000:0000:0000]/28"), // false: input v4, rule v6, mask v4
            new Tuple<>("127.0.0.1", "[0000:0010:0000:0000:0000:0000:0000:0000]/28"), // false: input v4, rule v6, mask v4
            new Tuple<>("255.255.255.255", "[0000:0010:0000:0000:1000:0000:0000:0000]/68"), // false: input v4, rule v6, mask v4
            new Tuple<>("127.0.0.1", "[0000:0010:0000:0000:1000:0000:0000:0000]/68"), // false: input v4, rule v6, mask v6
            new Tuple<>("[1111:0000:1000:0000:0000:0000:7F00:0002]", "127.0.0.1/32") // false: input v6, rule v4, mask v4
        )) {
            String ipStr = tuple.left;
            String[] arr = tuple.right.split("/");
            String netStr = arr[0];
            int maskInt = Integer.parseInt(arr[1]);
            byte[] ip = Utils.parseAddress(ipStr);
            byte[] net = Utils.parseAddress(netStr);
            byte[] mask = Utils.parseMask(maskInt);
            printBytes("  ip: ", ip);
            printBytes(" net: ", net);
            printBytes("mask: ", mask);
            System.out.println(ipStr + " match " + tuple.right + " " + (Utils.maskMatch(ip, net, mask) ? "\033[1;32mtrue\033[0m" : "\033[1;31mfalse\033[0m"));
        }
    }

    private static void printBytes(String msg, byte[] bytes) {
        System.out.print(msg);
        for (int i = 0; i < bytes.length; ++i) {
            if (i != 0)
                System.out.print("|");
            byte b = bytes[i];
            int positive = Utils.positive(b);
            String bin = Integer.toBinaryString(positive);
            int len = 8 - bin.length();
            for (int j = 0; j < len; ++j) {
                System.out.print("0");
            }
            System.out.print(bin);
        }
        System.out.println();
    }
}
