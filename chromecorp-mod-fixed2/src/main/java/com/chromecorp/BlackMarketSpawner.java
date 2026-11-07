package com.chromecorp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.List;

/**
 * Система спавна странствующих торговцев черного рынка.
 */
public final class BlackMarketSpawner {
    private BlackMarketSpawner() {}

    private static int cooldown = 0;
    private static final int MIN_COOLDOWN = 12000; // 10 минут минимум
    private static final int MAX_COOLDOWN = 36000; // 30 минут максимум

    /**
     * Вызывается каждый тик сервера для проверки спавна торговца.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return; // Проверяем раз в секунду

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // Случайный шанс спавна - 5% каждую секунду после окончания кулдауна
        if (server.getOverworld().random.nextInt(100) < 5) {
            trySpawnDealer(server);
        }
    }

    private static void trySpawnDealer(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        if (players.isEmpty()) return;

        // Выбираем случайного игрока
        ServerPlayerEntity targetPlayer = players.get(world.random.nextInt(players.size()));

        // Спавним на расстоянии 30-60 блоков от игрока
        double angle = world.random.nextDouble() * Math.PI * 2;
        int distance = 30 + world.random.nextInt(31);

        int x = (int) (targetPlayer.getX() + Math.cos(angle) * distance);
        int z = (int) (targetPlayer.getZ() + Math.sin(angle) * distance);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

        BlockPos spawnPos = new BlockPos(x, y, z);

        // Проверяем, что место подходит для спавна
        if (!world.getBlockState(spawnPos).isAir() || !world.getBlockState(spawnPos.down()).isSolidBlock(world, spawnPos.down())) {
            return;
        }

        // Создаем торговца
        BlackMarketDealer dealer = new BlackMarketDealer(ChromeCorp.BLACK_MARKET_DEALER, world);
        dealer.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360f, 0f);
        world.spawnEntity(dealer);

        // Уведомляем ближайших игроков
        for (ServerPlayerEntity player : players) {
            if (player.squaredDistanceTo(x, y, z) < 128 * 128) {
                player.sendMessage(Text.literal("§8§l[Черный Рынок]").formatted(Formatting.DARK_GRAY), false);
                player.sendMessage(Text.literal("§7Странный торговец появился поблизости...").formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("§7Координаты: §f" + x + " " + y + " " + z).formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("§c⚠ Он исчезнет через 20 минут!").formatted(Formatting.RED), false);
            }
        }

        // Устанавливаем новый кулдаун
        cooldown = MIN_COOLDOWN + world.random.nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
    }

    /**
     * Команда для админов: принудительный спавн торговца.
     */
    public static void forceSpawn(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        double angle = world.random.nextDouble() * Math.PI * 2;
        int distance = 10 + world.random.nextInt(11); // 10-20 блоков от игрока

        int x = (int) (player.getX() + Math.cos(angle) * distance);
        int z = (int) (player.getZ() + Math.sin(angle) * distance);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

        BlackMarketDealer dealer = new BlackMarketDealer(ChromeCorp.BLACK_MARKET_DEALER, world);
        dealer.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360f, 0f);
        world.spawnEntity(dealer);

        player.sendMessage(Text.literal("§aТорговец черного рынка призван на координатах " + x + " " + y + " " + z).formatted(Formatting.GREEN), false);
    }
}
