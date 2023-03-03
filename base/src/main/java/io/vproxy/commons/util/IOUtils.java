package io.vproxy.commons.util;

import io.vproxy.base.util.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class IOUtils {
    private IOUtils() {
    }

    public static void writeFileWithBackup(String filepath, String content) throws Exception {
        Logger.alert("Trying to write into file: " + filepath + ".new");
        File f = new File(filepath + ".new");
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        if (!f.createNewFile()) {
            throw new Exception("Create new file " + filepath + ".new failed");
        }
        try (FileOutputStream fos = new FileOutputStream(f)) {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));
            bw.write(content);
            bw.flush();
        }

        Logger.alert("Backup old file " + filepath);
        backupAndRemove(filepath);

        Logger.alert("Move new file to " + filepath);
        Files.move(Path.of(f.getAbsolutePath()), Path.of(filepath), StandardCopyOption.REPLACE_EXISTING);

        Logger.alert("Writing into file done: " + filepath);
    }

    private static void backupAndRemove(String filepath) throws Exception {
        File f = new File(filepath);
        File bakF = new File(filepath + ".bak");

        if (!f.exists())
            return; // do nothing if no need to backup
        if (bakF.exists() && !bakF.delete()) // remove old backup file
            throw new Exception("remove old backup file failed: " + bakF.getPath());
        if (f.exists() && !f.renameTo(bakF)) // do rename (backup)
            throw new Exception("backup the file failed: " + bakF.getPath());
    }

    public static String writeTemporaryFile(String prefix, String suffix, byte[] content) throws IOException {
        return writeTemporaryFile(prefix, suffix, content, false);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static String writeTemporaryFile(String prefix, String suffix, byte[] content, boolean executable) throws IOException {
        File f = File.createTempFile("vproxy-" + ProcessHandle.current().pid() + "-" + prefix, "." + suffix);
        f.deleteOnExit();
        Files.write(f.toPath(), content);
        f.setReadable(true);
        if (executable) {
            f.setExecutable(true);
        }
        return f.getAbsolutePath();
    }

    public static boolean deleteDirectory(File base) {
        if (base.isFile()) {
            return base.delete();
        }
        if (!base.isDirectory()) { // ignore special files
            return true;
        }
        var allContents = base.listFiles();
        if (allContents != null) {
            for (var file : allContents) {
                var ok = deleteDirectory(file);
                if (!ok) {
                    return false;
                }
            }
        }
        return base.delete();
    }

    public static void copyDirectory(Path src, Path dest) throws IOException {
        try (var stream = Files.walk(src)) {
            for (var ite = stream.iterator(); ite.hasNext(); ) {
                var source = ite.next();
                _copyDirectory(source, dest.resolve(src.relativize(source)));
            }
        }
    }

    private static void _copyDirectory(Path source, Path dest) throws IOException {
        if (source.toFile().isDirectory() && dest.toFile().isDirectory()) {
            return; // exists
        }
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }
}
