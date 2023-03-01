package io.vproxy.base.util.functional;

@FunctionalInterface
public interface BooleanSupplierEx<EX extends Throwable> {
    boolean getAsBoolean() throws EX;
}
