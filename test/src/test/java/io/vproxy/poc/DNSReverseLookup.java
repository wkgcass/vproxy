package vproxy.poc;

import vproxy.base.dns.*;
import vproxy.base.dns.rdata.PTR;
import vproxy.base.util.Utils;
import vproxy.base.util.callback.Callback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DNSReverseLookup {
    public static void main(String[] args) throws Exception {
        String toLookup = "220.181.108.95";
        List<String> ls = new ArrayList<>(Arrays.asList(toLookup.split("\\.")));
        Collections.reverse(ls);
        String d = String.join(".", ls);

        DNSClient dns = DNSClient.getDefault();
        Thread.sleep(1000);
        DNSPacket p = new DNSPacket();
        p.id = 0x123;
        p.isResponse = false;
        p.opcode = DNSPacket.Opcode.QUERY;
        p.aa = true;
        p.tc = false;
        p.rd = true;
        p.ra = false;
        p.rcode = DNSPacket.RCode.NoError;
        DNSQuestion q = new DNSQuestion();
        q.qname = d + ".in-addr.arpa";
        q.qtype = DNSType.PTR;
        q.qclass = DNSClass.IN;
        p.questions.add(q);
        dns.request(p, new Callback<>() {
            @Override
            protected void onSucceeded(DNSPacket value) {
                System.out.println(value);
                try {
                    System.out.println(((PTR) value.answers.get(0).rdata).ptrdname);
                } catch (RuntimeException ignore) {
                }
                Utils.exit(0);
            }

            @Override
            protected void onFailed(IOException err) {
                err.printStackTrace();
                Utils.exit(1);
            }
        });
    }
}
