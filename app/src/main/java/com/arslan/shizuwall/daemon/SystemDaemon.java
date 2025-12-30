package com.arslan.shizuwall.daemon;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;

public class SystemDaemon {
    
    private static final String TAG = "ShizuWallDaemon";
    private static final int PORT = 18522;
    private static final String TOKEN_PATH = "/data/local/tmp/shizuwall.token";
    private static String authToken = "";
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    
    public static void main(String[] args) {
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
            
            // Keep the process alive
            while(true) {
                Thread.sleep(5000);
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
                System.out.println("SystemDaemon: Listening on 127.0.0.1:" + PORT);
                while (true) {
                    Socket client = server.accept();
                    client.setSoTimeout(5000); // 5 second timeout to prevent DoS
                    executor.execute(() -> handleClient(client));
                }
            } catch (Exception e) {
                System.err.println("SystemDaemon: Socket server error");
                e.printStackTrace();
            }
        }).start();
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
            Log.d(TAG, "Received command: [" + command + "]");
            System.out.println("SystemDaemon: Received command: [" + command + "]");
            System.out.flush();

            if (command != null) {
                String result;
                if (command.trim().equalsIgnoreCase("ping")) {
                    result = "pong";
                } else {
                    result = executeCommand(command);
                }

                if (result == null || result.isEmpty()) {
                    result = "(No output from command)";
                }

                Log.d(TAG, "Sending result: " + result);
                System.out.println("SystemDaemon: Sending result (" + result.length() + " chars)");
                System.out.flush();
                
                writer.print(result);
                writer.flush();
            }
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
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd + " 2>&1"});
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = p.waitFor();
            String result = output.toString().trim();
            
            if (result.isEmpty()) {
                return "Command finished with exit code " + exitCode + " (No output)";
            }
            
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Execution error", e);
            System.err.println("SystemDaemon: Exception executing command");
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}
