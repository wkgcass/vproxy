package net.cassite.vproxy.app.cmd;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.component.auto.Sidecar;
import net.cassite.vproxy.component.exception.XException;
import net.cassite.vproxy.util.Callback;

public class ServiceMeshCommand {
    private ServiceMeshCommand() {
    }

    public static boolean isServiceMeshCommand(String line) {
        // line:
        // sadd service $service_name:$port

        String[] foo = line.split(" ");
        if (foo.length != 3)
            return false;
        if ((!foo[0].equalsIgnoreCase("sadd") && !foo[0].equalsIgnoreCase("srem"))
            || !foo[1].equals("service"))
            return false;
        String v = foo[2];
        int idx = v.lastIndexOf(":");
        if (idx == -1)
            return false;
        String service = v.substring(0, idx);
        if (service.isEmpty())
            return false;
        String portStr = v.substring(idx + 1);
        try {
            Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static void handleCommand(String line, Callback<CmdResult, Throwable> callback) {
        Sidecar sidecar = Application.get().sidecarHolder.getSidecar();
        if (sidecar == null) {
            callback.failed(new XException("This is not a sidecar instance."));
            return;
        }

        String cmd;
        String service;
        int port;

        {
            String[] arr = line.split(" ");
            cmd = arr[0].toLowerCase();

            String strServicePort = arr[2];
            int idx = strServicePort.lastIndexOf(":");

            service = strServicePort.substring(0, idx);
            port = Integer.parseInt(strServicePort.substring(idx + 1));
        }

        if (cmd.equals("sadd")) {
            try {
                sidecar.addService(service, port);
            } catch (Exception e) {
                callback.failed(e);
                return;
            }
            callback.succeeded(new CmdResult(null, 1 /*sadd returns integer value*/, ""));
        } else {
            assert cmd.equals("srem");

            boolean removed = sidecar.maintain(service, port);
            callback.succeeded(new CmdResult(null, (removed ? 1 : 0) /*srem returns integer value*/, ""));
        }
    }
}
