package com.tmquan2508.rcon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectionWorker {
    private final Plugin plugin;
    private final String host;
    private final int port;
    private final int reconnectInterval;
    private final File latestLogFile;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private Thread workerThread;
    private Socket currentSocket;

    public ConnectionWorker(Plugin plugin) {
        this.plugin = plugin;
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        this.host = plugin.getConfig().getString("remote.host");
        this.port = plugin.getConfig().getInt("remote.port");
        this.reconnectInterval = plugin.getConfig().getInt("remote.reconnectInterval");
        String logPath = plugin.getConfig().getString("log.latestLogPath");
        this.latestLogFile = new File(plugin.getServer().getWorldContainer(), logPath);
    }

    public void start() {
        workerThread = new Thread(this::runWorkerLoop, "Rcon-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public void shutdown() {
        isRunning.set(false);
        try {
            if (currentSocket != null && !currentSocket.isClosed()) {
                currentSocket.close();
            }
        } catch (IOException e) {
            // Ignored on shutdown
        }
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    private void runWorkerLoop() {
        while (isRunning.get()) {
            try {
                connectAndProcess();
            } catch (Exception e) {
                if (isRunning.get()) {
                    plugin.getLogger().warning("Connection failed or was lost: " + e.getMessage());
                }
            }

            if (!isRunning.get()) break;

            try {
                plugin.getLogger().info("Will try to reconnect in " + reconnectInterval + " seconds...");
                Thread.sleep(reconnectInterval * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        plugin.getLogger().info("Connection worker has been stopped.");
    }

    private void connectAndProcess() throws IOException {
        LogAppender logAppender = null;
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        Thread commandReaderThread = null;

        try (Socket socket = new Socket(host, port)) {
            this.currentSocket = socket;
            plugin.getLogger().info("Successfully connected to " + host + ":" + port);

            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            sendEntireLog(out);

            logAppender = new LogAppender(out);
            logAppender.start();
            rootLogger.addAppender(logAppender);
            
            plugin.getLogger().info("Log streaming has started.");

            commandReaderThread = new Thread(() -> receiveAndExecuteCommands(in));
            commandReaderThread.setDaemon(true);
            commandReaderThread.start();

            try {
                commandReaderThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } finally {
            if (logAppender != null) {
                rootLogger.removeAppender(logAppender);
                logAppender.stop();
                plugin.getLogger().info("Log streaming has stopped.");
            }
            if (commandReaderThread != null && commandReaderThread.isAlive()) {
                commandReaderThread.interrupt();
            }
            this.currentSocket = null;
        }
    }

    private void sendEntireLog(PrintWriter out) {
        if (!latestLogFile.exists()) {
            plugin.getLogger().warning("Log file not found: " + latestLogFile.getAbsolutePath());
            return;
        }
        out.println("=== BEGIN LOG HISTORY ===");
        try (BufferedReader reader = new BufferedReader(new FileReader(latestLogFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read log file: " + e.getMessage());
        }
        out.println("=== END LOG HISTORY ===");
    }

    private void receiveAndExecuteCommands(BufferedReader in) {
        try {
            String line;
            while (isRunning.get() && (line = in.readLine()) != null) {
                final String command = line.trim();
                if (command.isEmpty()) continue;
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().info("Executing command from remote: " + command);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                });
            }
        } catch (IOException e) {
            // This error is expected when the netcat client disconnects.
        }
    }
}