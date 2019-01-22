package net.cassite.vproxy.app;

import net.cassite.vproxy.component.app.Shutdown;
import net.cassite.vproxy.component.app.StdIOController;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            Application.create();
        } catch (IOException e) {
            System.err.println("start application failed! " + e);
            e.printStackTrace();
            System.exit(1);
            return;
        }
        // init signal hooks
        Shutdown.init();
        // start ControlEventLoop
        Application.get().controlEventLoop.loop();
        // start stdioController
        StdIOController controller = new StdIOController();
        new Thread(controller::start, "StdIOControllerThread").start();
    }
}
