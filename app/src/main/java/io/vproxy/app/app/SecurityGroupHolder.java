package io.vproxy.app.app;

import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.component.secure.SecurityGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecurityGroupHolder {
    private final Map<String, SecurityGroup> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias, boolean defaultAllow) throws AlreadyExistException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("security-group", alias);
        SecurityGroup secg = new SecurityGroup(alias, defaultAllow);
        map.put(alias, secg);
    }

    public SecurityGroup get(String alias) throws NotFoundException {
        if (SecurityGroup.defaultName.equals(alias)) {
            return SecurityGroup.allowAll();
        }
        if (SecurityGroup.defaultDenyName.equals(alias)) {
            return SecurityGroup.denyAll();
        }
        SecurityGroup secg = map.get(alias);
        if (secg == null)
            throw new NotFoundException("security-group", alias);
        return secg;
    }

    public void remove(String alias) throws NotFoundException {
        SecurityGroup g = map.remove(alias);
        if (g == null)
            throw new NotFoundException("security-group", alias);
    }
}
