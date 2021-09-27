package vproxyx;

import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxyx.pktfiltergen.flow.Flows;

import java.io.File;
import java.nio.file.Files;

public class PacketFilterGenerator {
    private static final String HELP_STR = "" +
        "PacketFilterGenerator:" +
        "\n    class={classname}             class name to be generated" +
        "\n    in={filename}                 the input file which contains flow tables" +
        "\n    out={filename}                output java code file";

    public static void main0(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println(HELP_STR);
            System.exit(1);
            return;
        }

        String cls = null;
        String in = null;
        String out = null;
        for (String arg : args) {
            if (arg.equals("help") || arg.equals("-h") || arg.equals("--help") || arg.equals("-help")) {
                System.out.println(HELP_STR);
                return;
            }
            if (arg.startsWith("class=")) {
                cls = arg.substring("class=".length());
            } else if (arg.startsWith("in=")) {
                in = arg.substring("in=".length()).trim();
            } else if (arg.startsWith("out=")) {
                out = arg.substring("out=".length()).trim();
            } else {
                Logger.warn(LogType.ALERT, "unknown argument: " + arg);
                System.out.println(HELP_STR);
                System.exit(1);
                return;
            }
        }
        if (cls == null || cls.isEmpty()) {
            Logger.warn(LogType.ALERT, "missing parameter class={...}");
            System.exit(1);
            return;
        }
        if (in == null || in.isEmpty()) {
            Logger.warn(LogType.ALERT, "missing parameter in={...}");
            System.exit(1);
            return;
        }
        if (out == null || out.isEmpty()) {
            Logger.warn(LogType.ALERT, "missing parameter out={...}");
            System.exit(1);
            return;
        }
        if (!cls.contains(".")) {
            Logger.warn(LogType.ALERT, "class name must contain packages");
            System.exit(1);
            return;
        }

        File outFile = new File(out);
        Files.createDirectories(outFile.getParentFile().toPath());

        String content = Files.readString(new File(in).toPath());

        Flows flows = new Flows();
        try {
            flows.add(content);
        } catch (Exception e) {
            Logger.warn(LogType.ALERT, e.getMessage());
            System.exit(1);
            return;
        }
        String output;
        try {
            output = flows.gen(cls);
        } catch (Exception e) {
            Logger.warn(LogType.ALERT, e.getMessage());
            System.exit(1);
            return;
        }

        Files.writeString(outFile.toPath(), output);

        Logger.alert("done");
        System.exit(0);
    }
}
