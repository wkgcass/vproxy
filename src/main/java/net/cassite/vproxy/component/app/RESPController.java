package net.cassite.vproxy.component.app;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.cmd.*;
import net.cassite.vproxy.component.exception.XException;
import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.protocol.ProtocolServerConfig;
import net.cassite.vproxy.protocol.ProtocolServerHandler;
import net.cassite.vproxy.redis.RESPConfig;
import net.cassite.vproxy.redis.RESPProtocolHandler;
import net.cassite.vproxy.redis.application.*;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public class RESPController {
    public final String alias;
    public final BindServer server;

    public RESPController(String alias, InetSocketAddress address, byte[] password) throws IOException {
        this.alias = alias;
        server = BindServer.create(address);
        NetEventLoop loop = Application.get().controlEventLoop;
        ProtocolServerHandler.apply(loop, server,
            new ProtocolServerConfig().setInBufferSize(16384).setOutBufferSize(16384),
            new RESPProtocolHandler(new RESPConfig().setMaxParseLen(16384),
                new RESPApplicationHandler(new RESPApplicationConfig().setPassword(password),
                    new RESPControllerApplication())));
    }

    public void stop() {
        server.close();
    }
}

class RESPControllerApplication implements RESPApplication<RESPApplicationContext> {
    @Override
    public RESPApplicationContext context() {
        return new RESPApplicationContext();
    }

    @Override
    public List<RESPCommand> commands() {
        return null; // ignore
    }

    @Override
    public void handle(Object o, RESPApplicationContext respApplicationContext, Callback<Object, Throwable> cb) {
        if (o == null) {
            cb.failed(new XException("cannot accept null"));
            return;
        }
        if (o instanceof String && ((String) o).trim().isEmpty()) {
            cb.succeeded("?"); // the input is empty, do nothing
            return;
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object e : (List) o) {
                if (!(e instanceof String)) {
                    cb.failed(new XException("invalid the command format"));
                    return;
                }
                sb.append(" ");
                String s = (String) e;
                sb.append(s);
            }
            o = sb.toString();
        }
        if (!(o instanceof String)) {
            cb.failed(new XException("invalid the command format"));
            return;
        }
        String line = ((String) o).trim();
        if (line.contains("\n") || line.contains("\r")) {
            cb.failed(new XException("invalid the command format"));
            return;
        }
        if (line.equals("help") || line.equals("man")) {
            // the `help` command is trapped by redis-cli
            // so let's use man instead
            cb.succeeded(Command.helpString().split("\n"));
            return;
        }

        boolean[] isListAction = {false}; // use array to change the variable captured by the inner class
        Callback<CmdResult, Throwable> callback = new Callback<CmdResult, Throwable>() {
            @Override
            protected void onSucceeded(CmdResult value) {
                if (value.processedResult == null) {
                    if (isListAction[0]) {
                        cb.succeeded(null); // for list operations just return list, maybe the command wants to return so
                    } else {
                        cb.succeeded("OK"); // redis usually returns OK response when something is done
                    }
                } else {
                    cb.succeeded(value.processedResult);
                }
            }

            @Override
            protected void onFailed(Throwable err) {
                cb.failed(err);
            }
        };

        if (SystemCommand.isSystemCall(line)) {
            if (!SystemCommand.allowNonStdIOController) {
                cb.failed(new XException("system call denied in RESPController"));
                return;
            }
            SystemCommand.handleSystemCall(line, callback);
        } else if (ServiceMeshCommand.isServiceMeshCommand(line)) {
            ServiceMeshCommand.handleCommand(line, callback);
        } else {
            Command cmd;
            try {
                cmd = Command.parseStrCmd(line);
            } catch (Exception e) {
                Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                    "parse cmd failed! " + Utils.formatErr(e) + " ... type `help` to show the help message");
                cb.failed(e); // callback failed
                return;
            }
            isListAction[0] = cmd.action == Action.l || cmd.action == Action.L;
            cmd.run(callback);
        }
    }
}
