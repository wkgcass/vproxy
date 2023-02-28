package io.vproxy.vpacket.dns.rdata;

import io.vproxy.vpacket.dns.DNSType;
import io.vproxy.vpacket.dns.Formatter;
import io.vproxy.vpacket.dns.InvalidDNSPacketException;
import io.vproxy.base.util.ByteArray;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class TXT implements RData {
    public List<String> texts = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TXT txt = (TXT) o;
        return Objects.equals(texts, txt.texts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(texts);
    }

    @Override
    public String toString() {
        return "TXT{" +
            "texts=" + texts +
            '}';
    }

    @Override
    public ByteArray toByteArray() {
        if (texts.isEmpty()) {
            return ByteArray.allocate(0);
        }
        ByteArray ret = Formatter.formatString(texts.get(0));
        for (int i = 1; i < texts.size(); ++i) {
            ret = ret.concat(Formatter.formatString(texts.get(i)));
        }
        return ret;
    }

    @Override
    public DNSType type() {
        return DNSType.TXT;
    }

    @Override
    public void fromByteArray(ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException {
        int offset = 0;
        List<String> tmp = new LinkedList<>();
        while (offset < data.length()) {
            int len = data.uint8(offset);
            ++offset;
            if (data.length() - offset < len) {
                throw new InvalidDNSPacketException("require more bytes in txt rdata field");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; ++i) {
                char c = (char) data.get(offset++);
                sb.append(c);
            }
            String s = sb.toString();
            tmp.add(s);
        }
        assert offset == data.length();
        texts.addAll(tmp);
    }
}
