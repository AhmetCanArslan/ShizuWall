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
import android.util.Log;

public class SystemDaemon {
    
    private static final String TAG = "ShizuWallDaemon";
    private static final int PORT = 18522;
    private static final String TOKEN_PATH = "/data/local/tmp/shizuwall.token";
    private static final int MAX_CONCURRENT_COMMANDS = 4;
    private static final int COMMAND_TIMEOUT_SECONDS = 30;
    private static final int MAX_COMMAND_LENGTH = 4096;
    
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
    
    public static void main(String[] args) {
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

        Log.d(TAG, "Daemon starting...");
        System.out.println("SystemDaemon: Starting...");
        System.out.flush();
        try {
            // Log identity
            executeCommand("id");
            
            // Start TCP socket server
            startSocketServer();
            Log.d(TAG, "TCP server started on port " + PORT);
            System.out.println("SystemDaemon: TCP server started on port " + PORT);
            System.out.flush();
            
            // Keep the process alive with health logging
            while(running) {
                Thread.sleep(30000); // 30 seconds
                Log.d(TAG, "Heartbeat - Active connections: " + activeConnections.get());
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
                Log.w(TAG, "Unauthorized access attempt");
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
                Log.w(TAG, "Command too long: " + command.length() + " chars");
                writer.println("Error: Command too long");
                return;
            }
            
            // Check for dangerous patterns
            String lowerCmd = command.toLowerCase();
            for (String blocked : BLOCKED_PATTERNS) {
                if (lowerCmd.contains(blocked.toLowerCase())) {
                    Log.w(TAG, "Blocked dangerous command: " + command);
                    writer.println("Error: Command blocked for safety");
                    return;
                }
            }
            
            Log.d(TAG, "Received command: [" + command + "]");
            System.out.println("SystemDaemon: Received command: [" + command + "]");
            System.out.flush();

            String result;
            if (command.trim().equalsIgnoreCase("ping")) {
                result = "pong";
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

            Log.d(TAG, "Sending result: " + result.substring(0, Math.min(100, result.length())));
            System.out.println("SystemDaemon: Sending result (" + result.length() + " chars)");
            System.out.flush();
            
            writer.print(result);
            writer.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Command interrupted: " + command);
        } catch (Exception e) {
            Log.e(TAG, "Client handler error for command: " + command, e);
            System.err.println("SystemDaemon: Client handler error");
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
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
            Log.e(TAG, "Execution error", e);
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
