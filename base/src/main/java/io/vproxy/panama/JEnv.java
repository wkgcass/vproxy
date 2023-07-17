package io.vproxy.panama;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;

public class JEnv {
    public static final MemoryLayout layout = MemoryLayout.structLayout(
        ExceptionStruct.layout.withName("ex"),
        MemoryLayout.unionLayout(
            ValueLayout.JAVA_INT_UNALIGNED.withName("return_i"),
            ValueLayout.JAVA_LONG_UNALIGNED.withName("return_j"),
            ValueLayout.JAVA_BOOLEAN.withName("return_z"),
            ValueLayout.ADDRESS_UNALIGNED.withName("return_p")
        ).withName("union0")
    );

    private final Arena arena;
    private final MemorySegment seg;
    private final ExceptionStruct ex;

    public JEnv() {
        this.arena = Arena.ofConfined();
        this.seg = arena.allocate(layout.byteSize());
        this.ex = new ExceptionStruct(seg.asSlice(0, ExceptionStruct.layout.byteSize()));
    }

    public MemorySegment getSegment() {
        return seg;
    }

    public ExceptionStruct ex() {
        return ex;
    }

    private static final VarHandle return_iVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("union0"),
        MemoryLayout.PathElement.groupElement("return_i")
    );

    public int returnInt() {
        return returnInt(null);
    }

    public <EX extends Exception> int returnInt(Class<EX> exClass) throws EX {
        checkException(exClass);
        return (int) return_iVH.get(seg);
    }

    private static final VarHandle return_jVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("union0"),
        MemoryLayout.PathElement.groupElement("return_j")
    );

    public long returnLong() {
        return returnLong(null);
    }

    public <EX extends Exception> long returnLong(Class<EX> exClass) throws EX {
        checkException(exClass);
        return (long) return_jVH.get(seg);
    }

    private static final VarHandle return_zVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("union0"),
        MemoryLayout.PathElement.groupElement("return_z")
    );

    public boolean returnBool() {
        return returnBool(null);
    }

    public <EX extends Exception> boolean returnBool(Class<EX> exClass) throws EX {
        checkException(exClass);
        return (boolean) return_zVH.get(seg);
    }

    private static final VarHandle return_pVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("union0"),
        MemoryLayout.PathElement.groupElement("return_p")
    );

    public MemorySegment returnPointer() {
        return returnPointer(null);
    }

    public <EX extends Exception> MemorySegment returnPointer(Class<EX> exClass) throws EX {
        checkException(exClass);
        var seg = (MemorySegment) return_pVH.get(this.seg);
        if (seg.address() == 0) {
            return null;
        }
        return seg;
    }

    public void returnNothing() {
        returnNothing(null);
    }

    public <EX extends Exception> void returnNothing(Class<EX> exClass) throws EX {
        checkException(exClass);
    }

    private <EX extends Exception> void checkException(Class<EX> exClass) throws EX {
        var exType = ex().type();
        if (exType == null) {
            return;
        }
        var msg = ex().message();
        if (exClass == null) {
            throw new RuntimeException("unexpected exception " + exType + ", original error message: " + msg);
        }
        Class<?> cls;
        try {
            cls = Class.forName(exType);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("exception type " + exType + " not found, original error message: " + msg, e);
        }
        if (!exClass.isAssignableFrom(cls)) {
            throw new RuntimeException("expected exception class " + exClass.getName() +
                " is not assignable from actual exception class " + cls.getName() + ", original error message: " + msg);
        }
        try {
            //noinspection unchecked
            throw (EX) cls.getConstructor(String.class).newInstance(msg);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
            throw new RuntimeException("constructing exception object failed, original error message: " + msg, e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("constructing exception object failed, original error message: " + msg, e.getCause());
        }
    }

    public void resetP() {
        return_pVH.set(seg, MemorySegment.NULL);
    }

    public void resetAll() {
        resetP();
        ex().resetType();
    }
}
