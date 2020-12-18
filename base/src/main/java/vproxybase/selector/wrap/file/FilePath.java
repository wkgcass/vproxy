package vproxybase.selector.wrap.file;

import vfd.IPPort;

import java.util.Objects;

public class FilePath extends IPPort {
    public final String filepath;

    public FilePath(String filepath) {
        super("127.0.0.1", -1);
        this.filepath = filepath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FilePath filePath = (FilePath) o;
        return Objects.equals(filepath, filePath.filepath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filepath);
    }

    @Override
    public String formatToIPPortString() {
        return toString();
    }

    @Override
    public String toString() {
        return "FilePath:" + filepath;
    }
}
