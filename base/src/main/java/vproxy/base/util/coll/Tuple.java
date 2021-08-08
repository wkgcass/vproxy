package vproxy.base.util.coll;

import java.util.Map;

public class Tuple<A, B> implements Map.Entry<A, B> {
    public final A left;
    public final B right;

    public Tuple(A left, B right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public A getKey() {
        return left;
    }

    @Override
    public B getValue() {
        return right;
    }

    @Override
    public B setValue(B value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "Tuple(" + left + ", " + right + ")";
    }
}
