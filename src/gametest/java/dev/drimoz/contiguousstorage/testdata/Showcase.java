package dev.drimoz.contiguousstorage.testdata;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.block.tile.ItemControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.FSAttachments;
import com.mojang.blaze3d.platform.NativeImage;
import dev.drimoz.contiguousstorage.CSConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dev-only, {@code ./gradlew runClient -Pshowcase}: builds the store page's scenes in the world
 * "scenario" and captures them at the window's size into {@code run/store-screenshots/}. Blocks are
 * placed as a player places them (server side, item in hand), so auto-link runs as in a real game.
 * Then {@code java tools/Banners.java run/store-screenshots docs/store-art} (STORE.md).
 */
public final class Showcase {

    private record Step(Runnable action, int waitAfter) {
    }

    /** One character per block, rows from the top - see {@link #stack(char)}. */
    private static final String[] HERO = {
            "DDDDDDDDDDD",
            "DaubdEdbuaD",
            "DstcECEctsD",
            "DaubdEdbuaD",
            "DDDDDDDDDDD"};
    private static final String[] TRIMS = {
            "aRaWaMaHafaFa",
            "COaSaBaJaAaDa"};
    private static final Item[] GOODS = {Items.COBBLESTONE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND,
            Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.EMERALD, Items.OAK_LOG, Items.WHEAT,
            Items.COPPER_INGOT, Items.QUARTZ, Items.AMETHYST_SHARD, Items.BONE, Items.STRING, Items.GLOWSTONE_DUST,
            Items.SLIME_BALL, Items.GUNPOWDER, Items.BRICK, Items.PRISMARINE_SHARD};

    private static final BlockPos HERO_AT = new BlockPos(400, -56, 401);   // top-left of HERO
    private static final BlockPos CHAIN_AT = new BlockPos(420, -60, 401);  // where the controller goes
    private static final BlockPos TRIMS_AT = new BlockPos(440, -59, 401);  // top-left of TRIMS

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Step> steps = new ArrayList<>();
    private final List<BlockPos> placed = new ArrayList<>();
    private int ticks;
    private int index;
    private int wait;
    private int goods;
    private boolean finished;

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        if (finished || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        if (++ticks < 120) {
            return;
        }
        if (steps.isEmpty()) {
            script();
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (index >= steps.size()) {
            finished = true;
            mc.options.hideGui = false;
            mc.level.disconnect();
            mc.disconnect();
            mc.stop();
            return;
        }
        Step step = steps.get(index++);
        step.action().run();
        wait = step.waitAfter();
    }

    private void script() {
        step(() -> run(() -> {
            CSConfig.MODE.set(CSConfig.Mode.ASSIST);
            command("gamemode creative");
            command("time set 6000");
            command("weather clear");
            command("gamerule doDaylightCycle false");
            command("gamerule doWeatherCycle false");
            command("gamerule doMobSpawning false");
            command("kill @e[type=!player]");
            command("clear @s");
            // The dry run needs permission 2, as on a server; /op is a dedicated server command.
            mc.getSingleplayerServer().getPlayerList().op(player().getGameProfile());
            command("fill 395 -60 396 455 -50 415 air");
            command("fill 395 -61 396 455 -61 415 spruce_planks");
            command("fill 397 -60 400 413 -54 400 stone_bricks");
            command("fill 417 -60 400 431 -55 400 stone_bricks");
            command("fill 437 -60 400 455 -56 400 stone_bricks");
            teleport(405.5, -59, 409.5, 180, 0);
        }), 60);

        // 1. The hero wall: Functional Storage drawers, a controller, energy drawers, framed in trims.
        step(() -> run(() -> build(HERO_AT, HERO)), 30);
        step(() -> run(this::fill), 40);
        camera(405.5, -59.2, 408.8, 180, -2, "01_wall");
        camera(399.2, -59.2, 408.6, 215, 2, "02_wall_angle");

        // 2. A chain of free drawers; the controller is placed at its end and links all of them.
        step(() -> run(() -> {
            teleport(424.5, -60, 407.6, 180, 5);
            placed.clear();
            for (int x = 1; x <= 7; x++) {
                place(CHAIN_AT.east(x), stack(x % 3 == 0 ? 't' : 'a'));
            }
            place(CHAIN_AT.offset(3, 1, 0), stack('a'));
            place(CHAIN_AT.offset(4, 1, 0), stack('b'));
            place(CHAIN_AT.offset(7, 1, 0), stack('s'));
            place(CHAIN_AT.offset(7, 2, 0), stack('d'));
        }), 30);
        step(() -> run(this::fill), 20);
        camera(424.5, -60, 407.6, 180, 5, null);
        step(() -> run(() -> place(CHAIN_AT, stack('C'))), 4);
        step(() -> shot("03_autolink"), 3);
        step(() -> shot("03_autolink_b"), 30);

        // 3. STRICT: breaking the second drawer cuts the far side - smoke and the action bar message.
        step(() -> {
            run(() -> {
                CSConfig.MODE.set(CSConfig.Mode.STRICT);
                player().getInventory().selected = 8;
            });
            mc.player.getInventory().selected = 8;
            mc.options.hideGui = false;
        }, 20);
        step(() -> run(() -> player().gameMode.destroyBlock(CHAIN_AT.east(2))), 3);
        step(() -> shot("04_cut"), 6);
        step(() -> shot("04_cut_b"), 20);

        // 4. Every trim: one per Functional Storage wood, a plain framed one and a dressed one.
        step(() -> {
            mc.options.hideGui = true;
            run(() -> {
                CSConfig.MODE.set(CSConfig.Mode.ASSIST);
                teleport(446.5, -59, 408.5, 180, 0);
                build(TRIMS_AT, TRIMS);
            });
        }, 30);
        step(() -> run(this::fill), 20);
        camera(446.5, -60, 407.6, 180, 5, "05_trims");

        // 5. The dry run, typed in the chat, in front of the wall.
        camera(405.5, -59.2, 408.8, 180, -2, null);
        step(() -> {
            mc.options.hideGui = false;
            mc.player.connection.sendCommand("contiguousstorage dryrun");
        }, 15);
        step(() -> shot("06_dryrun"), 2);
    }

    /** Places a grid of blocks, bottom row first, from its top-left corner, facing the player (south). */
    private void build(BlockPos topLeft, String[] rows) {
        placed.clear();
        for (int r = rows.length - 1; r >= 0; r--) {
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                if (ch != '.') {
                    place(topLeft.offset(c, -r, 0), stack(ch));
                }
            }
        }
    }

    /** Fills what was placed last: a different item in every drawer slot, power in the energy drawers. */
    private void fill() {
        ServerLevel level = level();
        for (BlockPos pos : placed) {
            if (level.getBlockEntity(pos) instanceof ItemControllableDrawerTile<?> tile && !(tile instanceof StorageControllerTile<?>)) {
                for (int slot = 0; slot < tile.getStorage().getSlots(); slot++) {
                    tile.getStorage().insertItem(slot, new ItemStack(GOODS[goods++ % GOODS.length], 64 * (1 + slot)), false);
                }
            }
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            if (energy != null) {
                energy.receiveEnergy(Integer.MAX_VALUE, false);
            }
        }
    }

    /**
     * The item a character stands for. Drawers: a/b/c oak 1x1, 1x2, 2x2 (Functional Storage's _1, _2,
     * _4), s/t/u spruce, d/e dark oak; C controller; E Immaterial Drawers' energy drawer. Trims: O S B J
     * A D R W M H by wood (oak, spruce, birch, jungle, acacia, dark oak, crimson, warped, mangrove,
     * cherry), f framed, F framed and dressed.
     */
    private static ItemStack stack(char ch) {
        return switch (ch) {
            case 'a', 'b', 'c' -> drawer("oak", ch - 'a');
            case 's', 't', 'u' -> drawer("spruce", ch - 's');
            case 'd', 'e' -> drawer("dark_oak", ch - 'd');
            case 'C' -> new ItemStack(FunctionalStorage.DRAWER_CONTROLLER.getBlock());
            case 'E' -> new ItemStack(item("immaterialdrawers:energy_drawer"));
            case 'f' -> new ItemStack(item("contiguousstorage:framed_trim"));
            case 'F' -> dressedFramedTrim();
            default -> new ItemStack(item("contiguousstorage:" + Map.of('O', "oak", 'S', "spruce", 'B', "birch",
                    'J', "jungle", 'A', "acacia", 'D', "dark_oak", 'R', "crimson", 'W', "warped", 'M', "mangrove",
                    'H', "cherry").get(ch) + "_trim"));
        };
    }

    private static ItemStack drawer(String wood, int size) {
        return new ItemStack(item("functionalstorage:" + wood + "_" + (size == 0 ? 1 : size == 1 ? 2 : 4)));
    }

    private static ItemStack dressedFramedTrim() {
        ItemStack stack = new ItemStack(item("contiguousstorage:framed_trim"));
        CompoundTag style = new CompoundTag();
        style.putString("particle", "minecraft:polished_deepslate");
        style.putString("side", "minecraft:polished_deepslate");
        style.putString("front", "minecraft:gold_block");
        style.putString("front_divider", "minecraft:polished_deepslate");
        stack.set(FSAttachments.STYLE.get(), style);
        return stack;
    }

    /** As a player would: the item in hand, used on the top of the block below. Server thread. */
    private void place(BlockPos pos, ItemStack stack) {
        ServerPlayer player = player();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        placed.add(pos);
    }

    /** Moves the camera (GUI hidden), lets the chunks and particles settle, and captures if named. */
    private void camera(double x, double y, double z, float yaw, float pitch, String shot) {
        step(() -> {
            mc.options.hideGui = true;
            run(() -> {
                command("kill @e[type=!player]");
                teleport(x, y, z, yaw, pitch);
            });
        }, 40);
        if (shot != null) {
            step(() -> shot(shot), 2);
        }
    }

    private void teleport(double x, double y, double z, float yaw, float pitch) {
        player().teleportTo(level(), x, y, z, yaw, pitch);
    }

    private void shot(String name) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path dir = mc.gameDirectory.toPath().resolve("store-screenshots");
            Files.createDirectories(dir);
            image.writeToFile(dir.resolve(name + ".png"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void step(Runnable action, int waitAfter) {
        steps.add(new Step(action, waitAfter));
    }

    private void command(String command) {
        mc.getSingleplayerServer().getCommands()
                .performPrefixedCommand(player().createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
    }

    private ServerPlayer player() {
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private ServerLevel level() {
        return mc.getSingleplayerServer().overworld();
    }

    private void run(Runnable runnable) {
        mc.getSingleplayerServer().submit(runnable).join();
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }
}
