package io.vproxy.base.util.file;

import io.vproxy.base.selector.PeriodicEvent;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileWatcher {
    private static final SelectorEventLoop watcherLoop0;

    static {
        SelectorEventLoop loop;
        try {
            loop = SelectorEventLoop.open();
        } catch (IOException e) {
            throw new Error(e);
        }
        loop.loop(r -> VProxyThread.create(r, "file-watcher-loop"));
        watcherLoop0 = loop;
    }

    public final Path file;
    public final FileWatcherHandler handler;
    private boolean exists = false;
    private long lastModifiedTime = 0;
    private final SelectorEventLoop watcherLoop;
    private PeriodicEvent periodicEvent = null;

    public FileWatcher(String file, FileWatcherHandler handler) {
        this(file, handler, watcherLoop0);
    }

    public FileWatcher(String file, FileWatcherHandler handler, SelectorEventLoop watcherLoop) {
        this.file = Path.of(file);
        this.handler = handler;
        this.watcherLoop = watcherLoop;
    }

    public void start() {
        if (periodicEvent != null) {
            return;
        }
        periodicEvent = watcherLoop.period(2_000, this::check);
    }

    public void stop() {
        PeriodicEvent periodicEvent = this.periodicEvent;
        if (periodicEvent == null) {
            return;
        }
        periodicEvent.cancel();
    }

    private void check() {
        boolean exists = Files.exists(file);
        if (this.exists && exists) {
            // check update
            long t = getLastModifiedTime();
            if (t > lastModifiedTime) {
                lastModifiedTime = t;
                String content = readFile();
                if (content == null) {
                    lastModifiedTime = 0;
                } else {
                    handler.onFileUpdated(file, content);
                }
            }
        } else if (!this.exists && exists) {
            // alert created
            this.exists = true;
            lastModifiedTime = getLastModifiedTime();
            String content = readFile();
            if (content == null) {
                this.exists = false;
                lastModifiedTime = 0;
            } else {
                handler.onFileCreated(file, content);
            }
        } else if (this.exists) {
            // alert deleted
            this.exists = false;
            lastModifiedTime = 0;
            handler.onFileRemoved(file);
        } // else: not found, nothing to be done
    }

    private long getLastModifiedTime() {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            Logger.shouldNotHappen("failed to get last modified time of file " + file, e);
            return 0;
        }
    }

    private String readFile() {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            Logger.shouldNotHappen("failed to read file " + file, e);
            return null;
        }
    }
}
