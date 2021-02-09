package vproxybase.processor.httpbin.frame;

public interface WithPriority {
    boolean priority();

    int streamDependency();

    int weight();

    void unsetPriority();
}
