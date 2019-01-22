package net.cassite.vproxy.component.app;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.util.Callback;

import java.util.Scanner;

public class StdIOController {
    @SuppressWarnings("InfiniteLoopStatement")
    public void start() {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty())
                continue;
            if (line.equals("h") || line.equals("help")) {
                System.out.println(Command.helpString());
                continue;
            }
            if (line.startsWith("System call:")) {
                String cmd = line.substring("System call:".length()).trim();
                switch (cmd) {
                    case "help":
                        System.out.println(Command.helpString());
                        break;
                    case "shutdown":
                        Shutdown.shutdown();
                        break;
                    default:
                        System.err.println("unknown system call `" + cmd + "`");
                        continue;
                }
                continue;
            }
            Command c;
            try {
                c = Command.parseStrCmd(line);
            } catch (Exception e) {
                System.err.println("parse cmd failed! " + e.getMessage() + "... type `help` to show the help message");
                continue;
            }
            c.run(new Callback<String, Throwable>() {
                @Override
                protected void onSucceeded(String resp) {
                    if (!resp.trim().isEmpty()) {
                        System.out.println(resp.trim());
                    }
                }

                @Override
                protected void onFailed(Throwable err) {
                    String msg = "command `" + line + "` failed! ";
                    if (err.getMessage() != null && !err.getMessage().trim().isEmpty()) {
                        msg += err.getMessage().trim();
                    } else {
                        msg += err.toString();
                    }
                    System.err.println(msg);
                }
            });
        }
    }
}
