package io.vproxy.buildtools;

import io.vproxy.base.util.Version;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import io.vproxy.base.util.Version;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

public class ModuleInfoGenerator {
    public static void main(String[] args) throws Exception {
        var modules = List.of("base", "core", "lib", "extended", "app");
        var requires = new LinkedHashSet<String>();
        requires.add("java.base");
        var exports = new LinkedHashSet<String>();
        var uses = new LinkedHashSet<String>();
        for (var module : modules) {
            String content = Files.readString(Path.of(module + "/src/main/java/module-info.java"));
            String[] split = content.split("\n");
            for (String line : split) {
                line = line.trim();
                if (line.endsWith(";")) {
                    line = line.substring(0, line.length() - 1);
                }
                if (line.startsWith("requires ")) {
                    String mod = line.substring("requires ".length()).trim();
                    if (mod.startsWith("vproxy")) {
                        continue;
                    }
                    requires.add(mod);
                } else if (line.startsWith("exports ")) {
                    exports.add(line.substring("exports ".length()).trim());
                } else if (line.startsWith("uses ")) {
                    uses.add(line.substring("uses ".length()).trim());
                }
            }
        }

        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V11, Opcodes.ACC_MODULE, "module-info", null, null, null);
        writer.newModule("vproxy");
        ModuleVisitor module = writer.visitModule("vproxy", 0, Version.VERSION);
        for (var require : requires) {
            module.visitRequire(require, 0, null);
        }
        for (var export : exports) {
            module.visitExport(export.replace(".", "/"), 0);
        }
        for (var use : uses) {
            module.visitUse(use.replace(".", "/"));
        }

        var output = writer.toByteArray();
        Files.write(Path.of("module-info.class"), output);
    }
}
