package vproxy.base.component.elgroup.dummy;

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
public interface IEventLoopGroup {
    List<EventLoopWrapper> list();

    List<String> names();

    EventLoopWrapper get(String alias) throws NotFoundException;

    void add(String alias) throws AlreadyExistException, IOException, ClosedException;

    void add(String alias, Annotations annotations) throws AlreadyExistException, IOException, ClosedException;

    void remove(String alias) throws NotFoundException;

    void attachResource(EventLoopGroupAttach resource) throws AlreadyExistException, ClosedException;

    void detachResource(EventLoopGroupAttach resource) throws NotFoundException;

    EventLoopWrapper next();

    EventLoopWrapper next(NetEventLoop hint);

    void close();

    String toString();
}
