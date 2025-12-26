package com.arslan.shizuwall.daemon;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import android.util.Log;

public class SystemDaemon {
    
    private static final String TAG = "ShizuWallDaemon";
    private static final int PORT = 18521;
    
    public static void main(String[] args) {
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
                ServerSocket server = new ServerSocket(PORT);
                System.out.println("SystemDaemon: Listening on TCP port: " + PORT);
                while (true) {
                    Socket client = server.accept();
                    handleClient(client);
                }
            } catch (Exception e) {
                System.err.println("SystemDaemon: Socket server error");
                e.printStackTrace();
            }
        }).start();
    }

    private static void handleClient(Socket socket) {
        new Thread(() -> {
            String command = "unknown";
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true)
            ) {
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
        }).start();
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
