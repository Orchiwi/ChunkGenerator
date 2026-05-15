package fr.horizonsmp.chunkGenerator.permission;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public final class PermissionService {

    public static final String ADMIN = "chunkgenerator.admin";
    public static final String COMMAND_START = "chunkgenerator.command.start";
    public static final String COMMAND_STOP = "chunkgenerator.command.stop";
    public static final String COMMAND_RESUME = "chunkgenerator.command.resume";
    public static final String COMMAND_CANCEL = "chunkgenerator.command.cancel";
    public static final String COMMAND_TRIM = "chunkgenerator.command.trim";
    public static final String COMMAND_STATUS = "chunkgenerator.command.status";
    public static final String COMMAND_LIST = "chunkgenerator.command.list";
    public static final String COMMAND_RELOAD = "chunkgenerator.command.reload";
    public static final String BOSSBAR = "chunkgenerator.bossbar";

    private final Logger logger;

    public PermissionService(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    public void announceProvider() {
        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms != null && luckPerms.isEnabled()) {
            logger.info("LuckPerms detected; permissions will be served through it.");
        } else {
            logger.info("LuckPerms not detected; permissions fall back to operator status.");
        }
    }

    public boolean canStart(CommandSender sender) {
        return sender.hasPermission(COMMAND_START) || sender.hasPermission(ADMIN);
    }

    public boolean canStop(CommandSender sender) {
        return sender.hasPermission(COMMAND_STOP) || sender.hasPermission(ADMIN);
    }

    public boolean canResume(CommandSender sender) {
        return sender.hasPermission(COMMAND_RESUME) || sender.hasPermission(ADMIN);
    }

    public boolean canCancel(CommandSender sender) {
        return sender.hasPermission(COMMAND_CANCEL) || sender.hasPermission(ADMIN);
    }

    public boolean canTrim(CommandSender sender) {
        return sender.hasPermission(COMMAND_TRIM) || sender.hasPermission(ADMIN);
    }

    public boolean canStatus(CommandSender sender) {
        return sender.hasPermission(COMMAND_STATUS) || sender.hasPermission(ADMIN);
    }

    public boolean canList(CommandSender sender) {
        return sender.hasPermission(COMMAND_LIST) || sender.hasPermission(ADMIN);
    }

    public boolean canReload(CommandSender sender) {
        return sender.hasPermission(COMMAND_RELOAD) || sender.hasPermission(ADMIN);
    }

    public boolean canUseAnyCommand(CommandSender sender) {
        return canStart(sender) || canStop(sender) || canResume(sender) || canCancel(sender)
                || canTrim(sender) || canStatus(sender) || canList(sender) || canReload(sender);
    }
}
