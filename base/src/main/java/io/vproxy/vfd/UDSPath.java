package vproxy.vfd;

import java.util.Objects;

public class UDSPath extends IPPort {
    public final String path;

    public UDSPath(String path) {
        super("127.0.0.1", -1);
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        UDSPath udsPath = (UDSPath) o;
        return Objects.equals(path, udsPath.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), path);
    }

    @Override
    public String formatToIPPortString() {
        return toString();
    }

    @Override
    public String toString() {
        return "sock:" + (
            path.isBlank() ? "<unnamed>" : path
        );
    }
}
