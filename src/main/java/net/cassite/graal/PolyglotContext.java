package net.cassite.graal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class PolyglotContext {
    private final Object context;
    private final Method evalMeth;
    private final Method asMeth;

    public PolyglotContext(String... permittedLanguages) {
        try {
            // Value#as
            asMeth = Class.forName("org.graalvm.polyglot.Value").getMethod("as", Class.class);

            // class Context
            Class<?> Context = Class.forName("org.graalvm.polyglot.Context");

            // Context#eval
            evalMeth = Context.getMethod("eval", String.class, CharSequence.class);

            // context = Context.create()
            Method create = Context.getMethod("create", String[].class);
            context = create.invoke(null, (Object) permittedLanguages);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T eval(String lang, String script, Class<T> type) {
        try {
            // Value value = context.eval(lang, script)
            Object value = evalMeth.invoke(context, lang, script);
            //noinspection unchecked
            return (T) asMeth.invoke(value, type);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getTargetException());
        }
    }
}
