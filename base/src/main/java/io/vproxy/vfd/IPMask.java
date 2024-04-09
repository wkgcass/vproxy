package io.vproxy.vfd;

public class IPMask {
    private final IP ip;
    private final IP mask;
    private final int maskInt;

    public IPMask(IP ip, int mask) {
        this.mask = IP.fromMask(ip instanceof IPv6, mask);
        this.ip = ip;
        this.maskInt = mask;
    }

    public IPMask(IP ip, IP mask) {
        if (!mask.isMask())
            throw new IllegalArgumentException(mask + " is not a valid mask");
        if (ip instanceof IPv4 && mask instanceof IPv6)
            throw new IllegalArgumentException(ip + " and " + mask + " are not in the same address family");
        if (ip instanceof IPv6 && mask instanceof IPv4)
            throw new IllegalArgumentException(ip + " and " + mask + " are not in the same address family");
        this.ip = ip;
        this.mask = mask;
        this.maskInt = mask.toPrefixLength();
    }

    public static IPMask from(String s) {
        if (!s.contains("/")) {
            throw new IllegalArgumentException("not ip/mask " + s);
        }
        var split = s.split("/");
        if (split.length != 2) {
            throw new IllegalArgumentException("not ip/mask " + s);
        }
        int mask;
        try {
            mask = Integer.parseInt(split[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
        return new IPMask(IP.from(split[0]), mask);
    }

    public IP ip() {
        return ip;
    }

    public IP mask() {
        return mask;
    }

    public int maskInt() {
        return maskInt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IPMask ipMask = (IPMask) o;

        if (maskInt != ipMask.maskInt) return false;
        if (!ip.equals(ipMask.ip)) return false;
        return mask.equals(ipMask.mask);
    }

    @Override
    public int hashCode() {
        int result = ip.hashCode();
        result = 31 * result + mask.hashCode();
        result = 31 * result + maskInt;
        return result;
    }

    @Override
    public String toString() {
        return ip + "/" + maskInt;
    }

    public String formatToIPMaskString() {
        return ip.formatToIPString() + "/" + maskInt;
    }
}
