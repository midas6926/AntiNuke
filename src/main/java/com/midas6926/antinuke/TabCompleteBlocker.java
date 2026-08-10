package com.midas6926.antinuke;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PlayerCommandSendEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

/**
 * Removes tab-complete entries a player does not have permission for.
 * Uses LuckPerms if present, falls back to player.hasPermission(...) otherwise.
 */
public class TabCompleteBlocker implements Listener {

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms; // may be null
    private final boolean allowOps;
    private final String bypassPermission;

    public TabCompleteBlocker(JavaPlugin plugin, boolean allowOps, String bypassPermission) {
        this.plugin = plugin;
        this.allowOps = allowOps;
        this.bypassPermission = (bypassPermission == null ? "" : bypassPermission);
        LuckPerms lp = null;
        try {
            RegisteredServiceProvider<LuckPerms> rsp = plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (rsp != null) lp = rsp.getProvider();
        } catch (NoClassDefFoundError e) {
            // LuckPerms not available on the server; that's fine — we will fallback to Bukkit perms.
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Error while getting LuckPerms provider", ex);
        }
        this.luckPerms = lp;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();

        // bypass checks
        if (allowOps && player.isOp()) return;
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) return;
        if (isWhitelisted(player)) return;

        Set<String> commands = event.getCommands();
        Iterator<String> it = commands.iterator();
        while (it.hasNext()) {
            String label = it.next();
            // strip possible trailing spaces or arguments
            label = label.trim();

            String node = resolvePermissionNode(label);
            boolean allowed;
            if (node != null && !node.isEmpty()) {
                allowed = hasPermission(player, node);
            } else {
                allowed = hasPermission(player, "bukkit.command." + label.toLowerCase(Locale.ROOT));
            }

            if (!allowed) {
                it.remove();
            }
        }
    }

    private boolean hasPermission(Player player, String permission) {
        // Try LuckPerms cached check first for accurate LP semantics
        if (luckPerms != null) {
            try {
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    return user.getCachedData().getPermissionData(QueryOptions.nonContextual()).checkPermission(permission).asBoolean();
                }
                // If user is not loaded, fall back to Bukkit's permission check below.
            } catch (Throwable t) {
                // If something goes wrong with LuckPerms API, fallback to Bukkit perms.
                plugin.getLogger().log(Level.FINE, "LuckPerms check failed, falling back to Bukkit permissions", t);
            }
        }
        return player.hasPermission(permission);
    }

    private String resolvePermissionNode(String label) {
        // First consult overrides in config via the main plugin if available
        if (plugin instanceof AntiNuke) {
            String override = ((AntiNuke) plugin).resolvePermissionForCommand(label);
            if (override != null) return override;
        }

        // Try to lookup registered plugin command
        Command cmd = plugin.getServer().getPluginCommand(label);
        if (cmd != null) {
            String perm = cmd.getPermission();
            if (perm != null && !perm.trim().isEmpty()) return perm.trim();
        }

        return null;
    }

    private boolean isWhitelisted(Player player) {
        try {
            if (!plugin.getConfig().contains("whitelist")) return false;
            for (String entry : plugin.getConfig().getStringList("whitelist")) {
                if (entry == null) continue;
                String e = entry.trim();
                if (e.equalsIgnoreCase(player.getName()) || e.equalsIgnoreCase(player.getUniqueId().toString())) {
                    return true;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Error reading whitelist from config", t);
        }
        return false;
    }
}
