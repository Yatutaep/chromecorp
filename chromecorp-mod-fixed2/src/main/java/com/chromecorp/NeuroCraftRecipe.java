package com.chromecorp;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Map;

/** Крафт нейропозина: стеклянная бутылочка + 2-4 ингредиента. Риск отравления зависит от чистоты. */
public class NeuroCraftRecipe extends SpecialCraftingRecipe {
    public static final Map<Item, Integer> PURITY = Map.of(
        Items.ROTTEN_FLESH, 0, Items.SPIDER_EYE, 1, Items.FERMENTED_SPIDER_EYE, 3, Items.NETHER_WART, 4,
        Items.BLAZE_POWDER, 5, Items.GLOWSTONE_DUST, 5, Items.AMETHYST_SHARD, 6, Items.GHAST_TEAR, 10);

    public NeuroCraftRecipe(Identifier id, CraftingRecipeCategory category) { super(id, category); }

    @Override
    public boolean matches(RecipeInputInventory inv, World world) {
        int bottles = 0, mats = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (s.isOf(Items.GLASS_BOTTLE)) bottles++;
            else if (PURITY.containsKey(s.getItem())) mats++;
            else return false;
        }
        return bottles == 1 && mats >= 2 && mats <= 4;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inv, DynamicRegistryManager reg) {
        int score = 0, mats = 0;
        for (int i = 0; i < inv.size(); i++) {
            Integer v = PURITY.get(inv.getStack(i).getItem());
            if (v != null) { score += v; mats++; }
        }
        // мусор → 50-55%, лучший состав → 1%
        int poison = score == 0 ? 50 + (mats * 3) % 6 : Math.max(1, Math.min(55, (int) Math.round(55 - score * 3.5)));
        ItemStack out = new ItemStack(ChromeCorp.NEUROPOSIN);
        NbtCompound n = out.getOrCreateNbt();
        n.putInt("PoisonPct", poison);
        n.putString("Source", "craft");
        return out;
    }

    @Override
    public boolean fits(int width, int height) { return width * height >= 3; }

    @Override
    public RecipeSerializer<?> getSerializer() { return ChromeCorp.NEURO_SERIALIZER; }
}
