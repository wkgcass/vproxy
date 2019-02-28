package net.cassite.graal;

public class PolyglotJsContext implements JsContext {
    private final PolyglotContext ctx;

    public PolyglotJsContext() {
        this.ctx = new PolyglotContext("js");
    }

    @Override
    public <T> T eval(String script, Class<T> type) {
        return ctx.eval("js", script, type);
    }
}
