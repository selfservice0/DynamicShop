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
    public boolean hasPermission(Player p, String permission) {
        if (vaultPerms == null) return false;
        return vaultPerms.playerHas(p, permission);
    }

    public boolean grantPermission(Player p, String permission) {
        if (vaultPerms == null) return false;

        // Already owns it
        if (vaultPerms.playerHas(p, permission)) {
            return false;
        }

        // Attempt grant
        boolean result = vaultPerms.playerAdd(p, permission);

        if (!result) {
            Bukkit.getLogger().warning("[DynamicShop] Failed to grant permission '" +
                    permission + "' to " + p.getName());
        }

        return result;
    }
}
