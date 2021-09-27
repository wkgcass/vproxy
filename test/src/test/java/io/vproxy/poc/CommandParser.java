package vproxy.poc;

import vproxy.app.app.cmd.Command;

public class CommandParser {
    public static void main(String[] args) throws Exception {
        System.out.println("-----the help string-----");
        System.out.println(Command.helpString());
        System.out.println("-----let's parse:-----");
        String input = "a el eventLoop1 to elg eventLoopGroup1";
        System.out.println("input: " + input);
        System.out.println(Command.parseStrCmd(input));
    }
}
