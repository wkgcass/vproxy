package net.cassite.vproxy.component.svrgroup;

import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerGroups {
    public final String alias;
    private List<ServerGroup> serverGroups = new ArrayList<>(0);
    private AtomicInteger cursor = new AtomicInteger(0);

    public ServerGroups(String alias) {
        this.alias = alias;
    }

    public void add(ServerGroup group) throws AlreadyExistException {
        List<ServerGroup> groups = serverGroups;
        if (groups.contains(group))
            throw new AlreadyExistException();
        List<ServerGroup> newLs = new ArrayList<>(groups.size() + 1);
        newLs.addAll(groups);
        newLs.add(group);
        serverGroups = newLs;
    }

    public synchronized void remove(ServerGroup group) throws NotFoundException {
        List<ServerGroup> groups = serverGroups;
        if (groups.isEmpty())
            throw new NotFoundException();
        boolean found = false;
        List<ServerGroup> newLs = new ArrayList<>(groups.size() - 1);
        for (ServerGroup g : groups) {
            if (g.equals(group)) {
                found = true;
            } else {
                newLs.add(g);
            }
        }
        if (!found) {
            throw new NotFoundException();
        }
        serverGroups = newLs;
    }

    public List<ServerGroup> getServerGroups() {
        return new ArrayList<>(serverGroups);
    }

    public Connector next() {
        List<ServerGroup> groups = serverGroups;
        return next(groups, 0);
    }

    private Connector next(List<ServerGroup> groups, int recursion) {
        if (recursion > groups.size())
            return null;
        ++recursion;

        int idx = cursor.getAndIncrement();
        if (groups.size() > idx) {
            Connector connector = groups.get(idx).next();
            if (connector != null)
                return connector;
        } else {
            cursor.set(0);
        }
        return next(groups, recursion);
    }
}
