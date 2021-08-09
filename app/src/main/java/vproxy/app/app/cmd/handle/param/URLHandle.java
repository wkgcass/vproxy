package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class URLHandle {
    private URLHandle() {
    }

    public static URL[] get(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.url)) {
            throw new XException("missing argument: " + Param.url.fullname);
        }
        String url = cmd.args.get(Param.url);
        return get(url);
    }

    public static URL[] get(String url) throws Exception {
        if (!url.contains(":")) {
            return new URL[]{new File(url).toURI().toURL()};
        }
        URL urlObj;
        try {
            urlObj = new URL(url);
        } catch (MalformedURLException e) {
            throw new XException(url + " is not a valid url");
        }
        return new URL[]{urlObj};
    }
}
