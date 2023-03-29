package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vswitch.Switch;

import java.util.List;

public class TraceHandle {
    public static List<String> list(Resource swRes) throws Exception {
        Switch sw = Application.get().switchHolder.get(swRes.alias);
        return sw.getTraces();
    }

    public static void checkTraceNum(Resource res) throws XException {
        if (res.alias.equals("*")) {
            return;
        }
        int n;
        try {
            n = Integer.parseInt(res.alias);
        } catch (NumberFormatException e) {
            throw new XException("invalid " + ResourceType.trace.fullname + ": not a number");
        }
        if (n < 0) {
            throw new XException("invalid " + ResourceType.trace.fullname + ": should not be negative");
        }
    }

    public static void remove(Command cmd) throws Exception {
        Switch sw = SwitchHandle.get(cmd.prepositionResource);
        if (cmd.resource.alias.equals("*")) {
            sw.clearTraces();
        } else {
            int n = Integer.parseInt(cmd.resource.alias);
            sw.removeTrace(n);
        }
    }
}
