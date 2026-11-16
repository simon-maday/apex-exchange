package com.maday.apex.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public class EventLogLockProcess {

    static void main(String[] args) throws Exception {

        var mode = args[0];
        var logPath = Path.of(args[1]);

        switch (mode) {
            case "hold" -> holdLock(logPath);
            case "try" -> tryLock(logPath);
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void tryLock(Path logPath) {

        try (var ignored = EventLogOwnership.acquire(logPath)){

            System.out.println("ACQUIRED");
            System.out.flush();

        } catch (IOException e) {

           IO.println("BUSY");
           System.out.flush();

           System.exit(2);
        }

    }

    private static void holdLock(Path logPath) throws Exception {
        try (var ignored = EventLogOwnership.acquire(logPath) ){

            IO.println("LOCKED");
            System.out.flush();

            // Keep this process alive until forcibly terminated
            new CountDownLatch(1).await();
        }
    }
}
