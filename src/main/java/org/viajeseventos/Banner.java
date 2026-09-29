package org.viajeseventos;

/** Printed once at boot, ahead of any log output — same idea as Spring Boot's {@code banner.txt}. */
final class Banner {

    private Banner() {
    }

    static final String TEXT = """
            __     ___       _                                               _
            \\ \\   / (_) __ _ (_) ___  ___    __ _    _____   _____ _ __ | |_ ___  ___
             \\ \\ / /| |/ _` || |/ _ \\/ __|  / _` |  / _ \\ \\ / / _ \\ '_ \\| __/ _ \\/ __|
              \\ V / | | (_| || |  __/\\__ \\ | (_| | |  __/\\ V /  __/ | | | || (_) \\__ \\
               \\_/  |_|\\__,_|/ |\\___||___/  \\__,_|  \\___| \\_/ \\___|_| |_|\\__\\___/|___/
                           |__/                                                   api
            """;


    static void print() {
        System.out.println(TEXT);
    }
}
