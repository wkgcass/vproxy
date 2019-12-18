package vproxy.processor;

public class Hint {
    public final String prefix;
    public final String suffix;

    public Hint(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public static final int MAX_MATCH_LEVEL = 3;

    public int matchLevel(String s) {
        int l = 0;
        if (s.startsWith(prefix)) {
            l += 2;
        }
        if (s.endsWith(suffix)) {
            l += 1;
        }
        return l;
    }

    @Override
    public String toString() {
        return "Hint{" +
            "prefix='" + prefix + '\'' +
            ", suffix='" + suffix + '\'' +
            '}';
    }
}
