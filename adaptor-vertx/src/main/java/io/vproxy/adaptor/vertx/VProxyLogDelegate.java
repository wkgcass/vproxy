package io.vproxy.adaptor.vertx;

import io.vertx.core.spi.logging.LogDelegate;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

public class VProxyLogDelegate implements LogDelegate {
    private final String name;

    public VProxyLogDelegate(String name) {
        this.name = name;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isDebugEnabled() {
        return Logger.debugOn();
    }

    @Override
    public boolean isTraceEnabled() {
        return Logger.debugOn();
    }

    protected LogType logType(@SuppressWarnings("unused") Object message) {
        return LogType.ALERT;
    }

    protected String msg(Object message) {
        if (message == null) {
            return "null";
        }
        if (message instanceof String) {
            return (String) message;
        }
        return message.toString();
    }

    protected String msg(Object message, Object... params) {
        return msg(message, null, params);
    }

    protected String msg(Object message, Throwable t, Object... params) {
        if (message == null) {
            return paramsOnlyMsg(null, t, params);
        }
        if (message instanceof String) {
            return formatMsg((String) message, t, params);
        }
        return paramsOnlyMsg(message, t, params);
    }

    protected String paramsOnlyMsg(Object message, Throwable t, Object[] params) {
        var sb = new StringBuilder();
        var isFirst = true;
        if (message != null) {
            sb.append(msg(message));
            isFirst = false;
        }
        for (var o : params) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(" ");
            }
            sb.append(msg(o));
        }
        if (t != null) {
            if (!isFirst) {
                sb.append(" ");
            }
            sb.append(msg(t));
        }
        return sb.toString();
    }

    protected String formatMsg(String message, Throwable t, Object[] params) {
        int i = 0;
        for (; i < params.length; ++i) {
            if (message.contains("{}")) {
                //noinspection RegExpRedundantEscape
                message = message.replaceFirst("\\{\\}", msg(params[i]));
            } else {
                break;
            }
        }
        if (i >= params.length && t == null) {
            return message;
        }
        var sb = new StringBuilder(message);
        for (; i < params.length; ++i) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(msg(params[i]));
        }
        if (t != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(t);
        }
        return sb.toString();
    }

    protected String prefix() {
        if (name == null || name.isBlank()) {
            return "";
        } else {
            return name + " - ";
        }
    }

    @Override
    public void fatal(Object message) {
        Logger.fatal(logType(message), prefix() + msg(message));
    }

    @Override
    public void fatal(Object message, Throwable t) {
        Logger.fatal(logType(message), prefix() + msg(message), t);
    }

    @Override
    public void error(Object message) {
        Logger.error(logType(message), prefix() + msg(message));
    }

    @Override
    public void error(Object message, Object... params) {
        Logger.error(logType(message), prefix() + msg(message, params));
    }

    @Override
    public void error(Object message, Throwable t) {
        Logger.error(logType(message), prefix() + msg(message), t);
    }

    @Override
    public void error(Object message, Throwable t, Object... params) {
        Logger.error(logType(message), prefix() + msg(message), t);
    }

    @Override
    public void warn(Object message) {
        Logger.warn(logType(message), prefix() + msg(message));
    }

    @Override
    public void warn(Object message, Object... params) {
        Logger.warn(logType(message), prefix() + msg(message, params));
    }

    @Override
    public void warn(Object message, Throwable t) {
        Logger.warn(logType(message), prefix() + msg(message), t);
    }

    @Override
    public void warn(Object message, Throwable t, Object... params) {
        Logger.warn(logType(message), prefix() + msg(message, params), t);
    }

    @Override
    public void info(Object message) {
        Logger.info(logType(message), prefix() + msg(message));
    }

    @Override
    public void info(Object message, Object... params) {
        Logger.info(logType(message), prefix() + msg(message, params));
    }

    @Override
    public void info(Object message, Throwable t) {
        Logger.info(logType(message), prefix() + msg(message, t));
    }

    @Override
    public void info(Object message, Throwable t, Object... params) {
        Logger.info(logType(message), prefix() + msg(message, t, params));
    }

    @Override
    public void debug(Object message) {
        assert Logger.lowLevelDebug(msg(message));
    }

    @Override
    public void debug(Object message, Object... params) {
        assert Logger.lowLevelDebug(prefix() + msg(message, params));
    }

    @Override
    public void debug(Object message, Throwable t) {
        assert Logger.lowLevelDebug(prefix() + msg(message, t));
    }

    @Override
    public void debug(Object message, Throwable t, Object... params) {
        assert Logger.lowLevelDebug(prefix() + msg(message, t, params));
    }

    @Override
    public void trace(Object message) {
        if (isTraceEnabled())
            Logger.trace(logType(message), prefix() + msg(message));
    }

    @Override
    public void trace(Object message, Object... params) {
        if (isTraceEnabled())
            Logger.trace(logType(message), prefix() + msg(message, params));
    }

    @Override
    public void trace(Object message, Throwable t) {
        if (isTraceEnabled())
            Logger.trace(logType(message), prefix() + msg(message, t));
    }

    @Override
    public void trace(Object message, Throwable t, Object... params) {
        if (isTraceEnabled())
            Logger.trace(logType(message), prefix() + msg(message, params));
    }
}
