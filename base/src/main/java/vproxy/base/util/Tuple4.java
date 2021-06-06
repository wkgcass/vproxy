package vproxy.base.util;

public class Tuple4<A, B, C, D> {
    public final A _1;
    public final B _2;
    public final C _3;
    public final D _4;

    public Tuple4(A _1, B _2, C _3, D _4) {
        this._1 = _1;
        this._2 = _2;
        this._3 = _3;
        this._4 = _4;
    }

    @Override
    public String toString() {
        return "Tuple(" + _1 + ", " + _2 + ", " + _3 + ", " + _4 + ")";
    }
}
