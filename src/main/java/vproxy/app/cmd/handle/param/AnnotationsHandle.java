package vproxy.app.cmd.handle.param;

import vjson.JSON;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.component.exception.XException;

import java.util.Map;

public class AnnotationsHandle {
    private AnnotationsHandle() {
    }

    public static Map<String, String> get(Command cmd) throws Exception {
        String anno = cmd.args.get(Param.anno);
        JSON.Object o;
        try {
            o = (JSON.Object) JSON.parse(anno);
        } catch (Exception e) {
            throw new XException("parse annotations json failed", e);
        }
        for (String key : o.keySet()) {
            if (!(o.get(key) instanceof JSON.String)) {
                throw new XException("values of annotations must be string");
            }
        }
        //noinspection UnnecessaryLocalVariable
        Map m = o.toJavaObject();
        //noinspection unchecked
        return m;
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.anno))
            throw new Exception("missing argument " + Param.anno.fullname);

        try {
            get(cmd);
        } catch (Exception e) {
            if (e instanceof XException)
                throw e;
            throw new XException("invalid format for " + Param.anno.fullname);
        }
    }
}
