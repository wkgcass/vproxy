package vproxyapp.app;

import vfd.IPPort;
import vproxybase.GlobalInspection;
import vserver.HttpServer;

import java.io.IOException;

public class GlobalInspectionHttpServerLauncher {
    private GlobalInspectionHttpServerLauncher() {
    }

    public static void launch(IPPort l4addr) throws IOException {
        new GlobalInspectionHttpServerLauncher().launch0(l4addr);
    }

    private void launch0(IPPort l4addr) throws IOException {
        HttpServer app = HttpServer.create();
        app.get("/metrics", rctx -> rctx.response().end(GlobalInspection.getInstance().toPrometheusString()));
        app.listen(l4addr);
    }
}
