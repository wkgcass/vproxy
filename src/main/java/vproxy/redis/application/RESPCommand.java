package vproxy.redis.application;

import java.util.LinkedList;
import java.util.List;

public class RESPCommand {
    public static final int F_WRITE /*----------*/ = 0b000000000000001;
    public static final int F_READONLY /*-------*/ = 0b000000000000010;
    public static final int F_DENYOOM /*--------*/ = 0b000000000000100;
    public static final int F_ADMIN /*----------*/ = 0b000000000001000;
    public static final int F_PUBSUB /*---------*/ = 0b000000000010000;
    public static final int F_NOSCRIPT /*-------*/ = 0b000000000100000;
    public static final int F_RANDOM /*---------*/ = 0b000000001000000;
    public static final int F_SORT_FOR_SCRIPT /**/ = 0b000000010000000;
    public static final int F_LOADING /*--------*/ = 0b000000100000000;
    public static final int F_STALE /*----------*/ = 0b000001000000000;
    public static final int F_SKIP_MONITOR /*---*/ = 0b000010000000000;
    public static final int F_ASKING /*---------*/ = 0b000100000000000;
    public static final int F_FAST /*-----------*/ = 0b001000000000000;
    public static final int F_MOVABLEKEYS /*----*/ = 0b010000000000000;

    static String f(int n) {
        switch (n) {
            case F_WRITE:
                return "write"; // command may result in modifications
            case F_READONLY:
                return "readonly"; // command will never modify keys
            case F_DENYOOM:
                return "denyoom"; // reject command if currently OOM
            case F_ADMIN:
                return "admin"; // server admin command
            case F_PUBSUB:
                return "pubsub"; // pubsub-related command
            case F_NOSCRIPT:
                return "noscript"; // deny this command from scripts
            case F_RANDOM:
                return "random"; // command has random results, dangerous for scripts
            case F_SORT_FOR_SCRIPT:
                return "sort_for_script"; // if called from script, sort output
            case F_LOADING:
                return "loading"; // allow command while database is loading
            case F_STALE:
                return "stale"; // allow command while replica has stale data
            case F_SKIP_MONITOR:
                return "skip_monitor"; // do not show this command in MONITOR
            case F_ASKING:
                return "asking"; // cluster related - accept even if importing
            case F_FAST:
                return "fast"; // command operates in constant or log(N) time. Used for latency monitoring.
            case F_MOVABLEKEYS:
                return "movablekeys"; // keys have no pre-determined position. You must discover keys yourself.
            default:
                throw new IllegalArgumentException("unknown flag " + Integer.toBinaryString(n));
        }
    }

    public final String name;
    public final int minParamCount;
    public final boolean mayHaveMore;
    public final int flags;
    public final int firstKeyPos;
    public final int lastKeyPos;
    public final int step;

    public RESPCommand(String name, int minParamCount, boolean mayHaveMore,
                       int flags, int firstKeyPos, int lastKeyPos, int step) {
        this.name = name;
        this.minParamCount = minParamCount;
        this.mayHaveMore = mayHaveMore;
        this.flags = flags;
        this.firstKeyPos = firstKeyPos;
        this.lastKeyPos = lastKeyPos;
        this.step = step;
    }

    private List<String> flags() {
        List<String> ls = new LinkedList<>();
        if ((flags & F_WRITE) != 0)
            ls.add(f(F_WRITE));
        if ((flags & F_READONLY) != 0)
            ls.add(f(F_READONLY));
        if ((flags & F_DENYOOM) != 0)
            ls.add(f(F_DENYOOM));
        if ((flags & F_ADMIN) != 0)
            ls.add(f(F_ADMIN));
        if ((flags & F_PUBSUB) != 0)
            ls.add(f(F_PUBSUB));
        if ((flags & F_NOSCRIPT) != 0)
            ls.add(f(F_NOSCRIPT));
        if ((flags & F_RANDOM) != 0)
            ls.add(f(F_RANDOM));
        if ((flags & F_SORT_FOR_SCRIPT) != 0)
            ls.add(f(F_SORT_FOR_SCRIPT));
        if ((flags & F_LOADING) != 0)
            ls.add(f(F_LOADING));
        if ((flags & F_STALE) != 0)
            ls.add(f(F_STALE));
        if ((flags & F_SKIP_MONITOR) != 0)
            ls.add(f(F_SKIP_MONITOR));
        if ((flags & F_ASKING) != 0)
            ls.add(f(F_ASKING));
        if ((flags & F_FAST) != 0)
            ls.add(f(F_FAST));
        if ((flags & F_MOVABLEKEYS) != 0)
            ls.add(f(F_MOVABLEKEYS));
        return ls;
    }

    @SuppressWarnings("unchecked")
    public List toList() {
        LinkedList ls = new LinkedList();
        // Command Name
        ls.add(name);
        // Command Arity
        if (mayHaveMore) ls.add(-minParamCount);
        else ls.add(minParamCount);
        // Flags
        ls.add(flags());
        // First Key in Argument List
        ls.add(firstKeyPos);
        // Last Key in Argument List
        ls.add(lastKeyPos);
        // Step Count
        ls.add(step);
        return ls;
    }
}
