package fr.horizonsmp.chunkGenerator.config;

public record PluginConfig(
        Throttle throttle,
        Display display,
        Monitoring monitoring,
        Persistence persistence
) {

    public record Throttle(
            double targetTps,
            double maxChunksPerTick,
            double minChunksPerTick
    ) {
    }

    public record Display(
            BossBar bossBar,
            ActionBar actionBar,
            Console console
    ) {
        public record BossBar(boolean enabled, String color) {
        }

        public record ActionBar(boolean enabled) {
        }

        public record Console(boolean enabled, long intervalSeconds) {
        }
    }

    public record Monitoring(long pollIntervalMs) {
    }

    public record Persistence(
            boolean autoResumeOnStartup,
            long saveThrottleSeconds,
            long saveThrottleChunks
    ) {
    }
}
