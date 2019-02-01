package net.cassite.vproxy.test.tool;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class CaseUtils {
    private CaseUtils() {
    }

    public static String ipv4OtherThan127() throws SocketException {
        String address = null;
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        out:
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            Enumeration<InetAddress> ips = nic.getInetAddresses();
            while (ips.hasMoreElements()) {
                InetAddress addr = ips.nextElement();
                if (addr instanceof Inet4Address && !addr.getHostAddress().equals("127.0.0.1")) {
                    address = addr.getHostAddress();
                    break out;
                }
            }
        }
        return address;
    }
}
