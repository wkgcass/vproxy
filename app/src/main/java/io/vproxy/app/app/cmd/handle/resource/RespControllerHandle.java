package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.AddrHandle;
import io.vproxy.app.controller.RESPController;
import io.vproxy.vfd.IPPort;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RespControllerHandle {
    private RespControllerHandle() {
    }

    public static void add(Command cmd) throws Exception {
        IPPort l4addr = AddrHandle.get(cmd);
        String pass = cmd.args.get(Param.pass);
        Application.get().respControllerHolder.add(cmd.resource.alias, l4addr, pass.getBytes(StandardCharsets.UTF_8));
    }

    public static List<String> names() {
        return Application.get().respControllerHolder.names();
    }

    public static List<RESPController> details() throws Exception {
        var names = names();
        List<RESPController> ret = new ArrayList<>(names.size());
        for (var name : names) {
            ret.add(Application.get().respControllerHolder.get(name));
        }
        return ret;
    }

    public static void removeAndStop(Command cmd) throws Exception {
        Application.get().respControllerHolder.removeAndStop(cmd.resource.alias);
    }
}
