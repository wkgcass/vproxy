package vproxybase.util.functional;

@FunctionalInterface
public interface FunctionEx<T, R, EX extends Throwable> {
    R apply(T t) throws EX;
}
