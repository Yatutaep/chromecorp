package com.chromecorp;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Предмет-вызов рипера: ПКМ по блоку ставит NPC. */
public class RipperSpawner extends Item {
    private final boolean shady;

    public RipperSpawner(Settings s, boolean shady) { super(s); this.shady = shady; }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World w = ctx.getWorld();
        if (w.isClient) return ActionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        RipperEntity e = ChromeCorp.RIPPER.create((ServerWorld) w);
        if (e == null) return ActionResult.FAIL;
        e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ctx.getPlayerYaw() + 180f, 0f);
        e.setShady(shady);
        e.setCustomName(Text.literal(shady ? "Теневой механик" : "Сплайсер"));
        e.setCustomNameVisible(true);
        e.setPersistent();
        w.spawnEntity(e);
        if (ctx.getPlayer() != null && !ctx.getPlayer().isCreative()) ctx.getStack().decrement(1);
        return ActionResult.SUCCESS;
    }
}
