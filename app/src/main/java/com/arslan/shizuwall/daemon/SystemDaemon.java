package com.arslan.shizuwall.daemon;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
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
    private static final String FW_UID_RULES_COMMAND = "fw-uid-rules";
    private static final String FW_UID_SERVER_COMMAND = "fw-uid-server";
    private static final String FG_TASK_COMMAND = "fg-task";
    private static final String FG_WATCH_COMMAND = "fg-watch";
    private static final long FG_DEBOUNCE_MS = 150;
    private static final long FG_HEARTBEAT_MS = 30000;
    private static final String FG_KEEPALIVE = ".";
    private static final int FIREWALL_CHAIN_OEM_DENY_3 = 9;
    private static final String UID_OWNER_MAP_MISSING = "suidownermap does not have entry for uid";
    
    // Blocked dangerous commands
    private static final Set<String> BLOCKED_PATTERNS = new HashSet<>(Arrays.asList(
        "rm -rf /",
        "mkfs",
        "dd if=",
        "> /dev/",
        ":(){ :|:& };:" // fork bomb
    ));
    
    private static String authToken = "";
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_COMMANDS * 2);
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
        if (args != null && args.length == 1 && FG_WATCH_COMMAND.equals(args[0])) {
            final PrintWriter stdout = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)), true);
            watchForegroundTask(new ForegroundEmitter() {
                @Override
                public boolean emit(String value) {
                    stdout.println(value);
                    return !stdout.checkError();
                }
            });
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
            } else if (command.trim().startsWith(FW_UID_RULES_COMMAND + " ")) {
                result = setUidFirewallRules(
                        command.trim().substring(FW_UID_RULES_COMMAND.length() + 1)
                );
            } else if (command.trim().equalsIgnoreCase(FG_WATCH_COMMAND)) {
                final PrintWriter sink = writer;
                watchForegroundTask(new ForegroundEmitter() {
                    @Override
                    public boolean emit(String value) {
                        sink.println(value);
                        return !sink.checkError();
                    }
                });
                return;
            } else if (command.trim().equalsIgnoreCase(FG_TASK_COMMAND)) {
                result = foregroundTask();
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

    private static Object connectivityService() throws Exception {
        Object binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class)
                .invoke(null, "connectivity");
        if (binder == null) {
            return null;
        }
        return Class.forName("android.net.IConnectivityManager$Stub")
                .getMethod("asInterface", Class.forName("android.os.IBinder"))
                .invoke(null, binder);
    }

    private static String setUidFirewallRules(String encodedRules) {
        Object service = null;
        Method setter = null;
        String setupError = null;
        try {
            service = connectivityService();
            if (service == null) {
                setupError = "Error (code 1): connectivity service unavailable";
            } else {
                setter = service.getClass()
                        .getMethod("setUidFirewallRule", int.class, int.class, int.class);
            }
        } catch (Throwable t) {
            Throwable cause = (t.getCause() != null) ? t.getCause() : t;
            logE("could not bind IConnectivityManager", cause);
            setupError = "Error (code 1): " + cause;
        }

        StringBuilder result = new StringBuilder();
        for (String encodedRule : encodedRules.split(",")) {
            String response;
            String[] values = encodedRule.split(":");
            if (setupError != null) {
                response = setupError;
            } else if (values.length != 2) {
                response = "Error (code 22): invalid uid rule";
            } else {
                int uid = 0;
                int rule = 0;
                boolean parsed;
                try {
                    uid = Integer.parseInt(values[0].trim());
                    rule = Integer.parseInt(values[1].trim());
                    parsed = true;
                } catch (NumberFormatException e) {
                    parsed = false;
                }
                if (!parsed) {
                    response = "Error (code 22): uid and rule must be integers";
                } else {
                    try {
                        setter.invoke(service, FIREWALL_CHAIN_OEM_DENY_3, uid, rule);
                        response = "OK " + uid + " " + rule;
                    } catch (Throwable t) {
                        Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                        if (cause.toString().toLowerCase(Locale.ROOT).contains(UID_OWNER_MAP_MISSING)) {
                            response = "OK " + uid + " " + rule;
                        } else {
                            logE("setUidFirewallRule failed for uid " + uid, cause);
                            response = "Error (code 1): " + cause;
                        }
                    }
                }
            }
            if (result.length() > 0) result.append(';');
            result.append(response.replace(';', ',').replace('\n', ' ').replace('\r', ' '));
        }
        return result.toString();
    }

    private static Object fieldValue(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object[] taskQueryArgs(Class<?>[] types) {
        Object[] args = new Object[types.length];
        int intIndex = 0;
        int boolIndex = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class || types[i] == Integer.class) {
                args[i] = (intIndex++ == 0) ? Integer.valueOf(1) : Integer.valueOf(0);
            } else if (types[i] == boolean.class || types[i] == Boolean.class) {
                args[i] = Boolean.valueOf(boolIndex++ == 0);
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    public static String foregroundTask() {
        try {
            Object binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, "activity_task");
            if (binder == null) {
                return "Error (code 1): activity_task service unavailable";
            }
            Object atm = Class.forName("android.app.IActivityTaskManager$Stub")
                    .getMethod("asInterface", Class.forName("android.os.IBinder"))
                    .invoke(null, binder);
            if (atm == null) {
                return "Error (code 1): IActivityTaskManager unavailable";
            }
            Method getTasks = null;
            for (Method method : atm.getClass().getMethods()) {
                if (!"getTasks".equals(method.getName())) continue;
                if (getTasks == null
                        || method.getParameterTypes().length > getTasks.getParameterTypes().length) {
                    getTasks = method;
                }
            }
            if (getTasks == null) {
                return "Error (code 1): getTasks unavailable";
            }
            Object raw = getTasks.invoke(atm, taskQueryArgs(getTasks.getParameterTypes()));
            if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) {
                return "Error (code 2): no visible task";
            }
            Object task = ((List<?>) raw).get(0);
            Object component = fieldValue(task, "topActivity");
            if (component == null) {
                return "Error (code 2): task has no activity";
            }
            String packageName = (String) component.getClass()
                    .getMethod("getPackageName")
                    .invoke(component);
            if (packageName == null || packageName.isEmpty()) {
                return "Error (code 2): task has no package";
            }
            Object userId = fieldValue(task, "userId");
            int user = (userId instanceof Integer) ? (Integer) userId : 0;
            return user + ":" + packageName;
        } catch (Throwable t) {
            Throwable cause = (t.getCause() != null) ? t.getCause() : t;
            return "Error (code 1): " + cause;
        }
    }

    public interface ForegroundEmitter {
        boolean emit(String value);
    }

    private static boolean registerTaskStackListener(IBinder listener) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, "activity_task");
            if (service == null) {
                return false;
            }
            Field codeField = Class.forName("android.app.IActivityTaskManager$Stub")
                    .getDeclaredField("TRANSACTION_registerTaskStackListener");
            codeField.setAccessible(true);
            int code = codeField.getInt(null);
            data.writeInterfaceToken(service.getInterfaceDescriptor());
            data.writeStrongBinder(listener);
            if (!service.transact(code, data, reply, 0)) {
                return false;
            }
            reply.readException();
            return true;
        } catch (Throwable t) {
            logW("registerTaskStackListener failed: " + t);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public static void watchForegroundTask(ForegroundEmitter emitter) {
        watchForegroundTask(emitter, true);
    }

    public static void watchForegroundTask(ForegroundEmitter emitter, boolean keepalive) {
        final Object lock = new Object();
        final boolean[] dirty = { true };
        Binder listener = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code >= IBinder.FIRST_CALL_TRANSACTION) {
                    synchronized (lock) {
                        dirty[0] = true;
                        lock.notifyAll();
                    }
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        if (!registerTaskStackListener(listener)) {
            emitter.emit("Error (code 1): registerTaskStackListener rejected");
            return;
        }
        logD("Foreground watch started");

        String last = null;
        while (true) {
            synchronized (lock) {
                if (!dirty[0]) {
                    try {
                        lock.wait(FG_HEARTBEAT_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                dirty[0] = false;
            }
            try {
                Thread.sleep(FG_DEBOUNCE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (lock) {
                dirty[0] = false;
            }
            String current = foregroundTask();
            if (current.startsWith("Error (code 1)")) {
                emitter.emit(current);
                return;
            }
            if (current.startsWith("Error")) {
                continue;
            }
            boolean alive;
            if (current.equals(last)) {
                alive = !keepalive || emitter.emit(FG_KEEPALIVE);
            } else {
                last = current;
                alive = emitter.emit(current);
            }
            if (!alive) {
                logD("Foreground watch client disconnected");
                return;
            }
        }
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
                String result;
                if (command.startsWith(FW_UID_RULES_COMMAND + " ")) {
                    result = setUidFirewallRules(command.substring(FW_UID_RULES_COMMAND.length() + 1));
                } else if (FG_TASK_COMMAND.equals(command)) {
                    result = foregroundTask();
                } else {
                    result = "Error (code 22): unsupported command";
                }
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
