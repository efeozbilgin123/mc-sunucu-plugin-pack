package org.bukkit.command; import net.kyori.adventure.text.Component; public interface CommandSender { void sendMessage(Component c); boolean hasPermission(String p); String getName(); }
