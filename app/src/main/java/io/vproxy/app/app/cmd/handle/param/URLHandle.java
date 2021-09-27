package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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

    public static URL[] get(String urlsStr) throws Exception {
        List<URL> urls = new ArrayList<>(1);
        String[] split = urlsStr.split(",");
        for (String url : split) {
            if (url.isBlank()) continue;
            if (!url.contains(":")) {
                return new URL[]{new File(url).toURI().toURL()};
            }
            URL urlObj;
            try {
                urlObj = new URL(url);
            } catch (MalformedURLException e) {
                throw new XException(url + " is not a valid url");
            }
            urls.add(urlObj);
        }
        URL[] ret = new URL[urls.size()];
        urls.toArray(ret);
        return ret;
    }
}
