package dev.drimoz.contiguousstorage.block;

import com.buuz135.functionalstorage.block.FramedDrawerBlock;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Dresses the framed trim: frame block, core block, framed trim, in the first three slots - Functional
 * Storage's own framed recipe, which on 1.20.1 takes only its own blocks. The look is written by
 * Functional Storage's {@link FramedDrawerBlock#fill}, so it reads exactly like a framed drawer's.
 */
public class FramedTrimRecipe extends CustomRecipe {

    public static final ResourceLocation ID = CSBlocks.id("framed_trim");
    public static final RecipeSerializer<FramedTrimRecipe> SERIALIZER = new SimpleCraftingRecipeSerializer<>(FramedTrimRecipe::new);

    public FramedTrimRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        return inv.getContainerSize() >= 3
                && inv.getItem(0).getItem() instanceof BlockItem
                && inv.getItem(1).getItem() instanceof BlockItem
                && inv.getItem(2).is(CSBlocks.FRAMED_TRIM.asItem());
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registries) {
        return matches(inv, null) ? FramedDrawerBlock.fill(inv.getItem(0), inv.getItem(1), inv.getItem(2), ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
