package org.viajeseventos.log;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal stand-in for a Log4j2/SLF4J logger: same {@code {}}-placeholder API and the same
 * console pattern Log4j2 ships by default ({@code %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level
 * %logger - %msg%n}). Hand-written instead of pulling in log4j-core so this module keeps its
 * zero-reflection-config footprint; see {@link org.viajeseventos.Main}.
 *
 * <p>Output targets (console / external rolling file) and per-logger levels are configured via
 * {@code logging.yml}, loaded once by {@link LoggingConfig}.
 */
public final class Logger {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final LoggingConfig CONFIG = LoggingConfig.INSTANCE;
    private static final RollingFileWriter FILE_WRITER = CONFIG.fileEnabled
            ? new RollingFileWriter(CONFIG.filePath, CONFIG.maxFileSizeBytes, CONFIG.maxHistory)
            : null;

    private final String name;
    private final Level threshold;

    Logger(String name) {
        this.name = name;
        this.threshold = CONFIG.levelFor(name);
    }

    public void debug(String message, Object... args) {
        log(Level.DEBUG, System.out, message, args, null);
    }

    public void info(String message, Object... args) {
        log(Level.INFO, System.out, message, args, null);
    }

    public void warn(String message, Object... args) {
        log(Level.WARN, System.err, message, args, null);
    }

    public void error(String message, Throwable t, Object... args) {
        log(Level.ERROR, System.err, message, args, t);
    }

    private void log(Level level, PrintStream console, String message, Object[] args, Throwable t) {
        if (level.ordinal() < threshold.ordinal()) return;
        String line = String.format("%s [%s] %-5s %s - %s%n",
                LocalDateTime.now().format(TIMESTAMP), threadLabel(), level, name, format(message, args));

        if (CONFIG.consoleEnabled) {
            console.print(line);
            if (t != null) t.printStackTrace(console);
        }
        if (FILE_WRITER != null) {
            FILE_WRITER.write(line);
            if (t != null) FILE_WRITER.write(stackTraceOf(t));
        }
    }

    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /** Virtual threads (this server's request executor) are unnamed by default, so fall back to their id. */
    private static String threadLabel() {
        Thread current = Thread.currentThread();
        String threadName = current.getName();
        return threadName.isEmpty() ? "vthread-" + current.threadId() : threadName;
    }

    private String format(String message, Object[] args) {
        if (args == null || args.length == 0) return message;
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}' && argIndex < args.length) {
                sb.append(args[argIndex++]);
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
