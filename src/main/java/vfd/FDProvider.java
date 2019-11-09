package vfd;

import vfd.jdk.ChannelFDs;

import java.io.IOException;
import java.util.Optional;
import java.util.ServiceLoader;

public class FDProvider {
    private static final FDProvider instance = new FDProvider();

    private final FDs provided;

    private FDProvider() {
        ServiceLoader<FDs> loader = ServiceLoader.load(FDs.class);
        Optional<FDs> vChannels = loader.findFirst();
        provided = vChannels.orElse(new ChannelFDs());
    }

    public static FDProvider get() {
        return instance;
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
}
