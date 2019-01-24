package net.cassite.vproxy.example;

public class RunAll {
    public static void main(String[] args) throws Exception {
        System.out.println("==============================================");
        System.out.println("     selector event loop echo server");
        System.out.println("==============================================");
        SelectorEventLoopEchoServer.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("         net event loop echo server");
        System.out.println("==============================================");
        NetEventLoopEchoServer.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("   net event loop split buffers echo server");
        System.out.println("==============================================");
        NetEventLoopSplitBuffersEchoServer.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("            proxy an echo server");
        System.out.println("==============================================");
        ProxyEchoServer.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("             health check client");
        System.out.println("==============================================");
        HealthCheckClientExample.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("                server group");
        System.out.println("==============================================");
        ServerGroupExample.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("             lb for echo servers");
        System.out.println("==============================================");
        LBForEchoServers.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("                parse commands");
        System.out.println("==============================================");
        CommandParser.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("                 parse resp");
        System.out.println("==============================================");
        TestRESPParser.main(new String[0]);

        System.out.println("==============================================");
        System.out.println("            echo protocol server");
        System.out.println("==============================================");
        EchoProtocolServer.main(new String[0]);
    }
}
