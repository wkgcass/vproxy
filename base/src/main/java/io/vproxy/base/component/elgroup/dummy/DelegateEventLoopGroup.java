package vproxy.base.component.elgroup.dummy;

import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.elgroup.EventLoopGroupAttach;
import vproxy.base.component.elgroup.EventLoopWrapper;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.util.Annotations;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.ClosedException;
import vproxy.base.util.exception.NotFoundException;

import java.io.IOException;
import java.util.List;

/**
 * Do not use this
 */
public class DelegateEventLoopGroup extends EventLoopGroup implements IEventLoopGroup {
    private final String newAlias;
    private final EventLoopGroup elg;

    public DelegateEventLoopGroup(String newAlias, EventLoopGroup elg) {
        super(newAlias, elg.annotations);
        this.newAlias = newAlias;
        this.elg = elg;
    }

    @Override
    public List<EventLoopWrapper> list() {
        return elg.list();
    }

    @Override
    public List<String> names() {
        return elg.names();
    }

    @Override
    public EventLoopWrapper get(String alias) throws NotFoundException {
        return elg.get(alias);
    }

    @Override
    public synchronized EventLoopWrapper add(String alias) throws AlreadyExistException, IOException, ClosedException {
        return elg.add(alias);
    }

    @Override
    public synchronized EventLoopWrapper add(String alias, Annotations annotations) throws AlreadyExistException, IOException, ClosedException {
        return elg.add(alias, annotations);
    }

    @Override
    public synchronized void remove(String alias) throws NotFoundException {
        elg.remove(alias);
    }

    @Override
    public void attachResource(EventLoopGroupAttach resource) throws AlreadyExistException, ClosedException {
        elg.attachResource(resource);
    }

    @Override
    public void detachResource(EventLoopGroupAttach resource) throws NotFoundException {
        elg.detachResource(resource);
    }

    @Override
    public EventLoopWrapper next() {
        return elg.next();
    }

    @Override
    public EventLoopWrapper next(NetEventLoop hint) {
        return elg.next(hint);
    }

    @Override
    public synchronized void close() {
        elg.close();
    }

    @Override
    public String toString() {
        return newAlias + " -> annotations " + annotations.toString();
    }
}
