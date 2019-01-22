package net.cassite.vproxy.app.cmd;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.cmd.handle.resource.*;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class Command {
    public Action action;
    public Resource resource;
    public Preposition preposition;
    public Resource prepositionResource;
    public final List<Flag> flags = new LinkedList<>();
    public final Map<Param, String> args = new HashMap<>();

    public static String helpString() {
        return "" +
            "vproxy:" +
            "\n    System commands:" +
            "\n        help                 | h                   show this message" +
            "\n        System call: help                          show this message" +
            "\n        System call: shutdown                      shutdown the vproxy process" +
            "\n        System call: load                          load config commands from a file" +
            "\n    Operate a resource:" +
            "\n        list                 | l                   list resources' names" +
            "\n        list-detail          | L                   list detailed info about resources" +
            "\n        add                  | a                   create a resource" +
            "\n        add . to .           | a . to .            add a resource into another one" +
            "\b        update               | u                   update a resource" +
            "\n        remove               | r                   remove and release a resource" +
            "\n        force-remove         | R                   force remove and release a resource" +
            "\n        remove . from .      | r . from .          detach a resource from another one, the detached resource is not released" +
            "\n    How to represent a resource:" +
            "\n        for top level resources: ${resource-type} ${resource-name}" +
            "\n        for sub level resources: ${resource-type} ${resource-name} in ${resource-type} ${resource-name} in ..." +
            "\n        Available resource types:" +
            "\n            tcp-lb           | tl                  tcp loadbalancer" +
            "\n            event-loop-group | elg                 event loop group" +
            "\n            server-groups    | sgs                 server groups" +
            "\n            server-group     | sg                  server group" +
            "\n            event-loop       | el                  event loop, is inside event loop group" +
            "\n            server           | svr                 server, is inside server group" +
            "\n            bind-server      | bs                  bind server record (socket that is listening), is inside event loop and loadbalancer" +
            "\n            connection       | conn                connection record, is inside event loop" +
            "\n            session          | sess                a proxy session, is inside loadbalancer" +
            "\n    Parameters:" +
            "\n        timeout                                    health check timeout     , required when (creating|updating server group) or (updating server group health check)" +
            "\n        period                                     health check period      , required when (creating|updating server group) or (updating server group health check)" +
            "\n        up                                         health check up times    , required when (creating|updating server group) or (updating server group health check)" +
            "\n        down                                       health check down times  , required when (creating|updating server group) or (updating server group health check)" +
            "\n        method               | meth                method to retrieve       , required when (creating|updating server group)" +
            "\n        weight               | w                   weight                   , required when (adding|updating server in server group)" +
            "\n        event-loop-group     | elg                 event loop group         , required when (creating server group) or (creating tcp-lb as the worker group)" +
            "\n        acceptor-elg         | aelg                acceptor event loop group, required when (creating tcp-lb)" +
            "\n        address              | addr                ip address or ip:port    , required when (creating tcp-lb) or (adding server into server group)" +
            "\n        ip                                         ip address               , required when (adding server into server group as the local ip)" +
            "\n        server-groups        | sgs                 server groups            , required when (creating tcp-lb)" +
            "\n        in-buffer-size                             in buffer size           , required when (creating tcp-lb)" +
            "\n        out-buffer-size                            out buffer size          , required when (creating tcp-lb)" +
            "\n    Usages:" +
            "\n        add event-loop-group elg0                  // creates a new event loop group named elg0" +
            "\n        add event-loop el00 to elg0                // creates a new event loop named el00 in elg0" +
            "\n        add server-group g0 timeout 500 period 800 up 4 down 5 method wrr elg elg0     // creates a server group named g0 with these arguments" +
            "\n        list-detail tcp-lb                         // list detailed info about all tcp lbs" +
            ""
            ;
    }

    public static Command parseStrCmd(String line) throws Exception {
        List<String> cmd = Arrays.asList(line.trim().split(" "));
        return parseStrCmd(cmd);
    }

    public static Command parseStrCmd(List<String> _cmd) throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (String c : _cmd) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(" ");
            }
            sb.append(c);
        }
        Logger.info(LogType.BEFORE_PARSING_CMD, sb.toString());

        _cmd = _cmd.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        Command cmd = new Command();

        int state;
        // 0 --> initial state, expecting Action (->1)
        // 1 --> action found, expecting resource type (->2)
        // 2 --> resource type found, expecting resource name (->3) or `in`(->10) or end
        // 3 --> resource name found, expecting preposition(->4) or flags(->7) or params(->8) or `in`(->10) or end
        // 4 --> preposition found, expecting resource type(-> 5)
        // 5 --> resource type found after preposition, expecting resource name(->6)
        // 6 --> resource name found after preposition, expecting flags(->7) or params(->8) or `in`(->13) or end
        // 7 --> flags found, expecting flags(->7) or params(->8) or end
        // 8 --> params found, expecting value (->9)
        // 9 --> value found, expecting flags(->7) or params(->8) or end
        // 10 --> `in` found, expecting resource type (->11)
        // 11 --> resource type found after in, expecting resource name (->12)
        // 12 --> resource name found after in, expecting preposition(->4) or flags(->7) or params(->8) or `in`(->10) or end
        // 13 --> `in` found after preposition, expecting resource type (->14)
        // 14 --> resource type found after preposition and in, expecting resource name (->15)
        // 15 --> resource name found after preposition and in, expecting flags(->7) or params(->8) or `in`(->13) or end

        Resource lastResource = null;
        Param lastParam = null;
        // --------------------------------------------------------------
        // implementation should follow the state machine commented above
        // --------------------------------------------------------------

        state = 1;
        loop:
        for (int i = 0; i < _cmd.size(); i++) {
            String c = _cmd.get(i);
            String next = (_cmd.size() - 1 == i) ? null : _cmd.get(i + 1);
            switch (state) {
                case 1:
                    try {
                        cmd.action = getEnum(c, Action.class);
                    } catch (IllegalArgumentException e) {
                        throw new Exception(c + " is not a valid action");
                    }
                    state = 2;
                    notEnd(next);
                    break;
                case 2:
                    lastResource = new Resource();
                    cmd.resource = lastResource;
                    try {
                        lastResource.type = getEnum(c, ResourceType.class);
                    } catch (IllegalArgumentException e) {
                        throw new Exception(c + " is not a valid resource type");
                    }
                    if (next == null) { // end
                        break loop;
                    }
                    if (next.equals("in")) {
                        state = 10;
                        break;
                    } else {
                        state = 3;
                        break;
                    }
                case 3:
                    lastResource.alias = notKeyWordAndValid(c);
                    if (next == null) { // end
                        break loop;
                    }
                    if (isEnumMatch(next, Preposition.class)) {
                        state = 4;
                        break;
                    }
                    if (isEnumMatch(next, Flag.class)) {
                        state = 7;
                        break;
                    }
                    if (isEnumMatch(next, Param.class)) {
                        state = 8;
                        break;
                    }
                    if (next.equals("in")) {
                        state = 10;
                        break;
                    }
                    throw new Exception("invalid syntax near " + c + " " + next);
                case 4:
                    try {
                        cmd.preposition = getEnum(c, Preposition.class);
                    } catch (IllegalArgumentException e) {
                        throw new Exception(c + " is not a valid preposition");
                    }
                    state = 5;
                    notEnd(next);
                    break;
                case 5:
                    lastResource = new Resource();
                    cmd.prepositionResource = lastResource;
                    try {
                        lastResource.type = getEnum(c, ResourceType.class);
                    } catch (IllegalArgumentException e) {
                        throw new Exception(c + " is not a valid resource type");
                    }
                    state = 6;
                    notEnd(next);
                    break;
                case 6:
                    lastResource.alias = notKeyWordAndValid(c);
                    if (next == null) { // end
                        break loop;
                    }
                    if (isEnumMatch(next, Flag.class)) {
                        state = 7;
                        break;
                    }
                    if (isEnumMatch(next, Param.class)) {
                        state = 8;
                        break;
                    }
                    if (next.equals("in")) {
                        state = 13;
                        break;
                    }
                    throw new Exception("invalid syntax near " + c + " " + next);
                case 7:
                    try {
                        cmd.flags.add(getEnum(c, Flag.class));
                    } catch (IllegalArgumentException e) {
                        throw new Exception(c + " is not a valid flag");
                    }
                    if (next == null) {
                        break loop;
                    }
                    if (isEnumMatch(next, Flag.class)) {
                        //noinspection ConstantConditions
                        state = 7;
                        break;
                    }
                    if (isEnumMatch(next, Param.class)) {
                        state = 8;
                        break;
                    }
                    throw new Exception("invalid syntax near " + c + " " + next);
                case 8:
                    try {
                        lastParam = getEnum(c, Param.class);
                    } catch (Exception e) {
                        throw new Exception(c + " is not a valid param");
                    }
                    state = 9;
                    notEnd(next);
                    break;
                case 9:
                    String v = notKeyWordAndValid(c);
                    cmd.args.put(lastParam, v);
                    if (next == null) {
                        break loop;
                    }
                    if (isEnumMatch(next, Flag.class)) {
                        //noinspection ConstantConditions
                        state = 7;
                        break;
                    }
                    if (isEnumMatch(next, Param.class)) {
                        state = 8;
                        break;
                    }
                    throw new Exception("invalid syntax near " + c + " " + next);
                case 10:
                    state = 11;
                    notEnd(next);
                    break;
                case 11:
                    lastResource.parentResource = new Resource();
                    lastResource = lastResource.parentResource;
                    try {
                        lastResource.type = getEnum(c, ResourceType.class);
                    } catch (IllegalArgumentException e) {
                        throw new Exception(c + " is not a valid resource type");
                    }
                    state = 12;
                    notEnd(next);
                    break;
                case 12:
                    lastResource.alias = notKeyWordAndValid(c);
                    if (next == null) {
                        break loop;
                    }
                    if (isEnumMatch(next, Preposition.class)) {
                        state = 4;
                        break;
                    }
                    if (isEnumMatch(next, Flag.class)) {
                        //noinspection ConstantConditions
                        state = 7;
                        break;
                    }
                    if (isEnumMatch(next, Param.class)) {
                        state = 8;
                        break;
                    }
                    if (next.equals("in")) {
                        state = 10;
                        break;
                    }
                    throw new Exception("invalid syntax near " + c + " " + next);
                case 13:
                    state = 14;
                    notEnd(next);
                    break;
                case 14:
                    lastResource.parentResource = new Resource();
                    lastResource = lastResource.parentResource;
                    try {
                        lastResource.type = getEnum(c, ResourceType.class);
                    } catch (IllegalArgumentException e) {
                        throw new Exception(c + " is not a valid resource type");
                    }
                    state = 15;
                    notEnd(next);
                    break;
                case 15:
                    lastResource.alias = notKeyWordAndValid(c);
                    if (next == null) { // end
                        break loop;
                    }
                    if (isEnumMatch(next, Flag.class)) {
                        state = 7;
                        break;
                    }
                    if (isEnumMatch(next, Param.class)) {
                        state = 8;
                        break;
                    }
                    if (next.equals("in")) {
                        state = 13;
                        break;
                    }
                    throw new Exception("invalid syntax near " + c + " " + next);
            }
        }
        if (state != 2 && state != 3 && state != 6 && state != 7 && state != 9 && state != 12 && state != 15) {
            throw new Exception("the parser has a bug and did not detect invalidity of this input");
        }

        semantic(cmd);
        Logger.info(LogType.AFTER_PARSING_CMD, cmd.toString());
        return cmd;
    }

    private static String notKeyWordAndValid(String c) throws Exception {
        if (c.equals("in")
            || isEnumMatch(c, Action.class)
            || isEnumMatch(c, Flag.class)
            || isEnumMatch(c, Param.class)
            || isEnumMatch(c, Preposition.class)
            || isEnumMatch(c, ResourceType.class))
            throw new Exception(c + " is a key word");
        if (c.contains(" ") || c.contains("\t"))
            throw new Exception(c + " is invalid");
        return c;
    }

    private static void notEnd(String next) throws Exception {
        if (next == null)
            throw new Exception("unexpected end");
    }

    private static <T extends Enum<T>> boolean isEnumMatch(String c, Class<T> cls) throws Exception {
        try {
            getEnum(c, cls);
            return true;
        } catch (IllegalArgumentException ignore) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T getEnum(String c, Class<T> cls) throws Exception {
        for (Enum e : cls.getEnumConstants()) {
            if (e.name().equals(c))
                return (T) e;
        }
        try {
            Field f = cls.getField("fullname");
            for (Enum e : cls.getEnumConstants()) {
                if (f.get(e).equals(c))
                    return (T) e;
            }
        } catch (NoSuchFieldException ignore) {
        }
        throw new IllegalArgumentException();
    }

    private static void semantic(Command cmd) throws Exception {
        if (cmd.action == Action.l || cmd.action == Action.L) {
            // for list operations, to/from are not allowed
            if (cmd.preposition != null) {
                throw new Exception("cannot specify preposition when action is " + Action.l.fullname + " or " + Action.L.fullname);
            }
            // and obviously you cannot specify a name when retrieving a list
            if (cmd.resource.alias != null) {
                throw new Exception("cannot specify resource name when " + cmd.action.fullname + "(-ing) the resource");
            }
        } else {
            // for non list operations, i.e. modification operations
            // the name to operate is required
            if (cmd.resource.alias == null) {
                throw new Exception("resource name not specified when " + cmd.action.fullname + "(-ing) the resource");
            }
            if (cmd.preposition != null) {
                // for add operations, preposition should be `to`
                // for remove operations, preposition should be `from`
                if ((cmd.action == Action.a && cmd.preposition != Preposition.to)
                    ||
                    ((cmd.action == Action.r || cmd.action == Action.R) && cmd.preposition != Preposition.from)
                )
                    throw new Exception("cannot " + cmd.action.fullname + "(-ing) " + cmd.resource.type.fullname + " [" + cmd.preposition + "] " + cmd.prepositionResource.type.fullname);
            }
            // for add/remove operations, the resource should not have a parent resource
            // the parent resource should be specified in preposition if required
            if (cmd.action == Action.a || cmd.action == Action.r || cmd.action == Action.R) {
                if (cmd.resource.parentResource != null) {
                    throw new Exception("cannot specify parent resource when " + cmd.action.fullname + "(-ing) " + cmd.resource.type.fullname);
                }
            }
            // for update operations, the preposition should not exist
            // the parent resource may exist
            if (cmd.action == Action.u) {
                if (cmd.prepositionResource != null) {
                    throw new Exception("cannot use " + cmd.preposition + " when " + cmd.action.fullname + "(-ing) " + cmd.resource.type.fullname);
                }
            }
        }
        Resource targetResource = cmd.resource.parentResource;
        if (targetResource == null)
            targetResource = cmd.prepositionResource;
        switch (cmd.resource.type) {
            case bs: // bindServer
            case conn: // connection
            case sess: // session
                // these three resources are related to a channel
                // the handles are similar
                // so put them together
                // though there're still some logic branches
                switch (cmd.action) {
                    case a:
                    case r:
                    case R:
                        // modification not supported for these resources
                        throw new Exception("cannot run " + cmd.action.fullname + " on " + cmd.resource.type.fullname);
                    case L:
                    case l:
                        // should have a parent resource
                        if (targetResource == null)
                            throw new Exception("cannot find " + cmd.resource.type.fullname + " on top level");

                        if ((cmd.resource.type == ResourceType.sess || cmd.resource.type == ResourceType.conn)
                            && targetResource.type == ResourceType.tl) {

                            // sess can only be found in tcp lb
                            TcpLBHandle.checkTcpLB(targetResource);

                        } else if (cmd.resource.type != ResourceType.sess
                            && targetResource.type == ResourceType.el) {

                            // bs and conn can be found in event loop
                            EventLoopHandle.checkEventLoop(targetResource);

                        } else {
                            throw new Exception(targetResource.type.fullname + " does not contain " + cmd.resource.type.fullname);
                        }
                        break;
                    default:
                        throw new Exception("unsupported action " + cmd.action.fullname + " for " + cmd.resource.type.fullname);
                }
                break;
            case el: // event loop
                switch (cmd.action) {
                    case a:
                    case r:
                    case R:
                    case L:
                    case l:
                        // event loop should be found in event loop group
                        if (targetResource == null)
                            throw new Exception("cannot find " + cmd.resource.type.fullname + " on top level");
                        if (targetResource.type != ResourceType.elg)
                            throw new Exception(targetResource.type.fullname + " does not contain " + cmd.resource.type.fullname);
                        // also should check event loop group
                        EventLoopGroupHandle.checkEventLoopGroup(targetResource);
                        // no need to check for creation
                        // no param required
                        break;
                    default:
                        throw new Exception("unsupported action " + cmd.action.fullname + " for " + cmd.resource.type.fullname);
                }
                break;
            case svr: // server
                switch (cmd.action) {
                    case a:
                    case r:
                    case R:
                    case L:
                    case l:
                        // server should be found in server group
                        if (targetResource == null)
                            throw new Exception("cannot find " + cmd.resource.type.fullname + " on top level");
                        if (targetResource.type != ResourceType.sg)
                            throw new Exception(targetResource.type.fullname + " does not contain " + cmd.resource.type.fullname);
                        // also should check server group
                        ServerGroupHandle.checkServerGroup(targetResource);
                        // check for creation
                        if (cmd.action == Action.a) {
                            ServerHandle.checkCreateServer(cmd);
                        }
                        break;
                    case u:
                        if (targetResource == null)
                            throw new Exception("cannot find " + cmd.resource.type.fullname + " on top level");
                        ServerHandle.checkUpdateServer(cmd);
                    default:
                        throw new Exception("unsupported action " + cmd.action.fullname + " for " + cmd.resource.type.fullname);
                }
                break;
            case sg: // server group
                switch (cmd.action) {
                    case a:
                    case r:
                    case R:
                    case L:
                    case l:
                        // server group is on top level or in serverGroups
                        if (targetResource != null) {
                            if (targetResource.type != ResourceType.sgs)
                                throw new Exception(targetResource.type.fullname + " does not contain " + cmd.resource.type.fullname);
                            // also should check serverGroups
                            ServerGroupsHandle.checkServerGroups(targetResource);
                            // no need to check for attaching,
                            // the attach process has no param
                        } else {
                            // only check creation when on top level
                            if (cmd.action == Action.a) {
                                ServerGroupHandle.checkCreateServerGroup(cmd);
                            }
                        }
                        break;
                    case u:
                        // can only update the server group on top level
                        // i'm not saying that you cannot modify the one in serverGroups
                        // but you don't have to go into serverGroups to modify,
                        // the one on top level is the same one in any serverGroup
                        if (targetResource != null)
                            throw new Exception("you should update " + cmd.resource.type + " on top level");
                        ServerGroupHandle.checkUpdateServerGroup(cmd);
                        break;
                    default:
                        throw new Exception("unsupported action " + cmd.action.fullname + " for " + cmd.resource.type.fullname);
                }
                break;
            case sgs: // server groups
            case tl: // tcp lb
            case elg: // event loog group
                // these three are only exist on top level
                // so bring them together
                switch (cmd.action) {
                    case a:
                    case r:
                    case R:
                    case L:
                    case l:
                        if (targetResource != null)
                            throw new Exception(cmd.resource.type.fullname + " is on top level");
                        // only check creation for tcp lb
                        // the other two does not have creation param
                        if (cmd.action == Action.a) {
                            if (cmd.resource.type == ResourceType.tl) {
                                TcpLBHandle.checkCreateTcpLB(cmd);
                            } // the other two does not need check
                        }
                        break;
                    default:
                        throw new Exception("unsupported action " + cmd.action.fullname + " for " + cmd.resource.type.fullname);
                }
                break;
            default:
                throw new Exception("unknown resource type " + cmd.resource.type.fullname);
        }
    }

    public void run(Callback<String, Throwable> cb) {
        Application.get()._controlEventLoop.nextTick(() -> {
            String res;
            try {
                res = runTry();
            } catch (Throwable t) {
                cb.failed(t);
                return;
            }
            cb.succeeded(res);
        });
    }

    private static String utilJoinList(List<?> ls) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Object o : ls) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append("\n");
            }
            sb.append(o);
        }
        return sb.toString();
    }

    private String runTry() throws Exception {
        Resource targetResource = resource.parentResource == null ? prepositionResource : resource.parentResource;
        switch (resource.type) {
            case conn: // can be retrieved from tl or el
                switch (action) {
                    case l:
                        return "" + ConnectionHandle.count(targetResource);
                    case L:
                        return utilJoinList(ConnectionHandle.list(targetResource));
                }
            case sess: // can only be retrieve from tl
                switch (action) {
                    case l:
                        return "" + SessionHandle.count(targetResource);
                    case L:
                        return utilJoinList(SessionHandle.list(targetResource));
                }
            case bs: // can only be retrieved from el
                switch (action) {
                    case l:
                        return "" + BindServerHandle.count(targetResource);
                    case L:
                        return utilJoinList(BindServerHandle.list(targetResource));
                }
            case svr: // can only be retrieved from server group
                switch (action) {
                    case l:
                        return utilJoinList(ServerHandle.names(targetResource));
                    case L:
                        return utilJoinList(ServerHandle.detail(targetResource));
                    case a:
                        ServerHandle.add(this);
                        return "";
                    case r:
                    case R:
                        ServerHandle.forceRemove(this);
                        return "";
                    case u:
                        ServerHandle.update(this);
                        return "";
                }
            case sgs: // top level
                switch (action) {
                    case l:
                    case L:
                        return utilJoinList(ServerGroupsHandle.names());
                    case a:
                        ServerGroupsHandle.add(this);
                        return "";
                    case r:
                        ServerGroupsHandle.preCheck(this);
                    case R:
                        ServerGroupsHandle.forceRemove(this);
                        return "";
                }
            case elg: // top level
                switch (action) {
                    case l:
                    case L:
                        return utilJoinList(EventLoopGroupHandle.names());
                    case a:
                        EventLoopGroupHandle.add(this);
                        return "";
                    case r:
                        EventLoopGroupHandle.preCheck(this);
                    case R:
                        EventLoopGroupHandle.forceRemvoe(this);
                        return "";
                }
            case el: // can only be retrieved from event loop group
                switch (action) {
                    case l:
                    case L:
                        return utilJoinList(EventLoopHandle.names(targetResource));
                    case a:
                        EventLoopHandle.add(this);
                        return "";
                    case r:
                    case R:
                        EventLoopHandle.forceRemove(this);
                        return "";
                }
            case sg: // top level or retrieved from serverGroups
                switch (action) {
                    case l:
                    case L:
                        return utilJoinList(ServerGroupHandle.names(targetResource));
                    case a:
                        ServerGroupHandle.add(this);
                        return "";
                    case r:
                        ServerGroupHandle.preCheck(this);
                    case R:
                        ServerGroupHandle.forceRemove(this);
                        return "";
                    case u:
                        ServerGroupHandle.update(this);
                        return "";
                }
            case tl: // top level
                switch (action) {
                    case l:
                        return utilJoinList(TcpLBHandle.names());
                    case L:
                        return utilJoinList(TcpLBHandle.details());
                    case a:
                        TcpLBHandle.add(this);
                        return "";
                    case r:
                    case R:
                        TcpLBHandle.forceRemove(this);
                }
                throw new Exception("cannot run " + action.fullname + " on " + resource.type.fullname);
            default:
                throw new Exception("unknown resource type " + resource.type.fullname);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(action.fullname).append(" ").append(resource);
        if (preposition != null) {
            sb.append(" ").append(preposition).append(" ").append(prepositionResource);
        }
        for (Flag f : flags) {
            sb.append(" ").append(f.fullname);
        }
        for (Param p : args.keySet()) {
            String v = args.get(p);
            sb.append(" ").append(p.fullname).append(" ").append(v);
        }
        return sb.toString();
    }
}
