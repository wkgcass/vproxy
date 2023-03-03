package io.vproxy.base.util.coll;

import java.util.Objects;

public class Tuple3<A, B, C> {
    public final A _1;
    public final B _2;
    public final C _3;

    public Tuple3(A a, B b, C c) {
        _1 = a;
        _2 = b;
        _3 = c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tuple3<?, ?, ?> tuple3 = (Tuple3<?, ?, ?>) o;

        if (!Objects.equals(_1, tuple3._1)) return false;
        if (!Objects.equals(_2, tuple3._2)) return false;
        return Objects.equals(_3, tuple3._3);
    }

    @Override
    public int hashCode() {
        int result = _1 != null ? _1.hashCode() : 0;
        result = 31 * result + (_2 != null ? _2.hashCode() : 0);
        result = 31 * result + (_3 != null ? _3.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Tuple(" + _1 + ", " + _2 + ", " + _3 + ")";
    }
}
