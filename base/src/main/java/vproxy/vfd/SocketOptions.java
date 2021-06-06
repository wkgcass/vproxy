package vproxy.vfd;

import java.net.SocketOption;

public class SocketOptions {
    public static final SocketOption<Boolean> IP_TRANSPARENT = new SpecialSocketOption<>("IP_TRANSPARENT", Boolean.class);

    private static class SpecialSocketOption<T> implements SocketOption<T> {
        private final String name;
        private final Class<T> type;

        SpecialSocketOption(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
