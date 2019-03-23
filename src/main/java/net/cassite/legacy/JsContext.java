package net.cassite.legacy;

public interface JsContext {
    static JsContext newContext() {
        return new JDKJsContext();
    }

    <T> T eval(String script, Class<T> type);
}
