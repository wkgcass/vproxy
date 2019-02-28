package net.cassite.graal;

import net.cassite.vproxy.app.Config;

public interface JsContext {
    static JsContext newContext() {
        if (!Config.enableJs)
            throw new UnsupportedOperationException("Support for js is not enabled, " +
                "use -D+A:EnableJs=true to enable. " +
                "If you are running a graalvm native image, " +
                "you may need to rebuild the image with the property.");
        if (Config.isGraal) {
            return new PolyglotJsContext();
        } else {
            return new JDKJsContext();
        }
    }

    <T> T eval(String script, Class<T> type);
}
