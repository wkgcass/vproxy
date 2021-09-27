package vproxy.base.util.functional;

@FunctionalInterface
public interface ConsumerEx<T, EX extends Throwable> {
    void accept(T t) throws EX;
}
