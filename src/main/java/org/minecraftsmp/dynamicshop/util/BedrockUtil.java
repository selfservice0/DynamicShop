package org.minecraftsmp.dynamicshop.util;

import org.bukkit.entity.Player;

/**
 * Utility to detect Bedrock players via Floodgate.
 * Uses reflection so Floodgate is a soft dependency — if not installed, always returns false.
 */
public class BedrockUtil {

    private static final java.util.Set<java.util.UUID> forcedBedrockPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static Boolean floodgateAvailable = null;
    private static java.lang.reflect.Method isFloodgatePlayerMethod = null;
    private static java.lang.reflect.Method getInstanceMethod = null;

    /**
     * Returns true if the player is a Bedrock player (connected through Geyser/Floodgate).
     * Returns false if Floodgate is not installed or the player is a Java player.
     */
    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        if (forcedBedrockPlayers.contains(player.getUniqueId())) return true;
        if (floodgateAvailable == null) {
            try {
                Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                getInstanceMethod = floodgateApi.getMethod("getInstance");
                isFloodgatePlayerMethod = floodgateApi.getMethod("isFloodgatePlayer", java.util.UUID.class);
                floodgateAvailable = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                floodgateAvailable = false;
            }
        }

        if (!floodgateAvailable) {
            return false;
        }

        try {
            Object api = getInstanceMethod.invoke(null);
            return (boolean) isFloodgatePlayerMethod.invoke(api, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    public static void setForceBedrock(Player player, boolean force) {
        if (force) {
            forcedBedrockPlayers.add(player.getUniqueId());
        } else {
            forcedBedrockPlayers.remove(player.getUniqueId());
        }
    }

    public static boolean isForcedBedrock(Player player) {
        return forcedBedrockPlayers.contains(player.getUniqueId());
    }
}
