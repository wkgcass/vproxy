package io.vproxy.base.component.elgroup.dummy;

import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.component.elgroup.EventLoopGroupAttach;
import io.vproxy.base.component.elgroup.EventLoopWrapper;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;

import java.io.IOException;
import java.util.List;

/**
 * Do not use this
 */
public interface IEventLoopGroup {
    List<EventLoopWrapper> list();

    List<String> names();

    EventLoopWrapper get(String alias) throws NotFoundException;

    EventLoopWrapper add(String alias) throws AlreadyExistException, IOException, ClosedException;

    EventLoopWrapper add(String alias, Annotations annotations) throws AlreadyExistException, IOException, ClosedException;

    void remove(String alias) throws NotFoundException;

    void attachResource(EventLoopGroupAttach resource) throws AlreadyExistException, ClosedException;

    void detachResource(EventLoopGroupAttach resource) throws NotFoundException;

    EventLoopWrapper next();

    EventLoopWrapper next(NetEventLoop hint);

    void close();

    String toString();
}
