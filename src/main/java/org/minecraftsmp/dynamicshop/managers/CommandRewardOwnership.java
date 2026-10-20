package org.minecraftsmp.dynamicshop.managers;

import java.util.Locale;

/** Ownership implied by a permanent LuckPerms grant to the purchasing player. */
record CommandRewardOwnership(String permission, String group, String world) {
    static CommandRewardOwnership parse(String command) {
        if (command == null || command.isBlank()) return null;
        String[] args = command.trim().replaceFirst("^/", "").split("\\s+");
        if (args.length < 6) return null;
        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.startsWith("luckperms:")) root = root.substring("luckperms:".length());
        if (!root.equals("lp") && !root.equals("luckperms")) return null;
        if (!args[1].equalsIgnoreCase("user")
                || !(args[2].equals("{player}") || args[2].equals("{uuid}"))) {
            return null;
        }

        boolean permission = args[3].equalsIgnoreCase("permission") && args[4].equalsIgnoreCase("set");
        boolean group = args[3].equalsIgnoreCase("parent") && args[4].equalsIgnoreCase("add");
        if (!permission && !group) return null;
        int context = 6;
        if (permission && args.length > context && args[context].equalsIgnoreCase("true")) context++;

        String world = null;
        for (int i = context; i < args.length; i++) {
            // Vault can represent a world scope only. Never discard other contexts or false grants.
            if (world != null || !args[i].startsWith("world=") || args[i].length() == 6) return null;
            world = args[i].substring(6);
        }
        return new CommandRewardOwnership(permission ? args[5] : null, group ? args[5] : null, world);
    }
}
