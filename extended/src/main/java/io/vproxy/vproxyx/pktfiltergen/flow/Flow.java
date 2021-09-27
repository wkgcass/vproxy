package vproxyx.pktfiltergen.flow;

import java.util.ArrayList;
import java.util.List;

public class Flow {
    public int table = 0;
    public int priority = 0;
    public final FlowMatcher matcher = new FlowMatcher();
    public final List<FlowAction> actions = new ArrayList<>();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("table=").append(table).append(",priority=").append(priority);
        String matcherString = matcher.toString();
        if (!matcherString.isEmpty()) {
            sb.append(",").append(matcherString);
        }
        sb.append(",actions=");
        boolean isFirst = true;
        for (var action : actions) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(",");
            }
            sb.append(action);
        }
        return sb.toString();
    }
}
