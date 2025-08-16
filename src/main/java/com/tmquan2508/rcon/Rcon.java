package com.tmquan2508.rcon;

import org.bukkit.plugin.java.JavaPlugin;

public final class Rcon extends JavaPlugin {

    private ConnectionWorker connectionWorker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Rcon (TCP Bridge) is enabling...");
        connectionWorker = new ConnectionWorker(this);
        connectionWorker.start();
        getLogger().info("Rcon (TCP Bridge) has been enabled.");
    }

    @Override
    public void onDisable() {
        if (connectionWorker != null) {
            connectionWorker.shutdown();
        }
        getLogger().info("Rcon (TCP Bridge) has been disabled.");
    }
}