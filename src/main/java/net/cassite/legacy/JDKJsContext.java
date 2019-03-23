package net.cassite.legacy;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class JDKJsContext implements JsContext {
    private final ScriptEngine engine;

    public JDKJsContext() {
        this.engine = new ScriptEngineManager().getEngineByName("js");
    }

    @Override
    public synchronized <T> T eval(String script, Class<T> type) {
        try {
            //noinspection unchecked
            return (T) engine.eval(script);
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }
}
