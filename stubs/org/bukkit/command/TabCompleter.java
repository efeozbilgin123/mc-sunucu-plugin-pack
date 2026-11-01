package org.bukkit.command; import java.util.List; public interface TabCompleter { List<String> onTabComplete(CommandSender s, Command c, String a, String[] args); }
