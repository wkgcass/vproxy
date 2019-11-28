package vfd;

import vfd.jdk.ChannelFDs;
import vfd.posix.PosixFDs;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.ServiceLoader;

public class FDProvider {
    private static final FDProvider instance = new FDProvider();

    private final FDs provided;

    private FDProvider() {
        var supported = Arrays.asList("provided", "jdk", "posix");
        var selected = VFDConfig.vfdImpl;
        if (!supported.contains(selected)) {
            selected = "provided";
        }
        if ("jdk".equals(selected)) {
            provided = ChannelFDs.get();
        } else if ("posix".equals(selected)) {
            provided = new PosixFDs();
            System.out.println("USING POSIX NATIVE FDs Impl");
        } else {
            ServiceLoader<FDs> loader = ServiceLoader.load(FDs.class);
            Optional<FDs> vChannels = loader.findFirst();
            provided = vChannels.orElse(ChannelFDs.get());
        }
    }

    public static FDProvider get() {
        return instance;
    }

    public FDs getProvided() {
        return provided;
    }

    public SocketFD openSocketFD() throws IOException {
        return provided.openSocketFD();
    }

    public ServerSocketFD openServerSocketFD() throws IOException {
        return provided.openServerSocketFD();
    }

    public DatagramFD openDatagramFD() throws IOException {
        return provided.openDatagramFD();
    }

    public FDSelector openSelector() throws IOException {
        return provided.openSelector();
    }

    public long currentTimeMillis() {
        return provided.currentTimeMillis();
    }
}
