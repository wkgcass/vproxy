package vproxy.test.cases;

import org.junit.Test;
import vproxy.base.prometheus.Counter;
import vproxy.base.prometheus.Gauge;
import vproxy.base.prometheus.Metrics;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestPrometheus {
    @Test
    public void counter() {
        Metrics metrics = new Metrics();

        Counter counter1 = new Counter("vproxy_test_case_counter", Map.of("class", "TestPrometheus", "method", "counter"));
        metrics.add(counter1);

        Counter counter2 = new Counter("vproxy_connections_total", Map.of("type", "tcp-lb", "alias", "tl0"));
        metrics.add(counter2);
        Counter counter3 = new Counter("vproxy_connections_total", Map.of("type", "tcp-lb", "alias", "tl1"));
        metrics.add(counter3);

        metrics.registerHelpMessage("vproxy_test_case_counter", "Some description messages");

        counter1.incr(7);
        counter2.incr(19);
        counter3.incr(91);

        assertEquals("" +
                "# TYPE vproxy_connections_total counter\n" +
                "vproxy_connections_total{alias=\"tl0\",type=\"tcp-lb\"} 19\n" +
                "vproxy_connections_total{alias=\"tl1\",type=\"tcp-lb\"} 91\n" +
                "# HELP vproxy_test_case_counter Some description messages\n" +
                "# TYPE vproxy_test_case_counter counter\n" +
                "vproxy_test_case_counter{class=\"TestPrometheus\",method=\"counter\"} 7\n" +
                "",
            metrics.toString());
    }

    @Test
    public void gauge() {
        Metrics metrics = new Metrics();

        Gauge gauge1 = new Gauge("vproxy_test_case_gauge", Map.of("class", "TestPrometheus", "method", "gauge"));
        metrics.add(gauge1);

        Gauge gauge2 = new Gauge("vproxy_connections_current", Map.of("type", "tcp-lb", "alias", "tl0"));
        metrics.add(gauge2);
        Gauge gauge3 = new Gauge("vproxy_connections_current", Map.of("type", "tcp-lb", "alias", "tl1"));
        metrics.add(gauge3);

        metrics.registerHelpMessage("vproxy_test_case_gauge", "Some description messages");

        gauge1.incr(7);
        gauge2.incr(19);
        gauge3.incr(91);

        assertEquals("" +
                "# TYPE vproxy_connections_current gauge\n" +
                "vproxy_connections_current{alias=\"tl0\",type=\"tcp-lb\"} 19\n" +
                "vproxy_connections_current{alias=\"tl1\",type=\"tcp-lb\"} 91\n" +
                "# HELP vproxy_test_case_gauge Some description messages\n" +
                "# TYPE vproxy_test_case_gauge gauge\n" +
                "vproxy_test_case_gauge{class=\"TestPrometheus\",method=\"gauge\"} 7\n" +
                "",
            metrics.toString());
    }
}
