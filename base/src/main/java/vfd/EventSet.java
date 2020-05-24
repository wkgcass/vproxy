package vfd;

import java.util.Objects;

public final class EventSet {
    private static final EventSet READABLE_ONLY = new EventSet(Event.READABLE);
    private static final EventSet WRITABLE_ONLY = new EventSet(Event.WRITABLE);
    private static final EventSet BOTH = new EventSet(Event.READABLE, Event.WRITABLE);
    private static final EventSet NONE = new EventSet();
    private final Event e1;
    private final Event e2;

    private EventSet() {
        this.e1 = null;
        this.e2 = null;
    }

    private EventSet(Event event) {
        Objects.requireNonNull(event);
        this.e1 = event;
        this.e2 = null;
    }

    private EventSet(Event e1, Event e2) {
        Objects.requireNonNull(e1);
        Objects.requireNonNull(e2);
        if (e1 == e2) {
            throw new IllegalArgumentException("e1 == e2");
        }
        this.e1 = e1;
        this.e2 = e2;
    }

    public static EventSet read() {
        return READABLE_ONLY;
    }

    public static EventSet write() {
        return WRITABLE_ONLY;
    }

    public static EventSet readwrite() {
        return BOTH;
    }

    public static EventSet none() {
        return NONE;
    }

    public boolean have(Event e) {
        Objects.requireNonNull(e);
        return e1 == e || e2 == e;
    }

    public EventSet combine(EventSet set) {
        if (this.equals(set)) {
            // combine the same set
            return this;
        }

        if (this.e2 != null) {
            // all events set, no need to modify
            return this;
        }

        if (this.e1 == null) {
            // no event set, return the input set instead
            return set;
        }

        // combine different events
        if (this.e1 == Event.READABLE) {
            if (set.have(Event.WRITABLE)) {
                return readwrite();
            }
        }
        if (this.e1 == Event.WRITABLE) {
            if (set.have(Event.READABLE)) {
                return readwrite();
            }
        }

        // otherwise they are the same
        return this;
    }

    public EventSet reduce(EventSet set) {
        if (this.equals(set)) {
            // reduce the same set
            return none();
        }

        if (this.e1 == null) {
            // no event in this set, no need to modify
            return this;
        }

        if (set.e1 == null) {
            // no event in the input set, no need to modify
            return this;
        }

        // reduce found events
        if (set.e2 != null) {
            return none();
        } else if (set.have(Event.READABLE)) {
            if (this.have(Event.READABLE)) {
                return write();
            }
        } else if (set.have(Event.WRITABLE)) {
            // set have WRITABLE
            if (this.have(Event.WRITABLE)) {
                return read();
            }
        }

        // otherwise no operations required
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventSet eventSet = (EventSet) o;
        return e1 == eventSet.e1 &&
            e2 == eventSet.e2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(e1, e2);
    }

    @Override
    public String toString() {
        if (e1 == null) {
            return "EventSet()";
        } else if (e2 != null) {
            return "EventSet(" + e1 + ", " + e2 + ")";
        } else {
            return "EventSet(" + e1 + ")";
        }
    }
}
