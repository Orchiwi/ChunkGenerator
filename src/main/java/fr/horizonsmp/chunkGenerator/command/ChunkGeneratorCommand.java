package fr.horizonsmp.chunkGenerator.command;

import fr.horizonsmp.chunkGenerator.ChunkGenerator;
import fr.horizonsmp.chunkGenerator.i18n.Messages;
import fr.horizonsmp.chunkGenerator.job.GenerationJob;
import fr.horizonsmp.chunkGenerator.job.JobManager;
import fr.horizonsmp.chunkGenerator.job.JobSnapshot;
import fr.horizonsmp.chunkGenerator.monitoring.StatsDisplay;
import fr.horizonsmp.chunkGenerator.permission.PermissionService;
import fr.horizonsmp.chunkGenerator.shape.TraversalPattern;
import fr.horizonsmp.chunkGenerator.shape.ZoneDefinition;
import fr.horizonsmp.chunkGenerator.shape.ZoneShape;
import fr.horizonsmp.chunkGenerator.trim.TrimJob;
import fr.horizonsmp.chunkGenerator.trim.TrimManager;
import fr.horizonsmp.chunkGenerator.trim.TrimPlan;
import fr.horizonsmp.chunkGenerator.trim.TrimSnapshot;
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

    private static final List<String> ROOT_SUBS = List.of("start", "stop", "resume", "cancel", "trim", "status", "list", "reload", "help");
    private static final List<String> SHAPES = List.of("square", "circle", "rectangle");
    private static final List<String> PATTERNS = List.of("center", "edge", "north", "south", "east", "west");
    private static final List<String> TRIM_TAIL_HINTS = List.of("--confirm");
    private static final int MAX_HALF_BLOCKS = 50_000;
    private static final UUID CONSOLE_PREVIEW_KEY = new UUID(0L, 0L);

    private final ChunkGenerator plugin;
    private final JobManager jobs;
    private final TrimManager trimManager;
    private final Messages messages;
    private final PermissionService permissions;

    public ChunkGeneratorCommand(ChunkGenerator plugin) {
        this.plugin = plugin;
        this.jobs = plugin.jobManager();
        this.trimManager = plugin.trimManager();
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
            case "resume" -> handleResume(sender, label, args);
            case "cancel" -> handleCancel(sender, label, args);
            case "trim" -> handleTrim(sender, label, args);
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
        JobManager.StartResult result = jobs.startJob(world, parsed.zone(), parsed.pattern(), launcherUuid);
        if (!result.ok()) {
            if ("already-running".equals(result.error())) {
                sender.sendMessage(messages.get("command.start.already-running",
                        Map.of("world", worldName, "label", label)));
            } else if ("zone-empty".equals(result.error())) {
                sender.sendMessage(messages.get("command.start.invalid-size",
                        Map.of("value", "0")));
            } else if ("trim-active".equals(result.error())) {
                sender.sendMessage(messages.get("command.start.refused-trim-active",
                        Map.of("world", worldName)));
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
        placeholders.put("pattern", parsed.pattern().name().toLowerCase(Locale.ROOT));
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
        int tailOffset;
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
                tailOffset = 5;
            } else {
                int radius = Integer.parseInt(args[3]);
                halfW = radius;
                halfL = radius;
                tailOffset = 4;
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

        List<String> tail = new ArrayList<>();
        for (int i = tailOffset; i < args.length; i++) {
            tail.add(args[i]);
        }

        TraversalPattern pattern = TraversalPattern.CENTER;
        if (!tail.isEmpty() && !isNumeric(tail.get(tail.size() - 1))) {
            String last = tail.get(tail.size() - 1);
            Optional<TraversalPattern> p = TraversalPattern.fromString(last);
            if (p.isEmpty()) {
                sender.sendMessage(messages.get("command.start.unknown-pattern",
                        Map.of("pattern", last)));
                return null;
            }
            pattern = p.get();
            tail.remove(tail.size() - 1);
        }

        int centerX;
        int centerZ;
        if (tail.isEmpty()) {
            Location spawn = world.getSpawnLocation();
            centerX = spawn.getBlockX();
            centerZ = spawn.getBlockZ();
        } else if (tail.size() == 2) {
            try {
                centerX = Integer.parseInt(tail.get(0));
                centerZ = Integer.parseInt(tail.get(1));
            } catch (NumberFormatException e) {
                sender.sendMessage(messages.get("command.start.invalid-coords"));
                return null;
            }
        } else {
            sender.sendMessage(messages.get(shape == ZoneShape.RECTANGLE
                            ? "command.start.usage-rectangle"
                            : "command.start.usage",
                    Map.of("label", label)));
            return null;
        }

        return new ParsedZone(new ZoneDefinition(shape, centerX, centerZ, halfW, halfL), pattern);
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        int start = s.charAt(0) == '-' ? 1 : 0;
        if (start == s.length()) return false;
        for (int i = start; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
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

    private void handleResume(CommandSender sender, String label, String[] args) {
        if (!permissions.canResume(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("command.resume.usage", Map.of("label", label)));
            return;
        }
        String worldName = args[1];
        JobManager.ResumeResult result = jobs.resume(worldName);
        if (!result.ok()) {
            String key = switch (result.error()) {
                case "already-running" -> "command.resume.already-running";
                case "terminal" -> "command.resume.completed";
                default -> "command.resume.no-job";
            };
            sender.sendMessage(messages.get(key, Map.of("world", worldName, "label", label)));
            return;
        }
        sender.sendMessage(messages.get("command.resume.resumed",
                Map.of("world", worldName)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.statsDisplay().onPlayerJoin(p);
        }
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
        boolean cancelledJob = jobs.cancel(worldName);
        boolean cancelledTrim = trimManager.cancel(worldName);
        if (!cancelledJob && !cancelledTrim) {
            sender.sendMessage(messages.get("command.cancel.no-job",
                    Map.of("world", worldName)));
            return;
        }
        sender.sendMessage(messages.get("command.cancel.cancelled",
                Map.of("world", worldName)));
    }

    private void handleTrim(CommandSender sender, String label, String[] args) {
        if (!permissions.canTrim(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(messages.get("command.trim.usage", Map.of("label", label)));
            return;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(messages.get("command.trim.unknown-world",
                    Map.of("world", worldName)));
            return;
        }
        Optional<ZoneShape> shapeOpt = ZoneShape.fromString(args[2]);
        if (shapeOpt.isEmpty()) {
            sender.sendMessage(messages.get("command.trim.unknown-shape",
                    Map.of("shape", args[2])));
            return;
        }
        ZoneShape shape = shapeOpt.get();

        int halfW;
        int halfL;
        int tailOffset;
        try {
            if (shape == ZoneShape.RECTANGLE) {
                if (args.length < 5) {
                    sender.sendMessage(messages.get("command.trim.usage-rectangle",
                            Map.of("label", label)));
                    return;
                }
                halfW = Integer.parseInt(args[3]);
                halfL = Integer.parseInt(args[4]);
                tailOffset = 5;
            } else {
                int radius = Integer.parseInt(args[3]);
                halfW = radius;
                halfL = radius;
                tailOffset = 4;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.get("command.trim.invalid-size",
                    Map.of("value", e.getMessage())));
            return;
        }
        if (halfW <= 0 || halfL <= 0) {
            sender.sendMessage(messages.get("command.trim.invalid-size",
                    Map.of("value", halfW + "/" + halfL)));
            return;
        }
        if (halfW > MAX_HALF_BLOCKS || halfL > MAX_HALF_BLOCKS) {
            sender.sendMessage(messages.get("command.trim.size-too-large",
                    Map.of("max", String.valueOf(MAX_HALF_BLOCKS))));
            return;
        }

        List<String> tail = new ArrayList<>();
        boolean confirm = false;
        for (int i = tailOffset; i < args.length; i++) {
            if ("--confirm".equalsIgnoreCase(args[i])) {
                confirm = true;
            } else {
                tail.add(args[i]);
            }
        }

        int centerX;
        int centerZ;
        if (tail.isEmpty()) {
            Location spawn = world.getSpawnLocation();
            centerX = spawn.getBlockX();
            centerZ = spawn.getBlockZ();
        } else if (tail.size() == 2) {
            try {
                centerX = Integer.parseInt(tail.get(0));
                centerZ = Integer.parseInt(tail.get(1));
            } catch (NumberFormatException e) {
                sender.sendMessage(messages.get("command.trim.invalid-coords"));
                return;
            }
        } else {
            sender.sendMessage(messages.get(shape == ZoneShape.RECTANGLE
                            ? "command.trim.usage-rectangle"
                            : "command.trim.usage",
                    Map.of("label", label)));
            return;
        }

        ZoneDefinition zone = new ZoneDefinition(shape, centerX, centerZ, halfW, halfL);
        UUID senderKey = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_PREVIEW_KEY;

        if (jobs.get(worldName).isPresent()) {
            sender.sendMessage(messages.get("command.trim.refused-generation-active",
                    Map.of("world", worldName)));
            return;
        }
        if (trimManager.isActive(worldName)) {
            sender.sendMessage(messages.get("command.trim.refused-trim-active",
                    Map.of("world", worldName)));
            return;
        }

        if (!confirm) {
            TrimManager.PreviewResult preview = trimManager.preview(world, zone);
            if (!preview.ok()) {
                sender.sendMessage(messages.get("command.trim.preview-failed",
                        Map.of("error", String.valueOf(preview.error()))));
                return;
            }
            TrimPlan plan = preview.plan();
            if (plan.chunksAffected() == 0L && plan.regionFilesAffected() == 0L) {
                sender.sendMessage(messages.get("command.trim.preview-empty",
                        Map.of("world", worldName)));
                return;
            }
            trimManager.cacheForSender(senderKey, plan);
            Map<String, String> map = new HashMap<>();
            map.put("world", worldName);
            map.put("count", String.valueOf(plan.chunksAffected()));
            map.put("regions", String.valueOf(plan.regionFilesAffected()));
            map.put("label", label);
            sender.sendMessage(messages.get("command.trim.preview", map));
            return;
        }

        Optional<TrimPlan> cached = trimManager.takeCachedPlan(senderKey, worldName, zone);
        if (cached.isEmpty()) {
            sender.sendMessage(messages.get("command.trim.missing-confirm",
                    Map.of("world", worldName, "label", label)));
            return;
        }
        TrimManager.StartResult result = trimManager.start(world, cached.get(),
                (sender instanceof Player p) ? p.getUniqueId() : null);
        if (!result.ok()) {
            sender.sendMessage(messages.get("command.trim.refused-trim-active",
                    Map.of("world", worldName)));
            return;
        }
        Map<String, String> map = new HashMap<>();
        map.put("world", worldName);
        map.put("count", String.valueOf(cached.get().chunksAffected()));
        sender.sendMessage(messages.get("command.trim.started", map));
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!permissions.canStatus(sender)) {
            sender.sendMessage(messages.get("command.no-permission"));
            return;
        }
        if (args.length >= 2) {
            String worldName = args[1];
            Optional<GenerationJob> job = jobs.get(worldName);
            Optional<TrimJob> trim = trimManager.get(worldName);
            if (job.isEmpty() && trim.isEmpty()) {
                sender.sendMessage(messages.get("command.status.no-job",
                        Map.of("world", worldName)));
                return;
            }
            job.ifPresent(g -> sendStatusLines(sender, jobs.snapshotOf(g)));
            trim.ifPresent(t -> sendTrimStatusLines(sender, t.snapshot()));
            return;
        }
        List<GenerationJob> allJobs = jobs.jobs();
        List<TrimJob> allTrims = trimManager.trims();
        if (allJobs.isEmpty() && allTrims.isEmpty()) {
            sender.sendMessage(messages.get("command.status.no-jobs"));
            return;
        }
        for (GenerationJob job : allJobs) {
            sendStatusLines(sender, jobs.snapshotOf(job));
        }
        for (TrimJob job : allTrims) {
            sendTrimStatusLines(sender, job.snapshot());
        }
    }

    private void sendTrimStatusLines(CommandSender sender, TrimSnapshot snap) {
        Map<String, String> map = new HashMap<>();
        map.put("world", snap.worldName());
        map.put("state", snap.status().name());
        map.put("processed", String.valueOf(snap.chunksProcessed()));
        map.put("total", String.valueOf(snap.totalChunks()));
        map.put("percent", StatsDisplay.formatPercent(snap.progressPercent()));
        map.put("regions", String.valueOf(snap.regionFilesAffected()));
        sender.sendMessage(messages.get("command.trim.status-header"));
        sender.sendMessage(messages.get("command.trim.status-world", map));
        sender.sendMessage(messages.get("command.trim.status-state", map));
        sender.sendMessage(messages.get("command.trim.status-progress", map));
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
        List<GenerationJob> allJobs = jobs.jobs();
        List<TrimJob> allTrims = trimManager.trims();
        if (allJobs.isEmpty() && allTrims.isEmpty()) {
            sender.sendMessage(messages.get("command.list.empty"));
            return;
        }
        sender.sendMessage(messages.get("command.list.header"));
        for (GenerationJob job : allJobs) {
            JobSnapshot snap = jobs.snapshotOf(job);
            Map<String, String> map = new HashMap<>();
            map.put("world", snap.worldName());
            map.put("state", snap.status().name());
            map.put("percent", StatsDisplay.formatPercent(snap.progressPercent()));
            map.put("speed", StatsDisplay.formatSpeed(snap.chunksPerSecond()));
            sender.sendMessage(messages.get("command.list.entry", map));
        }
        for (TrimJob trim : allTrims) {
            TrimSnapshot snap = trim.snapshot();
            Map<String, String> map = new HashMap<>();
            map.put("world", snap.worldName());
            map.put("state", snap.status().name());
            map.put("percent", StatsDisplay.formatPercent(snap.progressPercent()));
            map.put("processed", String.valueOf(snap.chunksProcessed()));
            map.put("total", String.valueOf(snap.totalChunks()));
            sender.sendMessage(messages.get("command.list.trim-entry", map));
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
        sender.sendMessage(messages.get("command.help.resume", placeholders));
        sender.sendMessage(messages.get("command.help.cancel", placeholders));
        sender.sendMessage(messages.get("command.help.trim", placeholders));
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
                || sub.equals("resume") || sub.equals("cancel") || sub.equals("trim")
                || sub.equals("status"))) {
            List<String> worldNames = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                worldNames.add(w.getName());
            }
            return startsWith(worldNames, args[1]);
        }
        if (args.length == 3 && (sub.equals("start") || sub.equals("trim"))) {
            return startsWith(SHAPES, args[2]);
        }
        if (args.length >= 5 && sub.equals("start")) {
            return startsWith(PATTERNS, args[args.length - 1]);
        }
        if (args.length >= 5 && sub.equals("trim")) {
            return startsWith(TRIM_TAIL_HINTS, args[args.length - 1]);
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

    private record ParsedZone(ZoneDefinition zone, TraversalPattern pattern) {
    }
}
