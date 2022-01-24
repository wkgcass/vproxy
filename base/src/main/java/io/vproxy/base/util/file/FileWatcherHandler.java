package io.vproxy.base.util.file;

import java.nio.file.Path;

public interface FileWatcherHandler {
    void onFileCreated(Path file, String fileContent);

    void onFileRemoved(Path file);

    void onFileUpdated(Path file, String fileContent);
}
