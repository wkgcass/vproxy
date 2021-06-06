package vproxy.app.app;

import vproxy.base.GlobalInspection;
import vproxy.vfd.IPPort;
import vproxy.vserver.HttpServer;

import java.io.IOException;

public class GlobalInspectionHttpServerLauncher {
    private static final GlobalInspectionHttpServerLauncher instance = new GlobalInspectionHttpServerLauncher();

    private GlobalInspectionHttpServerLauncher() {
    }

    public static void launch(IPPort l4addr) throws IOException {
        instance.launch0(l4addr);
    }

    public static void stop() {
        instance.stop0();
    }

    private HttpServer app;

    private void launch0(IPPort l4addr) throws IOException {
        if (app != null) {
            throw new IOException("GlobalInspectionHttpServer already started: " + app);
        }
        app = HttpServer.create();
        app.get("/metrics", rctx -> rctx.response().end(GlobalInspection.getInstance().getPrometheusString()));
        app.get("/lsof", rctx -> GlobalInspection.getInstance().getOpenFDs(rctx.response()::end));
        app.get("/jstack", rctx -> rctx.response().end(GlobalInspection.getInstance().getStackTraces()));
        app.listen(l4addr);
    }

    private void stop0() {
        if (app != null) {
            app.close();
            app = null;
        }
    }
}
