package com.midas6926.antinuke;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for AntiNuke.
 */
public class AntiNuke extends JavaPlugin implements Listener {

    private List<String> blockedCommands;
    private List<String> whitelist;
    private String blockedMessage;

    private boolean tabAllowOps;
    private String tabBypassPerm;
    private Map<String, String> commandPermissionOverrides;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        // register listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new TabCompleteBlocker(this, tabAllowOps, tabBypassPerm), this);

        getLogger().info("AntiNuke enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiNuke disabled.");
    }

    public void reloadSettings() {
        reloadConfig();
        blockedCommands = getConfig().getStringList("blocked-commands");
        for (int i = 0; i < blockedCommands.size(); i++) {
            blockedCommands.set(i, blockedCommands.get(i).toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }
        whitelist = getConfig().getStringList("whitelist");
        blockedMessage = getConfig().getString("blocked-message", "&cYou are not allowed to use that command.");

        tabAllowOps = getConfig().getBoolean("tab-allow-ops", false);
        tabBypassPerm = getConfig().getString("tab-bypass-perm", "antinuke.bypass.tab");
        commandPermissionOverrides = getConfig().getConfigurationSection("command-permission-overrides") != null
                ? getConfig().getConfigurationSection("command-permission-overrides").getValues(false)
                : Map.of();
    }

    private boolean isWhitelisted(Player player) {
        if (whitelist == null || whitelist.isEmpty()) return false;
        String name = player.getName();
        String uuid = player.getUniqueId().toString();
        for (String entry : whitelist) {
            if (entry == null) continue;
            String e = entry.trim();
            if (e.equalsIgnoreCase(name) || e.equalsIgnoreCase(uuid)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer() == null) return;
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message == null || message.isEmpty()) return;

        String lower = message.toLowerCase(Locale.ROOT).trim();
        String base = lower.split("\\s+", 2)[0].replaceFirst("^/", "");

        for (String cmd : blockedCommands) {
            if (cmd.equalsIgnoreCase(base)) {
                // allow whitelisted players or bypass permission
                if (isWhitelisted(player)) return;
                if (tabBypassPerm != null && !tabBypassPerm.isEmpty() && player.hasPermission(tabBypassPerm)) return;
                // allow ops if configured
                if (tabAllowOps && player.isOp()) return;

                // cancel and notify
                event.setCancelled(true);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', blockedMessage));
                Bukkit.getLogger().info(String.format("Blocked command '%s' from player %s", message, player.getName()));
                return;
            }
        }
    }

    /**
     * Helper to resolve permission for a command label, taking overrides into account.
     */
    public String resolvePermissionForCommand(String label) {
        if (label == null) return null;
        String lower = label.toLowerCase(Locale.ROOT);
        if (commandPermissionOverrides != null && commandPermissionOverrides.containsKey(lower)) {
            return commandPermissionOverrides.get(lower);
        }
        Command cmd = getServer().getPluginCommand(label);
        if (cmd != null) {
            String p = cmd.getPermission();
            if (p != null && !p.trim().isEmpty()) return p.trim();
        }
        return null;
    }
}
