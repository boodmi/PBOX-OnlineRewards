package org.example.onlinerewards;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OnlineRewardsPlugin extends JavaPlugin implements Listener {
    private File dataFile;
    private FileConfiguration dataConfig;
    private Set<Integer> triggeredThresholds = new HashSet<>();
    private Map<Integer, List<String>> thresholds = new TreeMap<>();
    private String hourlyAnnouncement;
    private boolean persistThresholds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        startHourlyTask();
        getCommand("onlinerewards").setExecutor((sender, command, label, args) -> {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadConfigValues();
                if (!persistThresholds) {
                    triggeredThresholds.clear();
                }
                sender.sendMessage("\u00a7a[OnlineRewards]\u00a7r Configuration reloaded.");
                return true;
            }
            sender.sendMessage("/" + label + " reload");
            return true;
        });
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, this::checkThresholds, 1L);
    }

    private void startHourlyTask() {
        long hourTicks = 20L * 60 * 60;
        new BukkitRunnable() {
            @Override
            public void run() {
                announceHourly();
            }
        }.runTaskTimer(this, hourTicks, hourTicks);
    }

    private void announceHourly() {
        int online = Bukkit.getOnlinePlayers().size();
        int next = getNextThreshold(online);
        int toGo = next > online ? next - online : 0;
        List<String> cmds = thresholds.getOrDefault(next, Collections.emptyList());
        String preview = String.join("; ", cmds);
        String msg = hourlyAnnouncement
                .replace("%online%", String.valueOf(online))
                .replace("%next_threshold%", String.valueOf(next))
                .replace("%to_go%", String.valueOf(toGo))
                .replace("%commands_preview%", preview);
        Bukkit.broadcastMessage(msg);
    }

    private void checkThresholds() {
        int online = Bukkit.getOnlinePlayers().size();
        int record = dataConfig.getInt("record-online", 0);
        if (online > record) {
            record = online;
            dataConfig.set("record-online", record);
            saveData();
        }
        for (Map.Entry<Integer, List<String>> entry : thresholds.entrySet()) {
            int threshold = entry.getKey();
            if (online >= threshold && !triggeredThresholds.contains(threshold)) {
                for (String cmd : entry.getValue()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                triggeredThresholds.add(threshold);
                if (persistThresholds) {
                    dataConfig.set("triggered-thresholds", new ArrayList<>(triggeredThresholds));
                    saveData();
                }
            }
        }
    }

    private int getNextThreshold(int current) {
        for (int t : thresholds.keySet()) {
            if (t > current) {
                return t;
            }
        }
        return current;
    }

    private void loadConfigValues() {
        thresholds.clear();
        for (String key : getConfig().getConfigurationSection("thresholds").getKeys(false)) {
            try {
                int value = Integer.parseInt(key);
                thresholds.put(value, getConfig().getStringList("thresholds." + key));
            } catch (NumberFormatException ignored) {
            }
        }
        hourlyAnnouncement = getConfig().getString("hourly-announcement", "");
        persistThresholds = getConfig().getBoolean("persist-thresholds", true);
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException ignored) {}
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        triggeredThresholds.clear();
        triggeredThresholds.addAll(dataConfig.getIntegerList("triggered-thresholds"));
    }

    private void saveData() {
        if (dataConfig == null || dataFile == null) return;
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
