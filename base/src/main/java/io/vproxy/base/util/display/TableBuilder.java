package io.vproxy.base.util.display;

import java.util.ArrayList;
import java.util.List;

public class TableBuilder {
    private final List<TR> lines = new ArrayList<>();

    public TR tr() {
        TR tr = new TR();
        lines.add(tr);
        return tr;
    }

    @Override
    public String toString() {
        List<Integer> maxLen = new ArrayList<>();
        for (var tr : lines) {
            for (int i = 0; i < tr.columns.size(); i++) {
                String td = tr.columns.get(i);
                int l = td.length();
                if (maxLen.size() <= i) {
                    maxLen.add(l);
                } else {
                    int x = maxLen.get(i);
                    if (x < l) {
                        maxLen.set(i, l);
                    }
                }
            }
        }
        for (int i = 0; i < maxLen.size(); ++i) {
            int x = maxLen.get(i);
            if (x % 8 == 0) {
                x += 4;
            } else {
                x += 4 - x % 4;
            }
            maxLen.set(i, x);
        }
        StringBuilder sb = new StringBuilder();
        for (var tr : lines) {
            for (int i = 0; i < tr.columns.size(); ++i) {
                String td = tr.columns.get(i);
                sb.append(td);
                int spaces = maxLen.get(i) - td.length();
                sb.append(" ".repeat(spaces));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
