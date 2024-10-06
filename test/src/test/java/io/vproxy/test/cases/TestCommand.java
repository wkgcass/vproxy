package io.vproxy.test.cases;

import io.vproxy.app.app.cmd.Action;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.ResourceType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestCommand {
    @Test
    public void simple() throws Exception {
        var cmd = Command.parseStrCmd("ll tcp-lb");
        assertEquals(Action.ll, cmd.action);
        assertEquals(ResourceType.tl, cmd.resource.type);
    }

    @Test
    public void simpleAdd() throws Exception {
        var cmd = Command.parseStrCmd("add svr s0 to sg sg0 address 10.1.1.1:8080 weight 10");
        assertEquals(Action.add, cmd.action);
        assertEquals(ResourceType.svr, cmd.resource.type);
        assertEquals("s0", cmd.resource.alias);
        assertEquals(2, cmd.args.size());
        assertEquals("10.1.1.1:8080", cmd.args.get(Param.addr));
        assertEquals("10", cmd.args.get(Param.weight));
    }

    @Test
    public void stringQuotes() throws Exception {
        var cmd = Command.parseStrCmd("add svr s0 to sg sg0 address \"10.1.1.1:8080\" weight '10'");
        assertEquals(Action.add, cmd.action);
        assertEquals(ResourceType.svr, cmd.resource.type);
        assertEquals("s0", cmd.resource.alias);
        assertEquals(2, cmd.args.size());
        assertEquals("10.1.1.1:8080", cmd.args.get(Param.addr));
        assertEquals("10", cmd.args.get(Param.weight));
    }

    @Test
    public void jsonObject() throws Exception {
        var cmd = Command.parseStrCmd("add sw sw0 anno { a: \"b\", 'c': d }");
        assertEquals(Action.add, cmd.action);
        assertEquals(ResourceType.sw, cmd.resource.type);
        assertEquals("sw0", cmd.resource.alias);
        assertEquals(1, cmd.args.size());
        assertEquals("{\"a\":\"b\",\"c\":\"d\"}", cmd.args.get(Param.anno));
    }

    @Test
    public void spaceInParams() throws Exception {
        var cmd = Command.parseStrCmd("add sw sw0 address 'hello world space'");
        assertEquals(Action.add, cmd.action);
        assertEquals(ResourceType.sw, cmd.resource.type);
        assertEquals("sw0", cmd.resource.alias);
        assertEquals(1, cmd.args.size());
        assertEquals("hello world space", cmd.args.get(Param.addr));
    }

    @Test
    public void stringConcatByQuotes() throws Exception {
        var cmd = Command.parseStrCmd("add ck ck0 cert 'abc','def',ghi key jkl");
        assertEquals(Action.add, cmd.action);
        assertEquals(ResourceType.ck, cmd.resource.type);
        assertEquals("ck0", cmd.resource.alias);
        assertEquals(2, cmd.args.size());
        assertEquals("abc,def,ghi", cmd.args.get(Param.cert));
        assertEquals("jkl", cmd.args.get(Param.key));
    }

    @Test
    public void stringConcatByQuotes2() throws Exception {
        var cmd = Command.parseStrCmd("add ck ck0 cert abc,'def','ghi'");
        assertEquals("abc,def,ghi", cmd.args.get(Param.cert));

        cmd = Command.parseStrCmd("add ck ck0 cert 'abc',def,'ghi'");
        assertEquals("abc,def,ghi", cmd.args.get(Param.cert));

        cmd = Command.parseStrCmd("add ck ck0 cert abc,\"def\",'ghi'");
        assertEquals("abc,def,ghi", cmd.args.get(Param.cert));

        cmd = Command.parseStrCmd("add ck ck0 cert abc,def,'ghi'");
        assertEquals("abc,def,ghi", cmd.args.get(Param.cert));

        cmd = Command.parseStrCmd("add ck ck0 cert abc,def,\"ghi\"");
        assertEquals("abc,def,ghi", cmd.args.get(Param.cert));
    }
}
