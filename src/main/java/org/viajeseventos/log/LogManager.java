package org.viajeseventos.log;

/** Mirrors {@code org.apache.logging.log4j.LogManager}'s entry point so call sites read the same way. */
public final class LogManager {

    private LogManager() {
    }

    public static Logger getLogger(Class<?> owner) {
        return new Logger(owner.getSimpleName());
    }
}
