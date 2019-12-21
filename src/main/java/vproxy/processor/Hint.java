package vproxy.processor;

public class Hint {
    public final String hint;

    public Hint(String hint) {
        this.hint = hint;
    }

    public static final int MAX_MATCH_LEVEL = 3;

    public int matchLevel(String s) {
        if (s.contains(":")) { // remove the tailing port
            s = s.substring(0, s.lastIndexOf(":"));
        }

        if (hint.equals(s)) { // exact match
            return 3;
        }
        if (hint.endsWith("." + s)) { // hint is a sub domain name of input value
            return 2;
        }
        if (s.endsWith("." + hint)) { // input value is a sub domain name of the hint
            return 1;
        }
        return 0; // not matched
    }

    @Override
    public String toString() {
        return "Hint{" +
            "hint=" + hint +
            '}';
    }
}
