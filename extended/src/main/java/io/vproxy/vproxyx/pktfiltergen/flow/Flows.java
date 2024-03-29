package io.vproxy.vproxyx.pktfiltergen.flow;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.bitwise.BitwiseIntMatcher;
import io.vproxy.base.util.bitwise.BitwiseMatcher;
import io.vproxy.base.util.misc.IntMatcher;
import io.vproxy.base.util.net.PortPool;
import io.vproxy.base.util.net.SNatIPPortPool;
import io.vproxy.base.util.ratelimit.RateLimiter;
import io.vproxy.base.util.ratelimit.SimpleRateLimiter;
import vjson.simple.SimpleString;
import io.vproxy.vfd.*;
import io.vproxy.vproxyx.pktfiltergen.IfaceHolder;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.plugin.FilterResult;

import java.util.*;
import java.util.function.Consumer;

public class Flows {
    private static final int ENABLE_CACHE_FLOW_COUNT_THRESHOLD = 65536;
    private final List<Flow> flows = new ArrayList<>();

    public void add(String linesStr) throws Exception {
        String[] lines = linesStr.split("\n");
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            var parser = new FlowParser(line);
            Flow flow;
            try {
                flow = parser.parse();
            } catch (Exception e) {
                throw new Exception("invalid input at line " + (i + 1) + ": " + e.getMessage(), e);
            }
            flows.add(flow);
        }
        check();
        sort();
    }

    private void sort() {
        flows.sort((a, b) -> {
            if (a.table != b.table) return a.table - b.table;
            return b.priority - a.priority;
        });
    }

    private void check() throws Exception {
        Set<Integer> tables = new HashSet<>();
        for (var f : flows) {
            tables.add(f.table);
        }
        Set<Integer> gotoTables = new HashSet<>();
        for (var f : flows) {
            for (var action : f.actions) {
                if (action.table == 0) {
                    continue;
                }
                gotoTables.add(action.table);
                if (tables.contains(action.table)) {
                    continue;
                }
                throw new Exception("invalid flow, table " + action.table + " not found: " + f);
            }
        }
        for (int table : tables) {
            if (table == 0) {
                continue;
            }
            if (gotoTables.contains(table)) {
                continue;
            }
            throw new Exception("table " + table + " is not reachable");
        }
    }

    @SuppressWarnings("ConcatenationWithEmptyString")
    private static final String GEN_CLASS_TEMPLATE = "" +
        "{{Imports}}" +
        "\n" +
        "public class {{ClassSimpleName}} extends {{ParentSimpleName}} {\n" +
        "{{Fields}}" +
        "    public {{ClassSimpleName}}() {\n" +
        "        super();" +
        "{{EnableIngressCache}}" +
        "{{RegisterIfaceHolders}}" +
        "    }\n" +
        "\n" +
        "    @Override\n" +
        "    protected FilterResult handleIngress(PacketFilterHelper helper, PacketBuffer pkb) {\n" +
        "        return table0(helper, pkb);\n" +
        "    }\n" +
        "{{FlowTables}}" +
        "{{Actions}}" +
        "}\n";
    @SuppressWarnings("ConcatenationWithEmptyString")
    private static final String DEFAULT_EMPTY_TABLE_0 = "" +
        "\n" +
        "    private FilterResult table0(PacketFilterHelper helper, PacketBuffer pkb) {\n" +
        "        return FilterResult.DROP;\n" +
        "    }\n";

    public String gen(String fullClassName) {
        return gen(fullClassName, "io.vproxy.app.plugin.impl.BasePacketFilter");
    }

    public String gen(String fullClassName, String parentFullClassname) {
        GenContext ctx = new GenContext();

        ctx.ensureImport(parentFullClassname);
        ctx.ensureImport(FilterResult.class);
        ctx.ensureImport(PacketFilterHelper.class);
        ctx.ensureImport(PacketBuffer.class);

        for (var flow : flows) {
            if (flow.matcher.in_port != null) {
                ctx.registerIface(flow.matcher.in_port);
            }
            for (var action : flow.actions) {
                if (action.output != null) {
                    ctx.registerIface(action.output);
                }
            }
        }

        String packagePrefix;
        if (fullClassName.contains(".")) {
            String packageName = fullClassName.substring(0, fullClassName.lastIndexOf("."));
            //noinspection ConcatenationWithEmptyString
            packagePrefix = "" +
                "package " + packageName + ";\n" +
                "\n";
        } else {
            packagePrefix = "";
        }
        String classSimpleName;
        if (fullClassName.contains(".")) {
            classSimpleName = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        } else {
            classSimpleName = fullClassName;
        }
        String parentSimpleName;
        if (parentFullClassname.contains(".")) {
            parentSimpleName = parentFullClassname.substring(parentFullClassname.lastIndexOf(".") + 1);
        } else {
            parentSimpleName = fullClassName;
        }
        String enableIngressCache = genEnableIngressCache();
        String registerIfaceHolders = genRegisterIfaceHolders(ctx);
        String flowTables = genFlowTables(ctx);
        String fields = genFields(ctx);
        String imports = genImports(ctx);
        String actions = formatActions(ctx);

        return packagePrefix + GEN_CLASS_TEMPLATE
            .replace("{{Imports}}", imports)
            .replace("{{ClassSimpleName}}", classSimpleName)
            .replace("{{ParentSimpleName}}", parentSimpleName)
            .replace("{{Fields}}", fields)
            .replace("{{EnableIngressCache}}", enableIngressCache)
            .replace("{{RegisterIfaceHolders}}", registerIfaceHolders)
            .replace("{{FlowTables}}", flowTables)
            .replace("{{Actions}}", actions)
            ;
    }

    private String genEnableIngressCache() {
        if (flows.size() < ENABLE_CACHE_FLOW_COUNT_THRESHOLD) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        genNewLine(sb, 8);
        sb.append("setEnableIngressCache(true);");
        return sb.toString();
    }

    private String genRegisterIfaceHolders(GenContext ctx) {
        if (ctx.indexedIfaces.isEmpty()) {
            return "\n";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.indexedIfaces.size(); ++i) {
            genNewLine(sb, 8);
            sb.append("registerIfaceHolder(this.ifaces[").append(i).append("]);");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String genFlowTables(GenContext ctx) {
        if (flows.isEmpty()) {
            return DEFAULT_EMPTY_TABLE_0;
        }
        ArrayList<List<Flow>> tableFlows = new ArrayList<>();
        int lastTable = -1;
        List<Flow> lastList = null;
        for (var flow : flows) {
            if (flow.table != lastTable) {
                lastList = new ArrayList<>();
                lastTable = flow.table;
                tableFlows.add(lastList);
            }
            assert lastList != null;
            lastList.add(flow);
        }
        StringBuilder sb = new StringBuilder();
        for (var flows : tableFlows) {
            sb.append(genFlowTables(ctx, flows));
        }
        return sb.toString();
    }

    private String genFlowTables(GenContext ctx, List<Flow> flows) {
        int table = flows.get(0).table;
        return genTable(table, sb -> {
            for (var flow : flows) {
                ctx.currentFlow = flow;
                boolean ret = genFlowTables(ctx, sb, flow);
                ctx.currentFlow = null;
                if (ret) {
                    sb.append("\n");
                    return;
                }
            }
            // add default return value
            genNewLine(sb, 8);
            sb.append("return FilterResult.DROP;\n");
        });
    }

    private boolean genFlowTables(GenContext ctx, StringBuilder sb, Flow flow) {
        String cond = flow.matcher.toIfConditionString(ctx);
        if (!cond.isEmpty()) {
            genNewLine(sb, 8);
            sb.append("if (").append(cond).append(") {");
        }

        StringBuilder actions = new StringBuilder();
        boolean lastIsGoto = flow.actions.get(flow.actions.size() - 1).table != 0;
        boolean onlyOneOutput = true;
        {
            int cnt = 0;
            for (var action : flow.actions) {
                if (action.output != null) {
                    ++cnt;
                    if (cnt > 1) {
                        onlyOneOutput = false;
                        break;
                    }
                    continue;
                }
                if (action.allowTerminating()) {
                    onlyOneOutput = false;
                    break;
                }
            }
        }
        if (onlyOneOutput) {
            for (int i = 0; i < flow.actions.size() - 1; i++) {
                var action = flow.actions.get(i);
                if (action.table != 0) {
                    break;
                }
                String stmt = formatAndAppendActionStatementString(actions, action.toStatementString(ctx));
                if (stmt.startsWith("return ")) {
                    break;
                }
            }
            actions.append("return helper.redirect(pkb, ifaces[").
                append(ctx.ifaceIndex(flow.actions.get(flow.actions.size() - 1).output))
                .append("].iface);");
        } else {
            boolean hasReturn = false;
            for (var action : flow.actions) {
                if (action.table != 0) {
                    break;
                }
                String stmt = formatAndAppendActionStatementString(actions, action.toStatementString(ctx));
                if (stmt.startsWith("return ")) {
                    hasReturn = true;
                    break;
                }
            }
            if (!hasReturn) {
                if (lastIsGoto) {
                    actions.append("return FilterResult.PASS;");
                } else {
                    actions.append("return FilterResult.DROP;");
                }
                actions.append("\n"); // make the `actions` string always ends with '\n'
            }
        }

        genNewLine(sb, cond.isEmpty() ? 8 : 12);
        if (!lastIsGoto || flow.actions.size() > 1) {
            int actionIndex = ctx.registerAction(actions.toString());
            sb.append("return execute(helper, pkb, this::action").append(actionIndex).append(");");
        } else {
            // only generate one goto stmt
            sb.append(flow.actions.get(0).toStatementString(ctx)).append(";");
        }

        if (!cond.isEmpty()) {
            genNewLine(sb, 8);
            sb.append("}");
        }
        return cond.isEmpty();
    }

    private String formatAndAppendActionStatementString(StringBuilder actions, String stmt) {
        String[] lines = stmt.split("\n");
        if (lines.length == 1) {
            actions.append(stmt).append(";\n");
            return stmt;
        }
        actions.append(lines[0]);
        for (int i = 1; i < lines.length; ++i) {
            genNewLine(actions, 0);
            var line = lines[i];
            line = line.replace("\t", "    ");
            actions.append(line);
        }
        actions.append("\n");
        return stmt;
    }

    private String genTable(int index, Consumer<StringBuilder> run) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n")
            .append("    private FilterResult table").append(index).append("(PacketFilterHelper helper, PacketBuffer pkb) {");
        run.accept(sb);
        sb
            .append("    }\n");
        return sb.toString();
    }

    private void genIndent(StringBuilder sb, int indent) {
        sb.append(" ".repeat(indent));
    }

    private void genNewLine(StringBuilder sb, int indent) {
        sb.append("\n");
        genIndent(sb, indent);
    }

    private String genFields(GenContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (MacAddress mac : ctx.macFields) {
            ctx.ensureImport(MacAddress.class);
            genIndent(sb, 4);
            sb.append("private static final MacAddress")
                .append(" ")
                .append(ctx.fieldName(mac)).append(" = ")
                .append("new MacAddress(\"")
                .append(mac)
                .append("\");\n");
        }
        for (IPv4 ip : ctx.ipv4Fields) {
            ctx.ensureImport(IPv4.class);
            ctx.ensureImport(IP.class);
            genIndent(sb, 4);
            sb.append("private static final IPv4 ")
                .append(ctx.fieldName(ip)).append(" = ")
                .append("(IPv4) IP.from(\"")
                .append(ip.formatToIPString())
                .append("\");\n");
        }
        for (IPv6 ip : ctx.ipv6Fields) {
            ctx.ensureImport(IPv6.class);
            ctx.ensureImport(IP.class);
            genIndent(sb, 4);
            var ipStr = ip.formatToIPString();
            if (ipStr.startsWith("[")) {
                ipStr = ipStr.substring(1, ipStr.length() - 1);
            }
            sb.append("private static final IPv6 ")
                .append(ctx.fieldName(ip)).append(" = ")
                .append("(IPv6) IP.from(\"")
                .append(ipStr)
                .append("\");\n");
        }
        for (BitwiseMatcher matcher : ctx.bitwiseMatcherFields) {
            ctx.ensureImport(BitwiseMatcher.class);
            ctx.ensureImport(ByteArray.class);
            genIndent(sb, 4);
            sb.append("private static final BitwiseMatcher ")
                .append(ctx.fieldName(matcher)).append(" = ")
                .append("BitwiseMatcher.from(")
                .append("ByteArray.fromHexString(\"")
                .append(matcher.getMatcher().toHexString()).append("\")")
                .append(", ")
                .append("ByteArray.fromHexString(\"")
                .append(matcher.getMask().toHexString()).append("\")")
                .append(");\n");
        }
        for (IntMatcher matcher : ctx.intMatcherFields) {
            ctx.ensureImport(IntMatcher.class);
            genIndent(sb, 4);
            sb.append("private static final IntMatcher ")
                .append(ctx.fieldName(matcher)).append(" = ");
            if (matcher instanceof BitwiseIntMatcher) {
                ctx.ensureImport(BitwiseIntMatcher.class);
                sb.append("BitwiseIntMatcher.from(")
                    .append(((BitwiseIntMatcher) matcher).getMatcher()).append(", ")
                    .append(((BitwiseIntMatcher) matcher).getMask()).append(");\n");
            } else if (matcher instanceof PortPool) {
                ctx.ensureImport(PortPool.class);
                sb.append("new PortPool(\"")
                    .append(((PortPool) matcher).serialize())
                    .append("\");\n");
            } else {
                throw new UnsupportedOperationException("unsupported int matcher: " + matcher);
            }
        }
        for (IPPort ipport : ctx.ipportFields) {
            ctx.ensureImport(IPPort.class);
            genIndent(sb, 4);
            sb.append("private static final IPPort ")
                .append(ctx.fieldName(ipport)).append(" = ")
                .append("new IPPort(\"")
                .append(ipport.formatToIPPortString())
                .append("\");\n");
        }
        for (SNatIPPortPool pool : ctx.ipportPoolFields) {
            ctx.ensureImport(SNatIPPortPool.class);
            genIndent(sb, 4);
            sb.append("private final SNatIPPortPool ")
                .append(ctx.fieldName(pool)).append(" = ")
                .append("new SNatIPPortPool(\"")
                .append(pool.serialize())
                .append("\");\n");
        }
        if (!ctx.indexedIfaces.isEmpty()) {
            genIndent(sb, 4);
            ctx.ensureImport(IfaceHolder.class);
            sb.append("private final IfaceHolder[] ifaces = new IfaceHolder[]{");
            for (int i = 0; i < ctx.indexedIfaces.size(); ++i) {
                if (i != 0) {
                    sb.append(",");
                }
                genNewLine(sb, 8);
                sb.append("new IfaceHolder(").append(new SimpleString(ctx.indexedIfaces.get(i)).stringify()).append(", null)");
            }
            genNewLine(sb, 4);
            sb.append("};\n");
        }
        if (!ctx.rateLimiters.isEmpty()) {
            genIndent(sb, 4);
            ctx.ensureImport(RateLimiter.class);
            ctx.ensureImport(SimpleRateLimiter.class);
            sb.append("private final RateLimiter[] ratelimiters = new RateLimiter[]{");
            for (int i = 0; i < ctx.rateLimiters.size(); ++i) {
                if (i != 0) {
                    sb.append(",");
                }
                genNewLine(sb, 8);
                var rl = ctx.rateLimiters.get(i);
                sb.append("new SimpleRateLimiter(").append(rl.capacity).append(", ").append(rl.fillRate).append(")");
            }
            genNewLine(sb, 4);
            sb.append("};\n");
        }
        if (sb.length() != 0) {
            sb.append("\n");
        }
        return sb.toString();
    }

    private String genImports(GenContext ctx) {
        StringBuilder sb = new StringBuilder();
        var imports = new ArrayList<>(ctx.imports);
        imports.sort(String::compareTo);
        for (String cls : imports) {
            sb.append("import ").append(cls).append(";\n");
        }
        return sb.toString();
    }

    private String formatActions(GenContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.actions.size(); ++i) {
            String action = ctx.actions.get(i);
            String[] lines = action.split("\n");
            genNewLine(sb, 4);
            sb.append("private FilterResult action").append(i).append("(PacketFilterHelper helper, PacketBuffer pkb) {");
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                genNewLine(sb, 8);
                sb.append(line);
            }
            genNewLine(sb, 4);
            sb.append("}");
            sb.append("\n");
        }
        for (String method : ctx.predicateMethods) {
            genNewLine(sb, 4);
            sb.append("protected boolean predicate_").append(method).append("(PacketFilterHelper helper, PacketBuffer pkb) {");
            genNewLine(sb, 8);
            sb.append("return false;");
            genNewLine(sb, 4);
            sb.append("}\n");
        }
        for (String method : ctx.runnableMethods) {
            genNewLine(sb, 4);
            sb.append("protected void run_").append(method).append("(PacketFilterHelper helper, PacketBuffer pkb) {");
            genNewLine(sb, 4);
            sb.append("}\n");
        }
        for (String method : ctx.invocationMethods) {
            genNewLine(sb, 4);
            sb.append("protected FilterResult invoke_").append(method).append("(PacketFilterHelper helper, PacketBuffer pkb) {");
            genNewLine(sb, 8);
            sb.append("return FilterResult.DROP;");
            genNewLine(sb, 4);
            sb.append("}\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Flow flow : flows) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append("\n");
            }
            sb.append(flow);
        }
        return sb.toString();
    }

    public static class GenContext {
        private final Set<String> imports = new LinkedHashSet<>();
        private final ArrayList<String> indexedIfaces = new ArrayList<>();
        private final List<SimpleRateLimiter> rateLimiters = new ArrayList<>();
        private final Set<MacAddress> macFields = new HashSet<>();
        private final Set<IPv4> ipv4Fields = new HashSet<>();
        private final Set<IPv6> ipv6Fields = new HashSet<>();
        private final List<BitwiseMatcher> bitwiseMatcherFields = new ArrayList<>();
        private final List<IntMatcher> intMatcherFields = new ArrayList<>();
        private final Set<IPPort> ipportFields = new HashSet<>();
        private final List<SNatIPPortPool> ipportPoolFields = new ArrayList<>();
        private final List<String> actions = new ArrayList<>();
        private final LinkedHashSet<String> predicateMethods = new LinkedHashSet<>();
        private final LinkedHashSet<String> runnableMethods = new LinkedHashSet<>();
        private final LinkedHashSet<String> invocationMethods = new LinkedHashSet<>();

        private Flow currentFlow;

        public void ensureImport(Class<?> cls) {
            imports.add(cls.getName());
        }

        public void ensureImport(String cls) {
            imports.add(cls);
        }

        public void registerIface(String name) {
            if (indexedIfaces.contains(name)) {
                return;
            }
            indexedIfaces.add(name);
        }

        public int ifaceIndex(String name) {
            return indexedIfaces.indexOf(name);
        }

        public int registerAction(String action) {
            int thisIndex = actions.size();
            actions.add(action);
            return thisIndex;
        }

        public String fieldName(MacAddress mac) {
            macFields.add(mac);
            return "MAC_HOLDER_" + mac.toString().replace(":", "_");
        }

        public String fieldName(IPv4 ip) {
            ipv4Fields.add(ip);
            return "IPv4_HOLDER_" + ip.formatToIPString().replace(".", "_");
        }

        public String fieldName(IPv6 ip) {
            ipv6Fields.add(ip);
            var ipStr = ip.formatToIPString();
            if (ipStr.startsWith("[")) {
                ipStr = ipStr.substring(1, ipStr.length() - 1);
            }
            return "IPv6_HOLDER_" + ipStr.replace(":", "_");
        }

        public String fieldName(IP ip) {
            if (ip instanceof IPv6) {
                return fieldName((IPv6) ip);
            } else {
                return fieldName((IPv4) ip);
            }
        }

        public String fieldName(BitwiseMatcher matcher) {
            int idx = bitwiseMatcherFields.indexOf(matcher);
            if (idx == -1) {
                idx = bitwiseMatcherFields.size();
                bitwiseMatcherFields.add(matcher);
            }
            return "BITWISE_MATCHER_HOLDER_" + idx;
        }

        public String fieldName(IntMatcher matcher) {
            int idx = intMatcherFields.indexOf(matcher);
            if (idx == -1) {
                idx = intMatcherFields.size();
                intMatcherFields.add(matcher);
            }
            return "BITWISE_INT_MATCHER_HOLDER_" + idx;
        }

        public String fieldName(IPPort ipport) {
            ipportFields.add(ipport);
            if (ipport.getAddress() instanceof IPv4) {
                return "IPv4PORT_HOLDER_" + ipport.getAddress().formatToIPString().replace(".", "_") + "$" + ipport.getPort();
            } else {
                return "IPv6PORT_HOLDER_" + ipport.getAddress().formatToIPString().replace(":", "_") + "$" + ipport.getPort();
            }
        }

        public String fieldName(SNatIPPortPool pool) {
            int idx = ipportPoolFields.indexOf(pool);
            if (idx == -1) {
                idx = ipportPoolFields.size();
                ipportPoolFields.add(pool);
            }
            return "IPPORTPOOL_HOLDER_" + idx;
        }

        public Flow getCurrentFlow() {
            return currentFlow;
        }

        public int newBPSRateLimiter(long bps) {
            int idx = rateLimiters.size();
            rateLimiters.add(new SimpleRateLimiter(bps, bps / 1000 + (bps % 1000 == 0 ? 0 : 1)));
            return idx;
        }

        public int newPPSRateLimiter(long pps) {
            int idx = rateLimiters.size();
            rateLimiters.add(new SimpleRateLimiter(pps, pps / 1000 + (pps % 1000 == 0 ? 0 : 1)));
            return idx;
        }

        public void registerPredicateMethod(String name) {
            predicateMethods.add(name);
        }

        public void registerRunnableMethods(String name) {
            runnableMethods.add(name);
        }

        public void registerInvocationMethods(String name) {
            invocationMethods.add(name);
        }
    }
}
