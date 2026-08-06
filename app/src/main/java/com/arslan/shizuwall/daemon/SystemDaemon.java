package com.arslan.shizuwall.daemon;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SystemDaemon {
    
    private static final String TAG = "ShizuWallDaemon";
    private static final int PORT = 18522;
    private static final String TOKEN_PATH = "/data/local/tmp/shizuwall.token";
    private static final int MAX_CONCURRENT_COMMANDS = 4;
    private static final int COMMAND_TIMEOUT_SECONDS = 30;
    private static final int MAX_COMMAND_LENGTH = 4096;
    private static final String FW_UID_RULE_COMMAND = "fw-uid-rule";
    private static final String FW_UID_RULES_COMMAND = "fw-uid-rules";
    private static final String FW_UID_SERVER_COMMAND = "fw-uid-server";
    private static final int FIREWALL_CHAIN_OEM_DENY_3 = 9;
    
    // Blocked dangerous commands
    private static final Set<String> BLOCKED_PATTERNS = new HashSet<>(Arrays.asList(
        "rm -rf /",
        "mkfs",
        "dd if=",
        "> /dev/",
        ":(){ :|:& };:" // fork bomb
    ));
    
    private static String authToken = "";
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_COMMANDS);
    private static final Semaphore commandSemaphore = new Semaphore(MAX_CONCURRENT_COMMANDS);
    private static final AtomicInteger activeConnections = new AtomicInteger(0);
    private static volatile boolean running = true;

    private static void logD(String message) {
        System.out.println(TAG + " [D] " + message);
    }

    private static void logW(String message) {
        System.err.println(TAG + " [W] " + message);
    }

    private static void logE(String message, Throwable t) {
        System.err.println(TAG + " [E] " + message);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }
    
    public static void main(String[] args) {
        if (args != null && args.length == 1 && FW_UID_SERVER_COMMAND.equals(args[0])) {
            runUidRuleServer();
            return;
        }
        if (args != null && args.length == 3 && FW_UID_RULE_COMMAND.equals(args[0])) {
            System.out.println(setUidFirewallRule(args[0] + " " + args[1] + " " + args[2]));
            return;
        }
        if (args != null && args.length == 2 && FW_UID_RULES_COMMAND.equals(args[0])) {
            System.out.println(setUidFirewallRules(args[1]));
            return;
        }

        // Setup shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("SystemDaemon: Shutdown signal received");
            running = false;
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }));
        
        try {
            File tokenFile = new File(TOKEN_PATH);
            if (!tokenFile.exists()) {
                System.err.println("SystemDaemon: Token file not found at " + TOKEN_PATH);
                System.exit(1);
            }
            try (BufferedReader br = new BufferedReader(new FileReader(tokenFile))) {
                authToken = br.readLine();
            }
            if (authToken == null || authToken.trim().isEmpty()) {
                System.err.println("SystemDaemon: Token file is empty");
                System.exit(1);
            }
            authToken = authToken.trim();
            
            // Secure the token file further
            tokenFile.setReadable(false, false);
            tokenFile.setReadable(true, true);
        } catch (Exception e) {
            System.err.println("SystemDaemon: Failed to read token: " + e.getMessage());
            System.exit(1);
        }

        logD("Daemon starting...");
        System.out.println("SystemDaemon: Starting...");
        System.out.flush();
        try {
            // Log identity
            executeCommand("id");
            
            // Start TCP socket server
            startSocketServer();
            logD("TCP server started on port " + PORT);
            System.out.println("SystemDaemon: TCP server started on port " + PORT);
            System.out.flush();
            
            // Keep the process alive with health logging
            while(running) {
                Thread.sleep(30000); // 30 seconds
                logD("Heartbeat - Active connections: " + activeConnections.get());
            }
        } catch (Exception e) {
            System.err.println("SystemDaemon: Fatal error in main");
            e.printStackTrace();
        }
    }
    
    private static void startSocketServer() throws Exception {
        new Thread(() -> {
            try {
                // Bind only to localhost (loopback) to prevent external network access
                ServerSocket server = new ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"));
                server.setReuseAddress(true);
                System.out.println("SystemDaemon: Listening on 127.0.0.1:" + PORT);
                
                while (running) {
                    try {
                        Socket client = server.accept();
                        client.setSoTimeout(10000);
                        activeConnections.incrementAndGet();
                        
                        try {
                            executor.execute(() -> {
                                try {
                                    handleClient(client);
                                } finally {
                                    activeConnections.decrementAndGet();
                                }
                            });
                        } catch (RejectedExecutionException e) {
                            // Too many connections, reject
                            activeConnections.decrementAndGet();
                            try {
                                PrintWriter w = new PrintWriter(client.getOutputStream());
                                w.println("Error: Server busy");
                                w.flush();
                                client.close();
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception e) {
                        if (running) {
                            System.err.println("SystemDaemon: Accept error: " + e.getMessage());
                        }
                    }
                }
                server.close();
            } catch (Exception e) {
                System.err.println("SystemDaemon: Socket server error");
                e.printStackTrace();
            }
        }, "SocketServer").start();
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static void handleClient(Socket socket) {
        String command = "unknown";
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true)
        ) {
            String token = reader.readLine();
            if (!safeEquals(token, authToken)) {
                logW("Unauthorized access attempt");
                System.out.println("SystemDaemon: Unauthorized access attempt");
                writer.println("Error: Unauthorized");
                return;
            }

            command = reader.readLine();
            
            // Validate command
            if (command == null || command.trim().isEmpty()) {
                writer.println("Error: Empty command");
                return;
            }
            
            if (command.length() > MAX_COMMAND_LENGTH) {
                logW("Command too long: " + command.length() + " chars");
                writer.println("Error: Command too long");
                return;
            }
            
            // Check for dangerous patterns
            String lowerCmd = command.toLowerCase();
            for (String blocked : BLOCKED_PATTERNS) {
                if (lowerCmd.contains(blocked.toLowerCase())) {
                    logW("Blocked dangerous command: " + command);
                    writer.println("Error: Command blocked for safety");
                    return;
                }
            }
            
            logD("Received command: [" + command + "]");
            System.out.println("SystemDaemon: Received command: [" + command + "]");
            System.out.flush();

            String result;
            if (command.trim().equalsIgnoreCase("ping")) {
                result = "pong";
            } else if (command.trim().startsWith(FW_UID_RULE_COMMAND)) {
                result = setUidFirewallRule(command.trim());
            } else if (command.trim().equalsIgnoreCase("status")) {
                result = "active:" + activeConnections.get() + ",uptime:" + 
                         (System.currentTimeMillis() / 1000);
            } else {
                // Acquire semaphore for rate limiting
                if (!commandSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    writer.println("Error: Too many concurrent commands");
                    return;
                }
                try {
                    result = executeCommand(command);
                } finally {
                    commandSemaphore.release();
                }
            }

            if (result == null || result.isEmpty()) {
                result = "(No output from command)";
            }

            logD("Sending result: " + result.substring(0, Math.min(100, result.length())));
            System.out.println("SystemDaemon: Sending result (" + result.length() + " chars)");
            System.out.flush();
            
            writer.print(result);
            writer.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logW("Command interrupted: " + command);
        } catch (Exception e) {
            logE("Client handler error for command: " + command, e);
            System.err.println("SystemDaemon: Client handler error");
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Handles "fw-uid-rule &lt;uid&gt; &lt;rule&gt;". Unlike the shell command, the binder API takes
     * a uid, so a block does not follow the appId into other users. Callable here because the
     * process runs as shell and app_process leaves non-SDK interfaces reachable.
     */
    private static String setUidFirewallRule(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length != 3) {
            return "Error (code 22): usage: " + FW_UID_RULE_COMMAND + " <uid> <rule>";
        }
        final int uid;
        final int rule;
        try {
            uid = Integer.parseInt(parts[1]);
            rule = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "Error (code 22): uid and rule must be integers";
        }
        try {
            Object binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, "connectivity");
            if (binder == null) {
                return "Error (code 1): connectivity service unavailable";
            }
            Object service = Class.forName("android.net.IConnectivityManager$Stub")
                    .getMethod("asInterface", Class.forName("android.os.IBinder"))
                    .invoke(null, binder);
            if (service == null) {
                return "Error (code 1): could not bind IConnectivityManager";
            }
            service.getClass()
                    .getMethod("setUidFirewallRule", int.class, int.class, int.class)
                    .invoke(service, FIREWALL_CHAIN_OEM_DENY_3, uid, rule);
            return "OK " + uid + " " + rule;
        } catch (Throwable t) {
            Throwable cause = (t.getCause() != null) ? t.getCause() : t;
            logE("setUidFirewallRule failed for uid " + uid, cause);
            return "Error (code 1): " + cause;
        }
    }

    private static String setUidFirewallRules(String encodedRules) {
        StringBuilder result = new StringBuilder();
        String[] rules = encodedRules.split(",");
        for (String encodedRule : rules) {
            String[] values = encodedRule.split(":");
            if (values.length != 2) {
                result.append("Error (code 22): invalid uid rule");
                break;
            }
            String response = setUidFirewallRule(
                    FW_UID_RULE_COMMAND + " " + values[0] + " " + values[1]
            );
            if (!response.startsWith("OK")) {
                result.append(response);
                break;
            }
            if (result.length() > 0) result.append(';');
            result.append(response);
        }
        return result.toString();
    }

    private static void runUidRuleServer() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)), true)
        ) {
            writer.println("READY");
            String command;
            while ((command = reader.readLine()) != null) {
                if ("exit".equals(command)) return;
                String result = command.startsWith(FW_UID_RULES_COMMAND + " ")
                        ? setUidFirewallRules(command.substring(FW_UID_RULES_COMMAND.length() + 1))
                        : "Error (code 22): unsupported command";
                writer.println(result);
            }
        } catch (Exception ignored) {
        }
    }

    private static String executeCommand(String cmd) {
        System.out.println("SystemDaemon: Executing: " + cmd);
        System.out.flush();
        
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", cmd + " 2>&1");
            pb.redirectErrorStream(true);
            p = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                int totalLength = 0;
                final int MAX_OUTPUT = 1024 * 1024; // 1MB limit
                
                while ((line = reader.readLine()) != null) {
                    if (totalLength + line.length() > MAX_OUTPUT) {
                        output.append("\n... (output truncated)");
                        break;
                    }
                    output.append(line).append("\n");
                    totalLength += line.length() + 1;
                }
            }
            
            // Wait with timeout
            boolean finished = p.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "Error (code 124): Command timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds";
            }
            
            int exitCode = p.exitValue();
            String result = output.toString().trim();
            
            if (exitCode != 0 && result.isEmpty()) {
                return "Error (code " + exitCode + "): Command failed with no output";
            }
            
            if (result.isEmpty()) {
                return "Command finished with exit code " + exitCode + " (No output)";
            }
            
            return result;
        } catch (Exception e) {
            logE("Execution error", e);
            System.err.println("SystemDaemon: Exception executing command");
            e.printStackTrace();
            return "Error: " + e.getMessage();
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }
}
