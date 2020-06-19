package vproxyapp.controller;

import vproxybase.util.ByteArray;
import vproxybase.util.Logger;

import java.io.IOException;
import java.io.InputStream;

public class ClasspathResourceHolder {
    private final String basePath;

    public ClasspathResourceHolder(String basePath) {
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        this.basePath = basePath;
    }

    public ByteArray get(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String resPath = basePath + path;
        InputStream inputStream = ClasspathResourceHolder.class.getResourceAsStream(resPath);
        if (inputStream == null) {
            return null;
        }
        ByteArray ret = null;
        final int BUF_LEN = 2048;
        byte[] buf = new byte[BUF_LEN];
        int len;
        try {
            while ((len = inputStream.read(buf)) > 0) {
                ByteArray b = ByteArray.from(buf);
                if (len < BUF_LEN) {
                    b = b.sub(0, len);
                }
                if (ret == null) {
                    ret = b;
                } else {
                    ret = ret.concat(b);
                }
            }
        } catch (IOException e) {
            Logger.shouldNotHappen("reading from classpath got exception", e);
            return null;
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignore) {
            }
        }
        if (ret == null) {
            ret = ByteArray.allocate(0);
        }
        return ret;
    }
}
