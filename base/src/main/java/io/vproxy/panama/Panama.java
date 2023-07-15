package io.vproxy.panama;

import java.lang.foreign.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class Panama {
    private static final Panama INSTANCE = new Panama();

    private Panama() {
    }

    public static Panama get() {
        return INSTANCE;
    }

    public Memory allocateNative(long size) {
        var arena = Arena.ofShared();
        var seg = arena.allocate(size, 1);
        return new Memory(arena, seg);
    }

    public WrappedFunction lookupWrappedFunction(String functionName, Class... parameterTypes) {
        var nativeLinker = Linker.nativeLinker();
        var loaderLookup = SymbolLookup.loaderLookup();
        var stdlibLookup = nativeLinker.defaultLookup();
        var h = loaderLookup.find(functionName)
            .or(() -> stdlibLookup.find(functionName))
            .map(m -> nativeLinker.downcallHandle(m, buildFunctionDescriptor(parameterTypes)))
            .orElse(null);
        if (h == null) {
            throw new UnsatisfiedLinkError(functionName + Arrays.stream(parameterTypes).map(Class::getSimpleName).collect(Collectors.joining(", ", "(", ")")));
        }
        return new WrappedFunction(h);
    }

    private FunctionDescriptor buildFunctionDescriptor(Object[] parameterTypes) {
        MemoryLayout[] layouts = new MemoryLayout[parameterTypes.length + 1];
        layouts[0] = ValueLayout.ADDRESS; // JEnv*
        for (int i = 0; i < parameterTypes.length; ++i) {
            layouts[i + 1] = buildMemoryLayout(parameterTypes[i]);
        }
        return FunctionDescriptor.ofVoid(layouts);
    }

    private MemoryLayout buildMemoryLayout(Object type) {
        if (type instanceof MemoryLayout l) {
            return l;
        }
        if (!(type instanceof Class cls)) {
            throw new IllegalArgumentException("invalid input for function argument: " + type);
        }
        if (type == int.class) {
            return ValueLayout.JAVA_INT;
        } else if (type == long.class) {
            return ValueLayout.JAVA_LONG;
        } else if (type == short.class) {
            return ValueLayout.JAVA_SHORT;
        } else if (type == boolean.class) {
            return ValueLayout.JAVA_BOOLEAN;
        } else if (type == String.class) {
            return ValueLayout.ADDRESS; // char*
        } else if (ByteBuffer.class.isAssignableFrom(cls)) {
            return ValueLayout.ADDRESS;
        } else if (type == MemorySegment.class) {
            return ValueLayout.ADDRESS;
        } else if (MemoryLayout.class.isAssignableFrom(cls)) {
            return ValueLayout.ADDRESS;
        } else if (type == Object.class) { // void*
            return ValueLayout.ADDRESS;
        } else {
            throw new IllegalArgumentException("unsupported type, unable to convert to MemoryLayout: " + type);
        }
    }

    public static MemorySegment format(String arg, Arena arena) {
        var bytes = arg.getBytes(StandardCharsets.UTF_8);
        var seg = arena.allocate(bytes.length + 1, 1);
        seg.setUtf8String(0, arg);
        return seg;
    }

    public static MemorySegment format(ByteBuffer b) {
        int pos = b.position();
        var seg = MemorySegment.ofBuffer(b);
        return MemorySegment
            .ofAddress(seg.address() - pos)
            .reinterpret(b.capacity());
    }
}
