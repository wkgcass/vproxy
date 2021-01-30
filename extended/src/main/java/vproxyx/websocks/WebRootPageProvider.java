package vproxyx.websocks;

import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.file.FileFD;
import vproxybase.selector.wrap.file.FilePath;
import vproxybase.util.ByteArray;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebRootPageProvider implements PageProvider {
    private static final int EXPIRE_DURATION = 2 * 3600 * 1000;
    private static final long LARGE_FILE_THRESHOLD = 2 * 1024 * 1024; // 2M
    private static final Set<String> CACHED_MIMES = Set.of("text/html", "text/css", "text/javascript", "image/png", "image/jpeg");
    private final String baseDir;
    private final String protocol;
    private final String domain;
    private final int port;
    // pages1 and pages2 are used for expiration
    // when request comes, the timestamp is checked
    // pages1 might be cleared and all contents of pages2 will be set into pages1
    private ConcurrentHashMap<String, Page> pages1 = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Page> pages2 = new ConcurrentHashMap<>();
    private long lastExpiredTime = 0;

    private static class Page {
        final ByteArray content;
        final long updateTime;

        private Page(ByteArray content, long updateTime) {
            this.content = content;
            this.updateTime = updateTime;
        }
    }

    public WebRootPageProvider(String baseDir, RedirectBaseInfo info) {
        this.baseDir = baseDir;
        this.protocol = info.protocol;
        this.domain = info.domain;
        this.port = info.port;
    }

    private File findFile(String url) {
        // try the url
        Path p = Path.of(baseDir, url);
        File f = p.toFile();
        if (f.exists() && !f.isDirectory()) {
            return f;
        }
        // try to add index.html
        p = Path.of(baseDir, url, "index.html");
        f = p.toFile();
        if (f.exists() && !f.isDirectory()) {
            return f;
        }
        // for url not ending with '/'
        // append '.html'
        if (!url.endsWith("/")) {
            p = Path.of(baseDir, url + ".html");
            f = p.toFile();
            if (f.exists() && !f.isDirectory()) {
                return f;
            }
        }
        // not found
        return null;
    }

    private String getMime(File f) {
        String name = f.getName();
        if (name.endsWith(".html")) {
            return "text/html";
        } else if (name.endsWith(".js")) {
            return "text/javascript";
        } else if (name.endsWith(".css")) {
            return "text/css";
        } else if (name.endsWith(".json")) {
            return "application/json";
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (name.endsWith(".png")) {
            return "image/png";
        } else {
            return "application/octet-stream";
        }
    }

    @Override
    public PageResult getPage(String url) {
        // first try to expire the pages
        long current = System.currentTimeMillis();
        if (current - lastExpiredTime > EXPIRE_DURATION) {
            Logger.alert("page cache expires, " + pages1.size() + " pages cleared");
            pages1 = pages2;
            pages2 = new ConcurrentHashMap<>();
            lastExpiredTime = current;
        }

        if (!url.endsWith("/")) {
            // maybe url not ending with `/` but it's a directory
            // relative path may be wrong
            File file = Path.of(baseDir, url).toFile();
            if (file.isDirectory()) {
                // need to redirect
                String portStr = ":" + port;
                if (protocol.equals("http")) {
                    if (port == 80) {
                        portStr = "";
                    }
                } else if (protocol.equals("https")) {
                    if (port == 443) {
                        portStr = "";
                    }
                }
                return new PageResult(protocol + "://" + domain + portStr + url + "/");
            }
        }
        File file = findFile(url);
        if (file == null) {
            return null;
        }
        String mime = getMime(file);
        long cacheAge = getCacheAgeFromMime(mime);
        String key = file.getAbsolutePath();
        if (!file.exists() || file.isDirectory()) {
            removePageCache(key);
            return null;
        }

        long fileLength = file.length();
        if (fileLength > LARGE_FILE_THRESHOLD) {
            Logger.alert("file " + key + " length " + fileLength + ", handled as a large file");
            var currentLoop = SelectorEventLoop.current();
            if (currentLoop == null) {
                Logger.shouldNotHappen("the WebRootPageProvider should run on the event loop which is handling the request");
                return null;
            }
            FileFD fileFD = new FileFD(currentLoop, StandardOpenOption.READ);
            try {
                fileFD.connect(new FilePath(key));
            } catch (IOException e) {
                Logger.error(LogType.FILE_ERROR, "call connect(...) on FileFD " + key + " failed", e);
                return null;
            }
            try {
                fileFD.finishConnect();
            } catch (IOException e) {
                Logger.shouldNotHappen("call finishConnect() on FileFD " + key + " failed", e);
                return null;
            }
            return new PageResult(mime, fileFD, cacheAge);
        }

        long time = file.lastModified();
        Page page = getCachedPage(key);
        if (page != null) {
            if (time != page.updateTime) {
                removePageCache(key);
                page = null;
            }
        }
        if (page != null) {
            assert Logger.lowLevelDebug("using cached page: " + url);
            return new PageResult(mime, page.content, cacheAge);
        }
        assert Logger.lowLevelDebug("reading from disk: " + url);
        byte[] buf = Utils.allocateByteArray(1024);
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int r;
            while ((r = fis.read(buf)) >= 0) {
                baos.write(buf, 0, r);
            }
            ByteArray content = ByteArray.from(baos.toByteArray());
            page = new Page(content, time);
            recordPageCache(key, page);
            return new PageResult(mime, page.content, cacheAge);
        } catch (IOException e) {
            Logger.error(LogType.FILE_ERROR, "reading file " + key + " failed", e);
            return null;
        }
    }

    private long getCacheAgeFromMime(String mime) {
        if ((CACHED_MIMES.contains(mime))) {
            return 60L * 10; // 10 minutes
        }
        return 0L;
    }

    private void removePageCache(String key) {
        pages1.remove(key);
        pages2.remove(key);
    }

    private Page getCachedPage(String key) {
        var ret = pages2.get(key);
        if (ret != null) {
            return ret;
        }
        return pages1.get(key);
    }

    private void recordPageCache(String key, Page page) {
        pages2.put(key, page);
    }
}
