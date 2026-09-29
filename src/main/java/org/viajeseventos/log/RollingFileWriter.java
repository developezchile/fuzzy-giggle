package org.viajeseventos.log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Appends log lines to an external file, rotating it once it crosses {@code maxFileSizeBytes}:
 * the oldest numbered backup ({@code <path>.maxHistory}) is dropped, remaining backups shift up
 * by one ({@code .N} -> {@code .N+1}), and the active file becomes {@code .1} before a fresh one
 * is opened. This is the hand-rolled equivalent of Log4j2's {@code RollingFileAppender} with a
 * {@code SizeBasedTriggeringPolicy} + {@code DefaultRolloverStrategy}, kept dependency-free like
 * the rest of {@link org.viajeseventos.log}.
 */
final class RollingFileWriter {

    private final Path activeFile;
    private final long maxFileSizeBytes;
    private final int maxHistory;
    private PrintStream out;

    RollingFileWriter(String path, long maxFileSizeBytes, int maxHistory) {
        this.activeFile = Path.of(path);
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxHistory = maxHistory;
        openStream();
    }

    synchronized void write(String text) {
        rotateIfNeeded();
        out.print(text);
        out.flush();
    }

    private void openStream() {
        try {
            if (activeFile.getParent() != null) {
                Files.createDirectories(activeFile.getParent());
            }
            out = new PrintStream(new FileOutputStream(activeFile.toFile(), true), false, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not open log file: " + activeFile, e);
        }
    }

    private void rotateIfNeeded() {
        try {
            if (Files.exists(activeFile) && Files.size(activeFile) >= maxFileSizeBytes) {
                rotate();
            }
        } catch (IOException e) {
            System.err.println("Log rotation check failed: " + e.getMessage());
        }
    }

    private void rotate() {
        out.close();
        try {
            Files.deleteIfExists(numbered(maxHistory));
            for (int i = maxHistory - 1; i >= 1; i--) {
                Path src = numbered(i);
                if (Files.exists(src)) {
                    Files.move(src, numbered(i + 1), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (maxHistory >= 1) {
                Files.move(activeFile, numbered(1), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(activeFile);
            }
        } catch (IOException e) {
            System.err.println("Log rotation failed: " + e.getMessage());
        } finally {
            openStream();
        }
    }

    private Path numbered(int index) {
        return Path.of(activeFile + "." + index);
    }
}
