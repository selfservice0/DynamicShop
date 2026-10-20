package org.minecraftsmp.dynamicshop.managers;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import org.minecraftsmp.dynamicshop.DynamicShop;

/**
 * Handles granting permission nodes to players using Vault.
 *
 * This is used for "permission shop" items such as:
 *   /shopadmin add perm <price> <permission.node>
 *
 * When the player buys it, we check if they have it, then grant it permanently.
 */
public class PermissionsManager {

    private final DynamicShop plugin;
    private Permission vaultPerms;

    public PermissionsManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------
    // INIT + SETUP
    // -------------------------------------------------------------
    public void init() {
        if (!setupVault()) {
            Bukkit.getLogger().warning("[DynamicShop] No Permission provider found via Vault!");
        }
    }

    private boolean setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        var rsp = plugin.getServer()
                .getServicesManager()
                .getRegistration(Permission.class);

        if (rsp == null) return false;

        vaultPerms = rsp.getProvider();
        return vaultPerms != null;
    }

    // -------------------------------------------------------------
    // PERMISSION LOGIC
    // -------------------------------------------------------------
    public boolean hasPermission(Player p, String permission, String world) {
        String w = (world != null && !world.isBlank()) ? world : null;
        if (w == null) {
            return p.hasPermission(permission)
                    || (vaultPerms != null && vaultPerms.playerHas((String) null, p, permission));
        }
        return vaultPerms != null && vaultPerms.playerHas(w, p, permission);
    }

    public boolean grantPermission(Player p, String permission, String world) {
        if (vaultPerms == null) return false;
        String w = (world != null && !world.isBlank()) ? world : null;

        // Already owns it
        if (hasPermission(p, permission, world)) {
            return false;
        }

        // Attempt grant
        boolean result = vaultPerms.playerAdd(w, p, permission);

        if (!result) {
            Bukkit.getLogger().warning("[DynamicShop] Failed to grant permission '" +
                    permission + "' to " + p.getName() +
                    (w != null ? " in world '" + w + "'" : " globally"));
        }

        return result;
    }

    // -------------------------------------------------------------
    // GROUP LOGIC
    // -------------------------------------------------------------

    /**
     * Returns true if the player is a member of the given Vault group.
     * @param world null or blank = global (all worlds)
     */
    public boolean isInGroup(Player p, String group, String world) {
        if (vaultPerms == null) return false;
        String w = (world != null && !world.isBlank()) ? world : null;
        if (vaultPerms.playerInGroup(w, p, group)) return true;
        String[] groups = vaultPerms.getPlayerGroups(w, p);
        if (groups != null) for (String membership : groups) {
            if (group.equalsIgnoreCase(membership)) return true;
        }
        return false;
    }

    /**
     * Adds the player to the given Vault group.
     * @param world null or blank = global (all worlds)
     * Returns false if already in group or call fails.
     */
    public boolean grantGroup(Player p, String group, String world) {
        if (vaultPerms == null) return false;
        String w = (world != null && !world.isBlank()) ? world : null;

        if (isInGroup(p, group, world)) {
            return false; // already a member
        }

        boolean result = vaultPerms.playerAddGroup(w, p, group);

        if (!result) {
            Bukkit.getLogger().warning("[DynamicShop] Failed to add '" +
                    p.getName() + "' to group '" + group +
                    (w != null ? "' in world '" + w + "'" : "' globally"));
        }

        return result;
    }
}
