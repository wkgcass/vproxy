package io.vproxy.all;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

@SuppressWarnings({"unused", "UnreachableCode"})
public class GraalFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        loadFeature(access, "io.vproxy.base.NativeAccessGraalFeature");
        loadFeature(access, "io.vproxy.msquic.Feature");

        var reflectClasses = List.of(
            io.vproxy.app.app.cmd.Action.class,
            io.vproxy.app.app.cmd.ResourceType.class,
            io.vproxy.app.app.cmd.Preposition.class,
            io.vproxy.app.app.cmd.Flag.class,
            io.vproxy.app.app.cmd.Param.class
        );
        for (var cls : reflectClasses) {
            RuntimeReflection.register(cls);
            var fields = cls.getFields();
            for (var f : fields) {
                RuntimeReflection.register(f);
            }
        }
        var dynamicLibraries = List.of(
            "pni",
            "fubuki",
            "fubukil",
            "vpfubuki",
            "msquic",
            "msquic-java",
            "bpf",
            "vpxdp",
            "vfdposix",
            "vfdwindows"
        );
        var arch = List.of(
            "x86_64",
            "aarch64"
        );
        for (var l : dynamicLibraries) {
            for (var a : arch) {
                var linux = "lib" + l + "-" + a + ".so";
                var macos = "lib" + l + "-" + a + ".dylib";
                var windows = l + "-" + a + ".dll";
                var paths = List.of(linux, macos, windows);
                for (var p : paths) {
                    RuntimeResourceAccess.addResource(GraalFeature.class.getModule(), "io/vproxy/" + p);
                }
            }
        }
    }

    private void loadFeature(DuringSetupAccess access, String featureClass) {
        Class<?> cls;
        try {
            cls = Class.forName(featureClass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("unable to find class: " + featureClass);
        }
        Constructor<?> cons;
        try {
            cons = cls.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("unable to retrieve default constructor from: " + featureClass);
        }
        Object o;
        try {
            o = cons.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("unable to construct " + featureClass);
        }
        if (!(o instanceof Feature f)) {
            throw new RuntimeException(o.getClass() + " is not subtype of " + Feature.class);
        }
        f.duringSetup(access);
    }
}
