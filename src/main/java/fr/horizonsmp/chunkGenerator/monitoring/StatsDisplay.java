package fr.horizonsmp.chunkGenerator.monitoring;

import fr.horizonsmp.chunkGenerator.config.PluginConfig;
import fr.horizonsmp.chunkGenerator.i18n.Messages;
import fr.horizonsmp.chunkGenerator.job.GenerationJob;
import fr.horizonsmp.chunkGenerator.job.JobManager;
import fr.horizonsmp.chunkGenerator.job.JobSnapshot;
import fr.horizonsmp.chunkGenerator.job.JobStatus;
import fr.horizonsmp.chunkGenerator.permission.PermissionService;
import fr.horizonsmp.chunkGenerator.platform.PlatformAdapter;
import fr.horizonsmp.chunkGenerator.platform.PlatformTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class StatsDisplay {

    private final JavaPlugin plugin;
    private final PlatformAdapter platform;
    private final JobManager jobs;
    private final Messages messages;
    private final Supplier<PluginConfig> configSupplier;

    private final Map<String, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<String, Long> lastConsoleLogMs = new ConcurrentHashMap<>();
    private PlatformTask refresher;

    public StatsDisplay(JavaPlugin plugin,
                        PlatformAdapter platform,
                        JobManager jobs,
                        Messages messages,
                        Supplier<PluginConfig> configSupplier) {
        this.plugin = plugin;
        this.platform = platform;
        this.jobs = jobs;
        this.messages = messages;
        this.configSupplier = configSupplier;
    }

    public void start() {
        refresher = platform.scheduleAsyncTask(this::refresh, 1000L, 1000L);
    }

    public void stop() {
        if (refresher != null) {
            refresher.cancel();
            refresher = null;
        }
        for (BossBar bar : bossBars.values()) {
            hideBarFromAll(bar);
        }
        bossBars.clear();
    }

    public void onPlayerJoin(Player player) {
        PluginConfig cfg = configSupplier.get();
        if (!cfg.display().bossBar().enabled()) {
            return;
        }
        if (!player.hasPermission(PermissionService.BOSSBAR)
                && !player.hasPermission(PermissionService.ADMIN)) {
            return;
        }
        for (BossBar bar : bossBars.values()) {
            player.showBossBar(bar);
        }
    }

    private void refresh() {
        PluginConfig cfg = configSupplier.get();
        for (GenerationJob job : jobs.jobs()) {
            JobSnapshot snap = jobs.snapshotOf(job);
            if (job.status() == JobStatus.RUNNING) {
                updateBossBar(job, snap, cfg);
                updateActionBar(snap, cfg);
                maybeLogConsole(snap, cfg);
            } else {
                BossBar existing = bossBars.remove(job.worldName());
                if (existing != null) {
                    hideBarFromAll(existing);
                }
            }
        }
    }

    private void updateBossBar(GenerationJob job, JobSnapshot snap, PluginConfig cfg) {
        if (!cfg.display().bossBar().enabled()) {
            BossBar removed = bossBars.remove(job.worldName());
            if (removed != null) {
                hideBarFromAll(removed);
            }
            return;
        }
        Component text = messages.get("display.bossbar", baseMap(snap));
        float progress = clamp01((float) (snap.progressPercent() / 100.0));
        BossBar.Color color = resolveColor(cfg.display().bossBar().color());

        BossBar bar = bossBars.computeIfAbsent(job.worldName(), w -> {
            BossBar created = BossBar.bossBar(text, progress, color, BossBar.Overlay.PROGRESS);
            showBarToEligible(created);
            return created;
        });
        bar.name(text);
        bar.progress(progress);
        bar.color(color);
    }

    private void updateActionBar(JobSnapshot snap, PluginConfig cfg) {
        if (!cfg.display().actionBar().enabled()) {
            return;
        }
        UUID uuid = snap.launcherUuid();
        if (uuid == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        player.sendActionBar(messages.get("display.actionbar", baseMap(snap)));
    }

    private void maybeLogConsole(JobSnapshot snap, PluginConfig cfg) {
        if (!cfg.display().console().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastConsoleLogMs.getOrDefault(snap.worldName(), 0L);
        long intervalMs = Math.max(5L, cfg.display().console().intervalSeconds()) * 1000L;
        if (now - last < intervalMs) {
            return;
        }
        lastConsoleLogMs.put(snap.worldName(), now);
        String line = messages.getRaw("display.console", baseMapPlain(snap));
        plugin.getLogger().info(stripColors(line));
    }

    private void showBarToEligible(BossBar bar) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PermissionService.BOSSBAR)
                    || player.hasPermission(PermissionService.ADMIN)) {
                player.showBossBar(bar);
            }
        }
    }

    private void hideBarFromAll(BossBar bar) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
        }
    }

    private Map<String, String> baseMap(JobSnapshot snap) {
        Map<String, String> out = baseMapPlain(snap);
        return out;
    }

    private Map<String, String> baseMapPlain(JobSnapshot snap) {
        Map<String, String> out = new HashMap<>();
        out.put("world", snap.worldName());
        out.put("percent", formatPercent(snap.progressPercent()));
        out.put("done", String.valueOf(snap.chunksDone()));
        out.put("total", String.valueOf(snap.totalChunks()));
        out.put("speed", formatSpeed(snap.chunksPerSecond()));
        out.put("eta", formatEta(snap.etaSeconds()));
        out.put("tps", String.format(Locale.ROOT, "%.1f", snap.tps()));
        out.put("cpu", String.format(Locale.ROOT, "%.0f", snap.cpuPercent()));
        out.put("ram", snap.usedRamMb() + "/" + snap.maxRamMb());
        out.put("inflight", String.valueOf(snap.inflight()));
        out.put("target", String.valueOf(snap.inflightTarget()));
        return out;
    }

    public static String formatPercent(double pct) {
        return String.format(Locale.ROOT, "%.1f", pct);
    }

    public static String formatSpeed(double speed) {
        if (speed >= 100.0) return String.format(Locale.ROOT, "%.0f", speed);
        if (speed >= 10.0) return String.format(Locale.ROOT, "%.1f", speed);
        return String.format(Locale.ROOT, "%.2f", speed);
    }

    public static String formatEta(long etaSeconds) {
        if (etaSeconds < 0) {
            return "?";
        }
        long h = etaSeconds / 3600L;
        long m = (etaSeconds % 3600L) / 60L;
        long s = etaSeconds % 60L;
        if (h > 0) {
            return h + "h" + (m < 10 ? "0" : "") + m + "m";
        }
        if (m > 0) {
            return m + "m" + (s < 10 ? "0" : "") + s + "s";
        }
        return s + "s";
    }

    private static BossBar.Color resolveColor(String name) {
        if (name == null) {
            return BossBar.Color.BLUE;
        }
        try {
            return BossBar.Color.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.BLUE;
        }
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static String stripColors(String raw) {
        return raw.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}
