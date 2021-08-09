package vproxy.app.plugin;

import vproxy.base.util.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

public class PluginLoader {
    private PluginLoader() {
    }

    public static Plugin load(URL[] urls, String classname) throws Exception {
        URLClassLoader loader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        Class<?> cls;
        try {
            cls = loader.loadClass(classname);
        } catch (Exception e) {
            throw new Exception("loading class " + classname + " failed, err: " + Utils.formatErr(e), e);
        }
        if (!Plugin.class.isAssignableFrom(cls)) {
            throw new Exception(classname + " is not a sub type of " + Plugin.class.getName());
        }
        Constructor<?> cons;
        try {
            cons = cls.getConstructor();
        } catch (Exception e) {
            throw new Exception("class " + classname + " does not have a public empty parameter constructor");
        }
        Object o;
        try {
            o = cons.newInstance();
        } catch (InvocationTargetException e) {
            throw new Exception("failed to instantiate " + classname + ", err: " + Utils.formatErr(e.getTargetException()),
                e.getTargetException());
        } catch (Exception e) {
            throw new Exception("failed to create instance of " + classname + ", err: " + Utils.formatErr(e), e);
        }
        Plugin plugin;
        try {
            plugin = (Plugin) o;
        } catch (ClassCastException e) {
            throw new Exception(o + " cannot be cast to " + Plugin.class.getName());
        }
        return plugin;
    }
}
