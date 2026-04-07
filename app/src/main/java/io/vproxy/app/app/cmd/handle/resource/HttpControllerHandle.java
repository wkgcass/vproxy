package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.AddrHandle;
import io.vproxy.app.app.cmd.handle.param.BooleanHandle;
import io.vproxy.app.controller.HttpController;
import io.vproxy.vfd.IPPort;

import java.util.ArrayList;
import java.util.List;

public class HttpControllerHandle {
    private HttpControllerHandle() {
    }

    public static void add(Command cmd) throws Exception {
        IPPort l4addr = AddrHandle.get(cmd);
        Boolean cors = BooleanHandle.get(cmd);
        String secret = cmd.args.get(Param.secret);
        Application.get().httpControllerHolder.add(cmd.resource.alias, l4addr, cors, secret);
    }

    public static List<String> names() {
        return Application.get().httpControllerHolder.names();
    }

    public static List<HttpController> details() throws Exception {
        var names = names();
        List<HttpController> ret = new ArrayList<>(names.size());
        for (var name : names) {
            ret.add(Application.get().httpControllerHolder.get(name));
        }
        return ret;
    }

    public static void removeAndStop(Command cmd) throws Exception {
        Application.get().httpControllerHolder.removeAndStop(cmd.resource.alias);
    }
}
