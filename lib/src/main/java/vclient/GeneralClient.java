package vclient;

public interface GeneralClient {
    boolean isClosed();

    void close();

    ClientContext getClientContext();
}
