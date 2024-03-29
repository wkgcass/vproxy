package io.vproxy.buildtools;

import io.vproxy.app.app.Main;
import io.vproxy.base.util.Version;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

public class ModuleInfoGenerator {
    public static void main(String[] args) throws Exception {
        var modules = List.of("dep", "base", "core", "lib", "extended", "app");
        var requires = new LinkedHashSet<String>();
        requires.add("java.base");
        requires.add("kotlin.stdlib");
        requires.add("kotlinx.coroutines.core");
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
                    if (mod.startsWith("transitive ")) {
                        mod = mod.substring("transitive ".length()).trim();
                    }
                    if (mod.startsWith("kotlin.") || mod.startsWith("kotlinx.")) {
                        continue; // kotlin classes will be added to the final jar, so no need to require them
                    }
                    if (mod.startsWith("io.vproxy.")) {
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
        writer.newModule("io.vproxy.all");
        ModuleVisitor module = writer.visitModule("io.vproxy.all", 0, Version.VERSION);
        for (var require : requires) {
            module.visitRequire(require, 0, null);
        }
        for (var export : exports) {
            module.visitExport(export.replace(".", "/"), 0);
        }
        for (var use : uses) {
            module.visitUse(use.replace(".", "/"));
        }
        module.visitMainClass(Main.class.getName().replace(".", "/"));

        module.visitOpen("io/vproxy", 0);

        var output = writer.toByteArray();
        Files.write(Path.of("module-info.class"), output);
    }
}
