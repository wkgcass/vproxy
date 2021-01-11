package vproxybase.util.promise;

@FunctionalInterface
public interface ThrowableConsumer<T> {
    void accept(T t) throws Throwable;
}
