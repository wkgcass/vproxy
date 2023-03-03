package io.vproxy.base.util.coll;

import java.util.Objects;

public class Tuple4<A, B, C, D> {
    public final A _1;
    public final B _2;
    public final C _3;
    public final D _4;

    public Tuple4(A a, B b, C c, D d) {
        _1 = a;
        _2 = b;
        _3 = c;
        _4 = d;
    }

    public String toString() {
        return "Tuple(" + _1 + ", " + _2 + ", " + _3 + ", " + _4 + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tuple4<?, ?, ?, ?> tuple4 = (Tuple4<?, ?, ?, ?>) o;

        if (!Objects.equals(_1, tuple4._1)) return false;
        if (!Objects.equals(_2, tuple4._2)) return false;
        if (!Objects.equals(_3, tuple4._3)) return false;
        return Objects.equals(_4, tuple4._4);
    }

    @Override
    public int hashCode() {
        int result = _1 != null ? _1.hashCode() : 0;
        result = 31 * result + (_2 != null ? _2.hashCode() : 0);
        result = 31 * result + (_3 != null ? _3.hashCode() : 0);
        result = 31 * result + (_4 != null ? _4.hashCode() : 0);
        return result;
    }
}
