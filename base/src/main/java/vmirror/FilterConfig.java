package vmirror;

import vfd.IP;
import vfd.MacAddress;
import vproxybase.util.Network;

public class FilterConfig {
    final OriginConfig originConfig;

    public MacAddress macX;
    public MacAddress macY;

    public Network netX;
    public Network netY;

    public String transportLayerProtocol;

    public int[] portX;
    public int[] portY;

    public String applicationLayerProtocol;

    public FilterConfig(OriginConfig originConfig) {
        this.originConfig = originConfig;
    }

    public boolean matchEthernet(MacAddress src, MacAddress dst) {
        if (macX != null && macY != null) {
            return (macX.equals(src) && macY.equals(dst))
                || (macY.equals(src) && macX.equals(dst));
        } else if (macX != null) {
            return macX.equals(src) || macX.equals(dst);
        } else {
            //noinspection ConstantConditions
            assert macX == null && macY == null;
            return true;
        }
    }

    boolean matchIp(MacAddress macSrc, MacAddress macDst,
                    IP ipSrc, IP ipDst) {
        if (!matchEthernet(macSrc, macDst)) {
            return false;
        }
        if (netX != null && netY != null) {
            return (netX.contains(ipSrc) && netY.contains(ipDst))
                || (netY.contains(ipSrc) && netX.contains(ipDst));
        } else if (netX != null) {
            return netX.contains(ipSrc) || netX.contains(ipDst);
        } else {
            //noinspection ConstantConditions
            assert netX == null && netY == null;
            return true;
        }
    }

    boolean matchTransport(MacAddress macSrc, MacAddress macDst,
                           IP ipSrc, IP ipDst,
                           String transportLayerProtocol,
                           int portSrc, int portDst) {
        if (!matchIp(macSrc, macDst, ipSrc, ipDst)) {
            return false;
        }
        if (this.transportLayerProtocol != null) {
            if (!this.transportLayerProtocol.equals(transportLayerProtocol)) {
                return false;
            }
        }
        if (portX != null && portY != null) {
            return ((portX[0] <= portSrc && portSrc <= portX[1]) && (portY[0] <= portDst && portDst <= portY[1]))
                || ((portY[0] <= portSrc && portSrc <= portY[1]) && (portX[0] <= portDst && portDst <= portX[1]));
        } else if (portX != null) {
            return (portX[0] <= portSrc && portSrc <= portX[1])
                || (portX[0] <= portDst && portDst <= portX[1]);
        } else {
            //noinspection ConstantConditions
            assert portX == null && portY == null;
            return true;
        }
    }

    boolean matchApplication(MacAddress macSrc, MacAddress macDst,
                             IP ipSrc, IP ipDst,
                             String transportLayerProtocol,
                             int portSrc, int portDst,
                             String applicationLayerProtocol) {
        if (!matchTransport(macSrc, macDst, ipSrc, ipDst, transportLayerProtocol, portSrc, portDst)) {
            return false;
        }
        if (this.applicationLayerProtocol != null) {
            return this.applicationLayerProtocol.equals(applicationLayerProtocol);
        }
        return true;
    }
}
