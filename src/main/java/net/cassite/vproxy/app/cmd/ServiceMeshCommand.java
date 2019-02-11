package net.cassite.vproxy.app.cmd;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.component.auto.Sidecar;
import net.cassite.vproxy.component.exception.XException;
import net.cassite.vproxy.util.Callback;

import java.util.List;

public class ServiceMeshCommand {
    private ServiceMeshCommand() {
    }

    public static boolean isServiceMeshCommand(String line) {
        // line:
        // sadd service $service_name:$port
        // srem service $service_name:$port
        // smembers service

        String[] foo = line.split(" ");
        if (foo.length != 2 && foo.length != 3)
            return false;
        if (!foo[1].equals("service"))
            return false;
        if (foo.length == 2 && !foo[0].equalsIgnoreCase("smembers"))
            return false;
        if (foo.length == 2)
            return true; // the cmd "smembers service" is already checked, so directly return true here

        if (!foo[0].equalsIgnoreCase("sadd") && !foo[0].equalsIgnoreCase("srem"))
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
        String service = null;
        int port = 0;

        {
            String[] arr = line.split(" ");
            cmd = arr[0].toLowerCase();

            if (cmd.equals("sadd") || cmd.equals("srem")) {
                String strServicePort = arr[2];
                int idx = strServicePort.lastIndexOf(":");

                service = strServicePort.substring(0, idx);
                port = Integer.parseInt(strServicePort.substring(idx + 1));
            }
        }

        switch (cmd) {
            case "smembers":
                List<String> list = sidecar.getServices();
                StringBuilder sb = new StringBuilder();
                boolean isFirst = true;
                for (String s : list) {
                    if (isFirst) isFirst = false;
                    else sb.append("\n");
                    sb.append(s);
                }
                callback.succeeded(new CmdResult(list, list, sb.toString()));
                break;
            case "sadd":
                try {
                    sidecar.addService(service, port);
                } catch (Exception e) {
                    callback.failed(e);
                    return;
                }
                callback.succeeded(new CmdResult(null, 1 /*sadd returns integer value*/, ""));
                break;
            default:
                assert cmd.equals("srem");

                boolean removed = sidecar.maintain(service, port);
                callback.succeeded(new CmdResult(null, (removed ? 1 : 0) /*srem returns integer value*/, ""));
                break;
        }
    }
}
