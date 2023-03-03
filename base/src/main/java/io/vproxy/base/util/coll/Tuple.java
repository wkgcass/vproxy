package io.vproxy.base.util.coll;

import java.util.Objects;

public class Tuple<A, B> implements java.util.Map.Entry<A, B> {
    public final A _1;
    public final B _2;

    public final A left;
    public final B right;

    public Tuple(A _1, B _2) {
        this._1 = _1;
        this._2 = _2;
        this.left = _1;
        this.right = _2;
    }

    @Override
    public A getKey() {
        return _1;
    }

    @Override
    public B getValue() {
        return _2;
    }

    @Override
    public B setValue(B newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "Tuple(" + getKey() + ", " + getValue() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tuple<?, ?> tuple = (Tuple<?, ?>) o;

        if (!Objects.equals(_1, tuple._1)) return false;
        return Objects.equals(_2, tuple._2);
    }

    @Override
    public int hashCode() {
        int result = _1 != null ? _1.hashCode() : 0;
        result = 31 * result + (_2 != null ? _2.hashCode() : 0);
        return result;
    }
}
