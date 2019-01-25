package net.cassite.vproxy.redis.application;

import net.cassite.vproxy.redis.RESPHandler;
import net.cassite.vproxy.util.Callback;

import java.util.*;

@SuppressWarnings("unchecked")
public class RESPApplicationHandler implements RESPHandler<RESPApplicationContext> {
    private final RESPApplicationConfig config;
    private final RESPApplication app;

    public RESPApplicationHandler(RESPApplicationConfig config, RESPApplication app) {
        this.config = config;
        this.app = app;
    }

    @Override
    public RESPApplicationContext attachment() {
        return app.context();
    }

    // false for auth fail or not auth
    private boolean handleAuth(Object input) {
        if (config.password == null)
            return false; // password not specified
        String pass;
        if (input instanceof List) {
            List ls = (List) input;
            if (ls.size() != 2) {
                return false; // should be AUTH xxx, so size != 2 we ignore
            }
            Object arg0 = ls.get(0);
            Object arg1 = ls.get(1);
            // both should be String
            if (!(arg0 instanceof String) || !(arg1 instanceof String)) {
                return false;
            }
            if (!arg0.equals("AUTH") && !arg0.equals("auth")) {
                return false; // command is not AUTH
            }
            pass = (String) arg1;
        } else if (input instanceof String) {
            String[] inStr = ((String) input).split(" ");
            if (inStr.length != 2)
                return false; // should be AUTH xxx, so size != 2 we ignore
            if (!inStr[0].equals("AUTH") && inStr[0].equals("auth"))
                return false; // command is not AUTH
            pass = inStr[1];
        } else {
            // otherwise is not AUTH
            return false;
        }
        // it's definitely auth operation when reaches here
        byte[] hash = RESPApplicationConfig.hashCrypto.apply(pass.getBytes());
        // compare two arrays
        return Arrays.equals(hash, config.password);
    }

    // null for not PING
    private String handlePing(Object input) {
        String pongStr;
        if (input instanceof List) {
            List ls = (List) input;
            if (ls.size() == 0 || ls.size() > 2) {
                return null; // should be PING [xxx], so size == 0 or > 2 we ignore
            }
            Object arg0 = ls.get(0);
            // both should be String
            if (!(arg0 instanceof String)) {
                return null;
            }
            if (!arg0.equals("PING") && !arg0.equals("ping")) {
                return null; // command is not PING
            }
            if (ls.size() == 2) {
                if (ls.get(1) instanceof String) {
                    pongStr = (String) ls.get(1);
                } else {
                    return null; // the second object is not String, so we ignore
                }
            } else {
                pongStr = null; // pongStr is not specified
            }
        } else if (input instanceof String) {
            String[] inStr = ((String) input).split(" ");
            if (inStr.length == 0 || inStr.length > 2)
                return null; // should be PING [xxx], so size == 0 or > 2 we ignore
            if (!inStr[0].equals("PING") && !inStr[0].equals("ping"))
                return null; // command is not PING
            if (inStr.length == 2) {
                pongStr = inStr[1];
            } else {
                pongStr = null;
            }
        } else {
            // otherwise is not PING
            return null;
        }
        // it's definitely ping operation when reaches here
        if (pongStr == null) {
            return "PONG";
        } else {
            return pongStr;
        }
    }

    // null for not COMMAND or COMMAND related ops
    private Object handleCmd(Object input) {
        boolean isReturnNum;
        List<String> requestedCmds;

        if (input instanceof List) {
            List inList = (List) input;
            if (inList.isEmpty()) {
                return null; // not command
            } else if (inList.size() == 1) {
                if (!(inList.get(0) instanceof String))
                    return null; // not string
                String arg0 = (String) inList.get(0);
                if (!arg0.equals("COMMAND") && !arg0.equals("command")) {
                    return null; // not command
                } else {
                    isReturnNum = false;
                    requestedCmds = null;
                }
            } else if (inList.size() == 2) {
                if (!(inList.get(0) instanceof String))
                    return null; // not string
                if (!(inList.get(1) instanceof String))
                    return null; // not string
                String arg0 = (String) inList.get(0);
                String arg1 = (String) inList.get(1);
                if ((!arg0.equals("COMMAND") && !arg0.equals("command")) ||
                    (!arg1.equals("COUNT") && !arg1.equals("count"))
                ) {
                    return null; // not command
                } else {
                    isReturnNum = true;
                    requestedCmds = null;
                }
            } else {
                if (!(inList.get(0) instanceof String))
                    return null; // not string
                if (!(inList.get(1) instanceof String))
                    return null; // not string
                String arg0 = (String) inList.get(0);
                String arg1 = (String) inList.get(1);
                if ((!arg0.equals("COMMAND") && !arg0.equals("command")) ||
                    (!arg1.equals("INFO") && !arg1.equals("info"))
                ) {
                    return null; // not command
                } else {
                    isReturnNum = false;
                    requestedCmds = new LinkedList<>(((List) input).subList(2, ((List) input).size()));
                }
            }
        } else if (input instanceof String) {
            String[] inList = ((String) input).split(" ");
            if (inList.length == 0) {
                return null; // not command
            } else if (inList.length == 1) {
                if (!inList[0].equals("COMMAND") && !inList[0].equals("command")) {
                    return null; // not command
                } else {
                    isReturnNum = false;
                    requestedCmds = null;
                }
            } else if (inList.length == 2) {
                if ((!inList[0].equals("COMMAND") && !inList[0].equals("command")) ||
                    (!inList[1].equals("COUNT") && !inList[1].equals("count"))
                ) {
                    return null; // not command count
                } else {
                    isReturnNum = true;
                    requestedCmds = null;
                }
            } else {
                if ((!inList[0].equals("COMMAND") && !inList[0].equals("command")) ||
                    (!inList[1].equals("INFO") && !inList[1].equals("info"))
                ) {
                    return null; // not command info
                } else {
                    isReturnNum = false;
                    requestedCmds = new LinkedList<>(Arrays.asList(inList).subList(2, inList.length));
                }
            }
        } else {
            return null;
        }

        // is command related commands when reach here

        List<RESPCommand> commands = app.commands();
        if (commands == null)
            commands = Collections.emptyList(); // the user did not provide
        if (isReturnNum) {
            return commands.size(); // return for COMMAND COUNT
        }
        if (requestedCmds == null) {
            List<List> ls = new LinkedList<>();
            for (RESPCommand c : commands) {
                ls.add(c.toList());
            }
            return ls; // return for COMMAND
        }
        Map<String, RESPCommand> map = new HashMap<>();
        for (RESPCommand c : commands) {
            map.put(c.name.toLowerCase(), c);
            map.put(c.name.toUpperCase(), c);
        }
        List<List> ls = new LinkedList<>();
        for (String s : requestedCmds) {
            if (map.containsKey(s)) {
                ls.add(map.get(s).toList());
            } else {
                ls.add(null);
            }
        }
        return ls; // return for COMMAND INFO
    }

    @Override
    public void handle(Object input, RESPApplicationContext ctx, Callback<Object, Throwable> cb) {
        if (handleAuth(input)) {
            ctx.noAuth = false;
        } else {
            // not auth, or auth failed
            if (ctx.noAuth) {
                cb.failed(new Exception("NOAUTH"));
                return;
            }
        }

        // now, the connection is auth-ed

        // try handle PING
        String str = handlePing(input);
        if (str != null) {
            cb.succeeded(str);
            return;
        }

        // try handle COMMAND
        // we try to handle COMMAND because the redis-cli
        // will always try to retrieve the command list
        // on starting
        // if user code doesn't provide the list,
        // we return our own
        Object cmdRes = handleCmd(input);
        if (cmdRes != null) {
            cb.succeeded(cmdRes);
            return;
        }

        // run into user code
        app.handle(input, ctx, cb);
    }
}
