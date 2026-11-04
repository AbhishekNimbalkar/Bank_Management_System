import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Single-file Master Runner for Bank Management System backend microservices.
 *
 * Usage:
 *   java BackendRunner.java           (Starts all services, keeps them alive, auto-restarts if any crashes)
 *   java BackendRunner.java --stop    (Stops all backend services cleanly)
 *   java BackendRunner.java --status  (Checks health and port status of all services)
 *
 * Features:
 *   - Eureka Discovery Server (port 8761) starts first and is verified.
 *   - Auth Service (port 8081) starts second and is verified.
 *   - Core Services (Organization 8082, Attendance 8083, Audit 8084, Incentive 8085) start in sequence.
 *   - API Gateway (port 8088) starts last once all downstreams are ready.
 *   - Single-instance lock prevents multiple runners from conflicting.
 *   - Auto-restarts any crashed service automatically.
 *   - Graceful shutdown hook clears all child processes on Ctrl+C.
 */
public class BackendRunner {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Path LOGS_DIR = Paths.get("logs");
    private static final Path LOCK_FILE = Paths.get(".runner.lock");

    record ServiceDef(
            String name,
            String jarPath,
            int port,
            String healthUrl,
            boolean waitForReady,
            String[] extraArgs
    ) {}

    private static final List<ServiceDef> SERVICES = List.of(
            new ServiceDef(
                    "discovery-server",
                    "discovery-server/target/discovery-server.jar",
                    8761,
                    "http://localhost:8761",
                    true,
                    new String[]{}
            ),
            new ServiceDef(
                    "auth-service",
                    "auth-service/target/auth-service.jar",
                    8081,
                    "http://localhost:8081/actuator/health",
                    true,
                    new String[]{"--server.port=8081"}
            ),
            new ServiceDef(
                    "organization-service",
                    "organization-service/target/organization-service.jar",
                    8082,
                    "http://localhost:8082/actuator/health",
                    true,
                    new String[]{"--server.port=8082"}
            ),
            new ServiceDef(
                    "attendance-service",
                    "attendance-service/target/attendance-service.jar",
                    8083,
                    "http://localhost:8083/actuator/health",
                    true,
                    new String[]{"--server.port=8083"}
            ),
            new ServiceDef(
                    "audit-service",
                    "audit-service/target/audit-service.jar",
                    8084,
                    "http://localhost:8084/actuator/health",
                    true,
                    new String[]{"--server.port=8084"}
            ),
            new ServiceDef(
                    "incentive-service",
                    "incentive-service/target/incentive-service.jar",
                    8085,
                    "http://localhost:8085/actuator/health",
                    true,
                    new String[]{"--server.port=8085"}
            ),
            new ServiceDef(
                    "api-gateway",
                    "api-gateway/target/api-gateway.jar",
                    8088,
                    "http://localhost:8088/actuator/health",
                    true,
                    new String[]{"--server.port=8088"}
            )
    );

    private static final Map<String, Process> RUNNING_PROCESSES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> RESTART_COUNTS = new ConcurrentHashMap<>();
    private static volatile boolean SHUTTING_DOWN = false;
    private static FileLock LOCK = null;
    private static FileChannel LOCK_CHANNEL = null;

    public static void main(String[] args) {
        log("================================================================================");
        log("             BANK MANAGEMENT SYSTEM - MASTER BACKEND RUNNER                     ");
        log("================================================================================");

        // Handle arguments
        if (args.length > 0) {
            String command = args[0].trim().toLowerCase();
            if (command.equals("--stop") || command.equals("-s")) {
                log("Stopping all backend services...");
                killStaleProcesses();
                releaseLock();
                log("All backend processes stopped.");
                return;
            } else if (command.equals("--status")) {
                showLiveStatus();
                return;
            }
        }

        // Acquire single-instance lock
        if (!acquireLock()) {
            logErr("Another instance of BackendRunner is already active!");
            log("Currently active services:");
            showLiveStatus();
            log("To stop existing services and rerun, execute: java BackendRunner.java --stop");
            return;
        }

        // Setup logs directory
        try {
            Files.createDirectories(LOGS_DIR);
        } catch (IOException e) {
            log("Warning: could not create logs directory: " + e.getMessage());
        }

        // Register shutdown hook for Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(BackendRunner::shutdownAll, "BackendRunner-Shutdown"));

        // Step 1: Clear any stale zombie ports
        log("[1/4] Checking and clearing stale ports (8761, 8081-8085, 8088)...");
        killStaleProcesses();

        // Step 2: Validate compiled JARs
        log("[2/4] Verifying compiled service JARs...");
        for (ServiceDef def : SERVICES) {
            Path jar = Paths.get(def.jarPath());
            if (!Files.exists(jar)) {
                logErr("Missing required JAR: " + jar.toAbsolutePath());
                logErr("Please build with: mvn clean install -DskipTests");
                System.exit(1);
            }
        }
        log("All 7 JARs verified successfully.");

        // Step 3: Sequential Launch
        log("[3/4] Launching services in dependency order...");
        for (ServiceDef def : SERVICES) {
            if (!startService(def)) {
                logErr("Failed to start " + def.name() + ". Aborting startup.");
                shutdownAll();
                System.exit(1);
            }
        }

        // Step 4: Display Master Dashboard
        printDashboard();

        // Step 5: Self-healing continuous monitoring loop
        log("All services running in background. Auto-restart is ENABLED. Press Ctrl+C to stop.");
        while (!SHUTTING_DOWN) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                break;
            }

            if (SHUTTING_DOWN) break;

            for (ServiceDef def : SERVICES) {
                Process proc = RUNNING_PROCESSES.get(def.name());
                if (proc != null && !proc.isAlive() && !SHUTTING_DOWN) {
                    int exitCode = proc.exitValue();
                    int restarts = RESTART_COUNTS.getOrDefault(def.name(), 0);
                    logErr("Service '" + def.name() + "' exited (code: " + exitCode + ").");

                    if (restarts < 5) {
                        RESTART_COUNTS.put(def.name(), restarts + 1);
                        log("--> Auto-restarting " + def.name() + " (attempt " + (restarts + 1) + "/5)...");
                        killPort(def.port());
                        startService(def);
                    } else {
                        logErr("Service '" + def.name() + "' exceeded max restart attempts (5). Leaving offline.");
                        RUNNING_PROCESSES.remove(def.name());
                    }
                }
            }
        }
    }

    private static boolean startService(ServiceDef def) {
        log("--> Starting " + def.name() + " on port " + def.port() + "...");

        List<String> cmd = new ArrayList<>();
        cmd.add(findJavaExecutable());
        // JVM flags: fast JIT tiered stop, serial GC, 32MB initial heap, 256MB max heap for rock-solid stability
        cmd.add("-XX:TieredStopAtLevel=1");
        cmd.add("-XX:+UseSerialGC");
        cmd.add("-Xms32m");
        cmd.add("-Xmx256m");
        cmd.add("-jar");
        cmd.add(def.jarPath());
        Collections.addAll(cmd, def.extraArgs());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File("."));

        Path logFile = LOGS_DIR.resolve(def.name() + ".log");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        try {
            Process process = pb.start();
            RUNNING_PROCESSES.put(def.name(), process);

            if (def.waitForReady()) {
                boolean ready = waitForPort(def.port(), 60);
                if (ready) {
                    log("    [OK] " + def.name() + " is ready on port " + def.port() + " (PID: " + process.pid() + ")");
                    return true;
                } else {
                    logErr("    [FAIL] " + def.name() + " timed out waiting on port " + def.port());
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            logErr("Error starting " + def.name() + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean waitForPort(int port, int maxSeconds) {
        long deadline = System.currentTimeMillis() + (maxSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 800);
                return true;
            } catch (IOException ignored) {
                try {
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void killPort(int port) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            try {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                        "(Get-NetTCPConnection -LocalPort " + port + " -ErrorAction SilentlyContinue).OwningProcess").start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String pidStr = line.trim();
                        if (!pidStr.isEmpty()) {
                            try {
                                long pid = Long.parseLong(pidStr);
                                if (pid > 0 && pid != ProcessHandle.current().pid()) {
                                    ProcessHandle.of(pid).ifPresent(handle -> {
                                        log("  Freeing port " + port + " (terminating PID " + pid + ")");
                                        handle.destroyForcibly();
                                    });
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void killStaleProcesses() {
        for (ServiceDef def : SERVICES) {
            killPort(def.port());
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
    }

    private static void shutdownAll() {
        if (SHUTTING_DOWN) return;
        SHUTTING_DOWN = true;
        System.out.println();
        log("Shutting down all backend services gracefully...");

        for (Map.Entry<String, Process> entry : RUNNING_PROCESSES.entrySet()) {
            String name = entry.getKey();
            Process proc = entry.getValue();
            if (proc.isAlive()) {
                log("  Stopping " + name + " (PID " + proc.pid() + ")...");
                proc.destroy();
                try {
                    if (!proc.waitFor(3, TimeUnit.SECONDS)) {
                        proc.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    proc.destroyForcibly();
                }
            }
        }

        killStaleProcesses();
        releaseLock();
        log("All services stopped cleanly. Goodbye!");
    }

    private static boolean acquireLock() {
        try {
            LOCK_CHANNEL = FileChannel.open(LOCK_FILE, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            LOCK = LOCK_CHANNEL.tryLock();
            if (LOCK != null) {
                // Write current PID to lock file
                String pid = String.valueOf(ProcessHandle.current().pid());
                LOCK_CHANNEL.truncate(0);
                LOCK_CHANNEL.write(java.nio.ByteBuffer.wrap(pid.getBytes()));
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void releaseLock() {
        try {
            if (LOCK != null && LOCK.isValid()) {
                LOCK.release();
            }
            if (LOCK_CHANNEL != null && LOCK_CHANNEL.isOpen()) {
                LOCK_CHANNEL.close();
            }
            Files.deleteIfExists(LOCK_FILE);
        } catch (Exception ignored) {}
    }

    private static void showLiveStatus() {
        System.out.println();
        log("================================================================================");
        log("                        BACKEND SERVICES STATUS                                 ");
        log("================================================================================");
        System.out.printf("  %-24s | %-6s | %-12s | %-28s%n", "SERVICE", "PORT", "STATUS", "URL");
        System.out.println("  -------------------------+--------+--------------+------------------------------");
        for (ServiceDef def : SERVICES) {
            boolean listening = isPortListening(def.port());
            String status = listening ? "ONLINE" : "OFFLINE";
            System.out.printf("  %-24s | %-6d | %-12s | %-28s%n", def.name(), def.port(), status, def.healthUrl());
        }
        System.out.println("  -------------------------+--------+--------------+------------------------------");
    }

    private static void printDashboard() {
        System.out.println();
        log("================================================================================");
        log("                        ALL BACKEND SERVICES ARE LIVE                           ");
        log("================================================================================");
        System.out.printf("  %-24s | %-6s | %-12s | %-28s%n", "SERVICE", "PORT", "STATUS", "URL / HEALTH");
        System.out.println("  -------------------------+--------+--------------+------------------------------");
        for (ServiceDef def : SERVICES) {
            Process p = RUNNING_PROCESSES.get(def.name());
            String pid = (p != null && p.isAlive()) ? "PID " + p.pid() : (isPortListening(def.port()) ? "ONLINE" : "OFFLINE");
            String status = (p != null && p.isAlive()) || isPortListening(def.port()) ? "ACTIVE" : "FAILED";
            System.out.printf("  %-24s | %-6d | %-12s | %-28s%n", def.name(), def.port(), status + " (" + pid + ")", def.healthUrl());
        }
        System.out.println("  -------------------------+--------+--------------+------------------------------");
        log("  API Gateway Entry Point : http://localhost:8088");
        log("  Eureka Service Registry : http://localhost:8761 (User: eureka / Pass: eureka)");
        log("  Aggregated Swagger UI   : http://localhost:8088/swagger-ui.html");
        log("  Log Files Directory     : " + LOGS_DIR.toAbsolutePath());
        log("================================================================================");
        System.out.println();
    }

    private static String findJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path javaBin = Paths.get(javaHome, "bin", System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
            if (Files.exists(javaBin)) {
                return javaBin.toString();
            }
        }
        return "java";
    }

    private static void log(String msg) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FMT) + "] " + msg);
    }

    private static void logErr(String msg) {
        System.err.println("[" + LocalDateTime.now().format(TIME_FMT) + "] [ERROR] " + msg);
    }
}
