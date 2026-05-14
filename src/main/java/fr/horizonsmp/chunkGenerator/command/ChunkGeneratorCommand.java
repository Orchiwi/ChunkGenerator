package fr.horizonsmp.chunkGenerator.command;

import fr.horizonsmp.chunkGenerator.ChunkGenerator;
import fr.horizonsmp.chunkGenerator.i18n.Messages;
import fr.horizonsmp.chunkGenerator.job.GenerationJob;
import fr.horizonsmp.chunkGenerator.job.JobManager;
import fr.horizonsmp.chunkGenerator.job.JobSnapshot;
import fr.horizonsmp.chunkGenerator.monitoring.StatsDisplay;
import fr.horizonsmp.chunkGenerator.permission.PermissionService;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;
import fr.horizonsmp.chunkGenerator.shape.ZoneShape;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ChunkGeneratorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBS = List.of("start", "stop", "cancel", "status", "list", "reload", "help");
    private static final List<String> SHAPES = List.of("square", "circle", "rectangle");
    private static final int MAX_HALF_BLOCKS = 50_000;

    private final ChunkGenerator plugin;
    private final JobManager jobs;
    private final Messages messages;
    private final PermissionService permissions;

    public ChunkGeneratorCommand(ChunkGenerator plugin) {
        this.plugin = plugin;
        this.jobs = plugin.jobManager();
        this.messages = plugin.messages();
        this.permissions = plugin.permissionService();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {
        if (!permissions.canUseAnyCommand(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> handleStart(sender, label, args);
            case "stop" -> handleStop(sender, label, args);
            case "cancel" -> handleCancel(sender, label, args);
            case "status" -> handleStatus(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "help" -> sendHelp(sender, label);
            default -> sender.sendMessage(messages.get("command.unknown-subcommand",
                    Map.of("label", label)));
        }
        return true;
    }

    private void handleStart(CommandSender sender, String label, String[] args) {
        if (!permissions.canStart(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(messages.get("command.start.usage", Map.of("label", label)));
            return;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(messages.get("command.start.unknown-world",
                    Map.of("world", worldName)));
            return;
        }
        Optional<ZoneShape> shapeOpt = ZoneShape.fromString(args[2]);
        if (shapeOpt.isEmpty()) {
            sender.sendMessage(messages.get("command.start.unknown-shape",
                    Map.of("shape", args[2])));
            return;
        }
        ZoneShape shape = shapeOpt.get();

        ParsedZone parsed = parseZone(sender, label, shape, world, args);
        if (parsed == null) {
            return;
        }

        UUID launcherUuid = (sender instanceof Player p) ? p.getUniqueId() : null;
        JobManager.StartResult result = jobs.startJob(world, parsed.zone(), launcherUuid);
        if (!result.ok()) {
            if ("already-running".equals(result.error())) {
                sender.sendMessage(messages.get("command.start.already-running",
                        Map.of("world", worldName, "label", label)));
            } else if ("zone-empty".equals(result.error())) {
                sender.sendMessage(messages.get("command.start.invalid-size",
                        Map.of("value", "0")));
            } else {
                sender.sendMessage(messages.get("command.reload-failed",
                        Map.of("error", String.valueOf(result.error()))));
            }
            return;
        }

        GenerationJob job = result.job();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", worldName);
        placeholders.put("shape", shape.name().toLowerCase(Locale.ROOT));
        placeholders.put("size", parsed.zone().halfWidthBlocks() == parsed.zone().halfLengthBlocks()
                ? String.valueOf(parsed.zone().halfWidthBlocks())
                : parsed.zone().halfWidthBlocks() + "x" + parsed.zone().halfLengthBlocks());
        placeholders.put("centerX", String.valueOf(parsed.zone().centerBlockX()));
        placeholders.put("centerZ", String.valueOf(parsed.zone().centerBlockZ()));
        placeholders.put("total", String.valueOf(job.totalChunks()));
        sender.sendMessage(messages.get("command.start.started", placeholders));

        if (launcherUuid != null) {
            plugin.statsDisplay().onPlayerJoin((Player) sender);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.statsDisplay().onPlayerJoin(p);
        }
    }

    private ParsedZone parseZone(CommandSender sender, String label, ZoneShape shape, World world, String[] args) {
        int centerArgsOffset;
        int halfW;
        int halfL;
        try {
            if (shape == ZoneShape.RECTANGLE) {
                if (args.length < 5) {
                    sender.sendMessage(messages.get("command.start.usage-rectangle",
                            Map.of("label", label)));
                    return null;
                }
                halfW = Integer.parseInt(args[3]);
                halfL = Integer.parseInt(args[4]);
                centerArgsOffset = 5;
            } else {
                int radius = Integer.parseInt(args[3]);
                halfW = radius;
                halfL = radius;
                centerArgsOffset = 4;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.get("command.start.invalid-size",
                    Map.of("value", e.getMessage())));
            return null;
        }
        if (halfW <= 0 || halfL <= 0) {
            sender.sendMessage(messages.get("command.start.invalid-size",
                    Map.of("value", halfW + "/" + halfL)));
            return null;
        }
        if (halfW > MAX_HALF_BLOCKS || halfL > MAX_HALF_BLOCKS) {
            sender.sendMessage(messages.get("command.start.size-too-large",
                    Map.of("max", String.valueOf(MAX_HALF_BLOCKS))));
            return null;
        }

        int centerX;
        int centerZ;
        if (args.length >= centerArgsOffset + 2) {
            try {
                centerX = Integer.parseInt(args[centerArgsOffset]);
                centerZ = Integer.parseInt(args[centerArgsOffset + 1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(messages.get("command.start.invalid-coords"));
                return null;
            }
        } else {
            Location spawn = world.getSpawnLocation();
            centerX = spawn.getBlockX();
            centerZ = spawn.getBlockZ();
        }

        return new ParsedZone(new ZoneDefinition(shape, centerX, centerZ, halfW, halfL));
    }

    private void handleStop(CommandSender sender, String label, String[] args) {
        if (!permissions.canStop(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("command.stop.usage", Map.of("label", label)));
            return;
        }
        String worldName = args[1];
        boolean stopped = jobs.stop(worldName);
        if (!stopped) {
            sender.sendMessage(messages.get("command.stop.not-running",
                    Map.of("world", worldName)));
            return;
        }
        sender.sendMessage(messages.get("command.stop.stopped",
                Map.of("world", worldName)));
    }

    private void handleCancel(CommandSender sender, String label, String[] args) {
        if (!permissions.canCancel(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("command.cancel.usage", Map.of("label", label)));
            return;
        }
        String worldName = args[1];
        boolean had = jobs.cancel(worldName);
        if (!had) {
            sender.sendMessage(messages.get("command.cancel.no-job",
                    Map.of("world", worldName)));
            return;
        }
        sender.sendMessage(messages.get("command.cancel.cancelled",
                Map.of("world", worldName)));
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!permissions.canStatus(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        if (args.length >= 2) {
            String worldName = args[1];
            Optional<GenerationJob> job = jobs.get(worldName);
            if (job.isEmpty()) {
                sender.sendMessage(messages.get("command.status.no-job",
                        Map.of("world", worldName)));
                return;
            }
            sendStatusLines(sender, jobs.snapshotOf(job.get()));
            return;
        }
        List<GenerationJob> all = jobs.jobs();
        if (all.isEmpty()) {
            sender.sendMessage(messages.get("command.status.no-jobs"));
            return;
        }
        for (GenerationJob job : all) {
            sendStatusLines(sender, jobs.snapshotOf(job));
        }
    }

    private void sendStatusLines(CommandSender sender, JobSnapshot snap) {
        Map<String, String> map = new HashMap<>();
        map.put("world", snap.worldName());
        map.put("state", snap.status().name());
        map.put("shape", snap.shape().name().toLowerCase(Locale.ROOT));
        map.put("centerX", String.valueOf(snap.centerBlockX()));
        map.put("centerZ", String.valueOf(snap.centerBlockZ()));
        map.put("sizeX", String.valueOf(snap.halfWidthBlocks()));
        map.put("sizeZ", String.valueOf(snap.halfLengthBlocks()));
        map.put("percent", StatsDisplay.formatPercent(snap.progressPercent()));
        map.put("done", String.valueOf(snap.chunksDone()));
        map.put("total", String.valueOf(snap.totalChunks()));
        map.put("speed", StatsDisplay.formatSpeed(snap.chunksPerSecond()));
        map.put("eta", StatsDisplay.formatEta(snap.etaSeconds()));
        map.put("tps", String.format(Locale.ROOT, "%.1f", snap.tps()));
        map.put("cpu", String.format(Locale.ROOT, "%.0f", snap.cpuPercent()));
        map.put("ram", snap.usedRamMb() + "/" + snap.maxRamMb());
        map.put("inflight", String.valueOf(snap.inflight()));
        map.put("target", String.valueOf(snap.inflightTarget()));

        sender.sendMessage(messages.get("command.status.header"));
        sender.sendMessage(messages.get("command.status.line-world", map));
        sender.sendMessage(messages.get("command.status.line-state", map));
        sender.sendMessage(messages.get("command.status.line-shape", map));
        sender.sendMessage(messages.get("command.status.line-progress", map));
        sender.sendMessage(messages.get("command.status.line-speed", map));
        sender.sendMessage(messages.get("command.status.line-perf", map));
    }

    private void handleList(CommandSender sender) {
        if (!permissions.canList(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        List<GenerationJob> all = jobs.jobs();
        if (all.isEmpty()) {
            sender.sendMessage(messages.get("command.list.empty"));
            return;
        }
        sender.sendMessage(messages.get("command.list.header"));
        for (GenerationJob job : all) {
            JobSnapshot snap = jobs.snapshotOf(job);
            Map<String, String> map = new HashMap<>();
            map.put("world", snap.worldName());
            map.put("state", snap.status().name());
            map.put("percent", StatsDisplay.formatPercent(snap.progressPercent()));
            map.put("speed", StatsDisplay.formatSpeed(snap.chunksPerSecond()));
            sender.sendMessage(messages.get("command.list.entry", map));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!permissions.canReload(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        try {
            plugin.reloadAll();
            sender.sendMessage(messages.get("command.reload-success"));
        } catch (RuntimeException e) {
            sender.sendMessage(messages.get("command.reload-failed",
                    Map.of("error", String.valueOf(e.getMessage()))));
            plugin.getLogger().warning("Reload failed: " + e.getMessage());
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        Map<String, String> placeholders = Map.of("label", label);
        sender.sendMessage(messages.get("command.help.header"));
        sender.sendMessage(messages.get("command.help.start", placeholders));
        sender.sendMessage(messages.get("command.help.stop", placeholders));
        sender.sendMessage(messages.get("command.help.cancel", placeholders));
        sender.sendMessage(messages.get("command.help.status", placeholders));
        sender.sendMessage(messages.get("command.help.list", placeholders));
        sender.sendMessage(messages.get("command.help.reload", placeholders));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        if (!permissions.canUseAnyCommand(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return startsWith(ROOT_SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("start") || sub.equals("stop")
                || sub.equals("cancel") || sub.equals("status"))) {
            List<String> worldNames = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                worldNames.add(w.getName());
            }
            return startsWith(worldNames, args[1]);
        }
        if (args.length == 3 && sub.equals("start")) {
            return startsWith(SHAPES, args[2]);
        }
        return List.of();
    }

    private static List<String> startsWith(List<String> source, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }

    private record ParsedZone(ZoneDefinition zone) {
    }
}
