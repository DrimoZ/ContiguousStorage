package dev.drimoz.contiguousstorage.testdata;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.FramedTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.FSAttachments;
import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.network.Networks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Plays the manual test scenario of MANUAL_TESTS.md in a real client, by itself: placements and
 * breaks go through the client's game mode, as a player's would, the Linking Tool is used the same
 * way, action bar and chat messages are captured, screenshots are taken, and every expected outcome
 * is checked on the integrated server and written to {@code run/scenario/report-phaseN.txt}.
 *
 * <p>Phase 1 builds and checks; phase 2 runs on the next launch, against the saved world.
 */
final class ClientScenario {

    private record Step(String name, Runnable action, int waitAfter) {
    }

    private static final int Y = -60;
    private static final BlockPos C1 = new BlockPos(200, Y, 200);
    private static final BlockPos C2 = new BlockPos(200, Y, 206);
    private static final BlockPos C3 = new BlockPos(200, Y, 212);
    private static final BlockPos C4 = new BlockPos(205, Y, 218);
    private static final int SLOT_CONTROLLER = 0;
    private static final int SLOT_DRAWER = 1;
    private static final int SLOT_TOOL = 2;
    private static final int SLOT_OAK = 3;
    private static final int SLOT_SPRUCE = 4;
    private static final int SLOT_FRAMED = 5;
    private static final int SLOT_FRAMED_STYLED = 6;
    private static final int SLOT_PLANKS = 7;

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Step> steps = new ArrayList<>();
    private final List<String> overlay = new CopyOnWriteArrayList<>();
    private final List<String> chat = new CopyOnWriteArrayList<>();
    private final Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("scenario");
    private boolean phase2;
    private PrintWriter report;
    private int index;
    private int wait;
    private int worldTicks;
    private int shots;
    private int passed;
    private int failed;
    private boolean finished;
    private long walksBefore;

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent.System event) {
        (event.isOverlay() ? overlay : chat).add(event.getMessage().getString());
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        if (finished || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        if (++worldTicks < 120) {
            return;
        }
        measureTick();
        if (report == null) {
            start();
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (index >= steps.size()) {
            finish();
            return;
        }
        Step step = steps.get(index++);
        try {
            step.action().run();
        } catch (Throwable t) {
            fail(step.name(), "exception: " + t);
        }
        wait = step.waitAfter();
    }

    // --- The scenario --------------------------------------------------------------------------

    private void start() {
        try {
            Files.createDirectories(dir);
            phase2 = Files.exists(dir.resolve("phase2.marker"));
            report = new PrintWriter(Files.newBufferedWriter(dir.resolve(phase2 ? "report-phase2.txt" : "report-phase1.txt"), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log("SCENARIO phase " + (phase2 ? 2 : 1) + " starts");
        if (phase2) {
            buildPhase2();
        } else {
            buildPhase1();
        }
    }

    private void buildPhase1() {
        step("S0 setup", () -> {
            command("gamemode creative");
            command("time set day");
            command("gamerule doDaylightCycle false");
            command("weather clear");
            command("gamerule doWeatherCycle false");
            command("fill 195 -60 195 215 -50 225 air");
            run(() -> CSConfig.MODE.set(CSConfig.Mode.ASSIST));
            run(() -> CSConfig.TRIMS_ENABLED.set(true));
            run(() -> mc.getSingleplayerServer().getPlayerList().op(new net.minecraft.server.players.NameAndId(player().getGameProfile())));
            give(SLOT_CONTROLLER, new ItemStack(FunctionalStorage.DRAWER_CONTROLLER.getBlock()));
            give(SLOT_DRAWER, new ItemStack(FunctionalStorage.DRAWER_TYPES.get(FunctionalStorage.DrawerType.X_1).get(0).getBlock(), 64));
            give(SLOT_TOOL, new ItemStack(FunctionalStorage.LINKING_TOOL.get()));
            give(SLOT_OAK, new ItemStack(item("contiguousstorage:oak_trim"), 64));
            give(SLOT_SPRUCE, new ItemStack(item("contiguousstorage:spruce_trim"), 64));
            give(SLOT_FRAMED, new ItemStack(item("contiguousstorage:framed_trim"), 64));
            give(SLOT_FRAMED_STYLED, styledFramedTrim());
            give(SLOT_PLANKS, new ItemStack(Items.OAK_PLANKS, 64));
            look(C1.offset(2, 0, 0));
        }, 20);

        // ASSIST
        step("A1 drawer placed against the controller", () -> {
            placeOnFloor(SLOT_CONTROLLER, C1);
        }, 3);
        step("A1 (drawer)", () -> placeOnFloor(SLOT_DRAWER, x(201)), 4);
        step("A1 check", () -> {
            expect("A1 drawer against the controller is linked", x(201), C1, "linked");
            shot("A1_first_drawer");
        }, 2);
        step("A2 row and a drawer across a gap", () -> {
            for (int x = 202; x <= 205; x++) {
                placeOnFloor(SLOT_DRAWER, x(x));
            }
            placeOnFloor(SLOT_DRAWER, x(207));
        }, 4);
        step("A2 check", () -> {
            IntStream.rangeClosed(202, 205).forEach(x -> expect("A2 drawer in the row at x=" + x + " is linked", x(x), C1, "linked"));
            expect("A2 drawer across a gap (x=207) is free", x(207), C1, "free");
        }, 1);
        // A teleport and an action in the same tick lose the action: the server has not had the
        // client's confirmation of the teleport yet. Every look() gets a step of its own.
        step("A3 (move)", () -> look(x(204)), 10);
        step("A3 Linking Tool at a distance (ASSIST)", () -> use(SLOT_TOOL, C1, Direction.UP), 3);
        step("A3 (link)", () -> use(SLOT_TOOL, x(207), Direction.UP), 4);
        step("A3 check", () -> expect("A3 drawer linked with the tool across a gap stays linked in ASSIST", x(207), C1, "linked"), 1);
        step("A4 break the middle in ASSIST", () -> breakBlock(x(203)), 4);
        step("A4 check", () -> {
            expect("A4 far side (x=204) stays linked in ASSIST", x(204), C1, "linked");
            expect("A4 far side (x=205) stays linked in ASSIST", x(205), C1, "linked");
        }, 1);

        // STRICT
        step("S1 switch to STRICT", () -> run(() -> CSConfig.MODE.set(CSConfig.Mode.STRICT)), 130);
        step("S1 check", () -> {
            expect("S1 drawer linked at a distance (x=207) is unlinked by the safety net", x(207), C1, "free");
            expect("S1 cut-off drawer (x=204) is unlinked by the safety net", x(204), C1, "free");
            expect("S1 cut-off drawer (x=205) is unlinked by the safety net", x(205), C1, "free");
            expect("S1 near drawer (x=202) stays linked", x(202), C1, "linked");
        }, 1);
        step("S2 put the middle drawer back", () -> placeOnFloor(SLOT_DRAWER, x(203)), 4);
        step("S2 check", () -> IntStream.rangeClosed(203, 205)
                .forEach(x -> expect("S2 drawer x=" + x + " is linked again once the row is whole", x(x), C1, "linked")), 1);
        step("S3 break the middle in STRICT", () -> {
            overlay.clear();
            breakBlock(x(202));
        }, 2);
        step("S3 screenshot", () -> shot("S3_cut_smoke_and_message"), 3);
        step("S3 check", () -> {
            IntStream.rangeClosed(203, 205).forEach(x -> expect("S3 drawer x=" + x + " past the cut is unlinked", x(x), C1, "free"));
            expectTrue("S3 action bar says 3 drawers were disconnected (got " + overlay + ")",
                    overlay.stream().anyMatch(m -> m.contains("3 drawer(s) disconnected")));
        }, 1);
        step("S4 Linking Tool across a gap in STRICT", () -> {
            overlay.clear();
            use(SLOT_TOOL, C1, Direction.UP);
        }, 3);
        step("S4 (link)", () -> use(SLOT_TOOL, x(207), Direction.UP), 5);
        step("S4 check", () -> {
            expect("S4 drawer linked with the tool across a gap is unlinked again", x(207), C1, "free");
            expectTrue("S4 action bar says the drawer was not linked (got " + overlay + ")",
                    overlay.stream().anyMatch(m -> m.contains("not touching")));
        }, 1);
        step("S5 observer clock against the network", () -> {
            command("setblock 201 -60 199 observer[facing=north]");
            command("setblock 201 -60 198 observer[facing=south]");
            walksBefore = onServer(() -> Networks.validations);
        }, 100);
        step("S5 check", () -> {
            long walks = onServer(() -> Networks.validations) - walksBefore;
            expectTrue("S5 an observer clock pulsing against a drawer for 5 s walked the network " + walks + " time(s), expected 0", walks == 0);
            expect("S5 drawer next to the clock (x=201) is still linked", x(201), C1, "linked");
            command("setblock 201 -60 199 air");
            command("setblock 201 -60 198 air");
        }, 2);

        // Linking Tool, area mode (STRICT): three drawers touching the controller, two not.
        BlockPos c7 = new BlockPos(200, Y, 236);
        step("Z (move)", () -> look(c7.offset(3, 0, 0)), 10);
        step("Z1 drawers set by command, not linked", () -> {
            command("setblock 200 -60 236 functionalstorage:storage_controller");
            command("fill 201 -60 236 203 -60 236 functionalstorage:oak_1");
            command("fill 205 -60 236 206 -60 236 functionalstorage:oak_1");
            give(SLOT_TOOL, areaTool());
        }, 5);
        step("Z2 configure the tool", () -> {
            overlay.clear();
            use(SLOT_TOOL, c7, Direction.UP);
        }, 3);
        step("Z3 first corner", () -> use(SLOT_TOOL, c7.offset(1, 0, 0), Direction.UP), 3);
        step("Z4 second corner", () -> use(SLOT_TOOL, c7.offset(6, 0, 0), Direction.UP), 5);
        step("Z5 check", () -> {
            IntStream.rangeClosed(1, 3).forEach(dx -> expect("Z5 area-linked drawer touching the controller (x+" + dx + ") stays linked", c7.offset(dx, 0, 0), c7, "linked"));
            IntStream.rangeClosed(5, 6).forEach(dx -> expect("Z5 area-linked drawer across a gap (x+" + dx + ") is unlinked", c7.offset(dx, 0, 0), c7, "free"));
            expectTrue("Z5 action bar says 2 drawers were not linked (got " + overlay + ")",
                    overlay.stream().anyMatch(m -> m.contains("2 drawer(s) not touching")));
            give(SLOT_TOOL, new ItemStack(FunctionalStorage.LINKING_TOOL.get()));
        }, 1);

        // Immaterial Drawers (I) and Create (K, M) are on the 1.21.1 branch: neither exists for 26.1.

        // Trims
        step("T1 items in the hotbar", () -> {
            selectSlot(SLOT_FRAMED_STYLED);
            look(C2.offset(2, 0, 0));
        }, 25);
        step("T1 screenshot", () -> shot("T1_hotbar_items"), 2);
        step("T2 controller, trims, drawer", () -> {
            placeOnFloor(SLOT_CONTROLLER, C2);
            placeOnFloor(SLOT_OAK, C2.offset(1, 0, 0));
            placeOnFloor(SLOT_SPRUCE, C2.offset(2, 0, 0));
            placeOnFloor(SLOT_FRAMED, C2.offset(3, 0, 0));
            placeOnFloor(SLOT_FRAMED_STYLED, C2.offset(4, 0, 0));
        }, 3);
        step("T2 (drawer)", () -> placeOnFloor(SLOT_DRAWER, C2.offset(5, 0, 0)), 4);
        step("T2 check", () -> {
            expect("T2 drawer past oak, spruce, framed and styled framed trims is linked", C2.offset(5, 0, 0), C2, "linked");
            expectTrue("T2 the styled framed trim kept its design once placed (side = diamond block)",
                    "minecraft:diamond_block".equals(onServer(() -> designSide(C2.offset(4, 0, 0)))));
            closeUp(C2.offset(2, 0, 0));
        }, 30);
        step("T2 screenshot", () -> shot("T2_trims_row_close"), 2);
        step("T3 (move)", () -> look(C3.offset(2, 0, 0)), 10);
        step("T3 the same with planks", () -> {
            placeOnFloor(SLOT_CONTROLLER, C3);
            placeOnFloor(SLOT_PLANKS, C3.offset(1, 0, 0));
            placeOnFloor(SLOT_PLANKS, C3.offset(2, 0, 0));
        }, 3);
        step("T3 (drawer)", () -> placeOnFloor(SLOT_DRAWER, C3.offset(3, 0, 0)), 4);
        step("T3 check", () -> expect("T3 drawer past planks is free", C3.offset(3, 0, 0), C3, "free"), 1);
        step("T4 dryrun from the chat", () -> {
            chat.clear();
            mc.player.connection.sendCommand("contiguousstorage dryrun");
        }, 10);
        step("T4 check", () -> expectTrue("T4 /contiguousstorage dryrun answered (got " + chat + ")",
                chat.stream().anyMatch(m -> m.contains("STRICT would unlink"))), 1);
        step("T5 creative tabs", () -> {
            expectTrue("T5 the oak trim is in a creative tab", inCreativeTabs("contiguousstorage:oak_trim"));
            expectTrue("T5 the framed trim is in a creative tab", inCreativeTabs("contiguousstorage:framed_trim"));
        }, 1);
        step("T6 trims disabled", () -> {
            run(() -> CSConfig.TRIMS_ENABLED.set(false));
            command("reload");
        }, 60);
        step("T6 (re-place the drawer past the trims)", () -> {
            breakBlock(C2.offset(5, 0, 0));
        }, 3);
        step("T6 (drawer)", () -> placeOnFloor(SLOT_DRAWER, C2.offset(5, 0, 0)), 5);
        step("T6 check", () -> {
            expect("T6 with trims disabled, a drawer placed past them is free", C2.offset(5, 0, 0), C2, "free");
            expectTrue("T6 with trims disabled, no recipe of the mod is loaded", !onServer(this::hasModRecipes));
            expectTrue("T6 with trims disabled, the oak trim is in no creative tab", !inCreativeTabs("contiguousstorage:oak_trim"));
        }, 1);
        step("T6 trims enabled again", () -> {
            run(() -> CSConfig.TRIMS_ENABLED.set(true));
            command("reload");
        }, 60);
        step("T6 (re-place again)", () -> breakBlock(C2.offset(5, 0, 0)), 3);
        step("T6 (drawer again)", () -> placeOnFloor(SLOT_DRAWER, C2.offset(5, 0, 0)), 5);
        step("T6 check again", () -> {
            expect("T6 trims enabled again: the drawer past them is linked", C2.offset(5, 0, 0), C2, "linked");
            expectTrue("T6 trims enabled again: the mod's recipes are back", onServer(this::hasModRecipes));
        }, 1);

        // Chunks
        step("C1 (move)", () -> look(C4.offset(3, 0, 0)), 10);
        step("C1 chain across a chunk border (x=207|208)", () -> {
            placeOnFloor(SLOT_CONTROLLER, C4);
            for (int x = 206; x <= 211; x++) {
                placeOnFloor(SLOT_DRAWER, new BlockPos(x, Y, C4.getZ()));
            }
        }, 5);
        step("C1 check before leaving", () -> IntStream.rangeClosed(206, 211).forEach(x ->
                expect("C1 chain drawer x=" + x + " is linked", new BlockPos(x, Y, C4.getZ()), C4, "linked")), 1);
        step("C1 go 3000 blocks away", () -> run(() -> player().teleportTo(level(), 3000.5, -59, 3000.5, java.util.Set.of(), 0, 0, true)), 400);
        step("C1 come back", () -> look(C4.offset(3, 0, 0)), 100);
        step("C1 check after coming back", () -> IntStream.rangeClosed(206, 211).forEach(x ->
                expect("C1 after the chunks unloaded and came back, chain drawer x=" + x + " is still linked", new BlockPos(x, Y, C4.getZ()), C4, "linked")), 1);

        // FS #314: every link sends the controller's whole list to the client, which rebuilds the
        // network's shape. A 306-drawer wall placed one drawer per tick, against the same wall in
        // planks: client tick gaps measure the stutter.
        step("W (move)", () -> run(() -> player().teleportTo(level(), 214.5, -55, 224.5, java.util.Set.of(), 0, 10, true)), 30);
        step("W0 controller", () -> command("setblock 214 -60 229 functionalstorage:storage_controller"), 5);
        wall("W1 planks", Items.OAK_PLANKS, 234);
        wall("W2 drawers", FunctionalStorage.DRAWER_TYPES.get(FunctionalStorage.DrawerType.X_1).get(0).getBlock().asItem(), 230);
        step("W3 check", () -> expect("W3 the last drawer of the wall is linked", new BlockPos(222, -52, 231), new BlockPos(214, -60, 229), "linked"), 1);
    }

    /**
     * Places a two-layer wall (x 206..222, y -60..-52, z and z+1) one block per tick, the way a
     * player's placement does on the server (ItemStack.useOn, so EntityPlaceEvent fires), and
     * measures the client's tick gaps and the server's placement time meanwhile.
     */
    private void wall(String name, Item item, int z) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int layer = 0; layer < 2; layer++) {
            for (int y = -60; y <= -52; y++) {
                for (int x = 206; x <= 222; x++) {
                    blocks.add(new BlockPos(x, y, z + layer));
                }
            }
        }
        step(name + " start", () -> startMeasure(), 0);
        for (BlockPos pos : blocks) {
            step(name, () -> {
                long start = System.nanoTime();
                run(() -> {
                    ServerPlayer player = player();
                    ItemStack stack = new ItemStack(item);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    BlockPos below = pos.below();
                    stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
                });
                serverPlacementNanos += System.nanoTime() - start;
            }, 0);
        }
        step(name + " end", () -> {
            measuring = false;
            log(String.format(java.util.Locale.ROOT,
                    "MEASURE | %s wall of %d, one per tick: client tick gap max %.1f ms, mean %.1f ms, %d gap(s) over 100 ms; server placement mean %.2f ms",
                    name, blocks.size(), maxGapNanos / 1e6, gapCount == 0 ? 0 : gapSumNanos / 1e6 / gapCount, slowGaps, serverPlacementNanos / 1e6 / blocks.size()));
        }, 20);
    }

    private boolean measuring;
    private long lastTickNanos;
    private long maxGapNanos;
    private long gapSumNanos;
    private int gapCount;
    private int slowGaps;
    private long serverPlacementNanos;

    private void startMeasure() {
        measuring = true;
        lastTickNanos = 0;
        maxGapNanos = 0;
        gapSumNanos = 0;
        gapCount = 0;
        slowGaps = 0;
        serverPlacementNanos = 0;
    }

    private void measureTick() {
        long now = System.nanoTime();
        if (measuring && lastTickNanos != 0) {
            long gap = now - lastTickNanos;
            maxGapNanos = Math.max(maxGapNanos, gap);
            gapSumNanos += gap;
            gapCount++;
            if (gap > 100_000_000L) {
                slowGaps++;
            }
        }
        lastTickNanos = now;
    }

    private static ItemStack areaTool() {
        ItemStack tool = new ItemStack(FunctionalStorage.LINKING_TOOL.get());
        tool.set(FSAttachments.LINKING_MODE.get(), com.buuz135.functionalstorage.item.LinkingToolItem.LinkingMode.MULTIPLE);
        return tool;
    }

    private void buildPhase2() {
        step("P1 world loaded", () -> {
            log("PASS | P1 the world loaded and the game runs (no freeze on load)");
            passed++;
            closeUp(C2.offset(2, 0, 0));
        }, 30);
        step("P2 check", () -> {
            IntStream.rangeClosed(203, 205).forEach(x -> expect("P2 after reload, drawer x=" + x + " cut in S3 is still free", x(x), C1, "free"));
            expect("P2 after reload, drawer x=201 is still linked", x(201), C1, "linked");
        }, 1);
        step("P3 check", () -> {
            expect("P3 after reload, the drawer past the trims is still linked", C2.offset(5, 0, 0), C2, "linked");
            expectTrue("P3 after reload, the styled framed trim kept its design",
                    "minecraft:diamond_block".equals(onServer(() -> designSide(C2.offset(4, 0, 0)))));
            shot("P3_trims_row_after_reload");
        }, 2);
        step("P4 check", () -> IntStream.rangeClosed(206, 211).forEach(x ->
                expect("P4 after reload, chain drawer x=" + x + " across the chunk border is still linked", new BlockPos(x, Y, C4.getZ()), C4, "linked")), 1);
    }

    private void finish() {
        finished = true;
        log("SCENARIO phase " + (phase2 ? 2 : 1) + " done: " + passed + " passed, " + failed + " failed");
        if (!phase2) {
            try {
                Files.writeString(dir.resolve("phase2.marker"), "phase 1 done");
            } catch (IOException e) {
                log("ERROR writing the phase 2 marker: " + e);
            }
        }
        report.close();
        if (mc.level != null) {
            mc.level.disconnect(net.minecraft.network.chat.Component.empty());
        }
        mc.disconnectWithSavingScreen();
        mc.stop();
    }

    // --- Actions -------------------------------------------------------------------------------

    private void step(String name, Runnable action, int waitAfter) {
        steps.add(new Step(name, action, waitAfter));
    }

    private static BlockPos x(int x) {
        return new BlockPos(x, Y, C1.getZ());
    }

    /** Stands the player 3 blocks north of {@code target}, facing it. */
    private void look(BlockPos target) {
        run(() -> player().teleportTo(level(), target.getX() + 0.5, Y, target.getZ() - 2.5, java.util.Set.of(), 0, 35, true));
    }

    /** Closer and higher, for a screenshot of a row of blocks. */
    private void closeUp(BlockPos target) {
        run(() -> player().teleportTo(level(), target.getX() + 0.5, Y + 0.5, target.getZ() - 1.8, java.util.Set.of(), 0, 40, true));
    }

    private void give(int slot, ItemStack stack) {
        run(() -> player().getInventory().setItem(slot, stack));
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
    }

    /** What a right click on the top of the floor block under {@code target} does: places there. */
    private void placeOnFloor(int slot, BlockPos target) {
        use(slot, target.below(), Direction.UP);
    }

    private void use(int slot, BlockPos clicked, Direction face) {
        selectSlot(slot);
        Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clicked, false));
    }

    /** A creative player's left click: the block goes at once, through the server's break path. */
    private void breakBlock(BlockPos pos) {
        mc.gameMode.startDestroyBlock(pos, Direction.NORTH);
    }

    private void shot(String name) {
        String file = String.format("scenario_%02d_%s.png", ++shots, name);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1, message -> { });
        log("SHOT | " + file);
    }

    private void command(String command) {
        run(() -> mc.getSingleplayerServer().getCommands()
                .performPrefixedCommand(player().createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS).withSuppressedOutput(), command));
    }

    // --- Checks --------------------------------------------------------------------------------

    private void expect(String what, BlockPos drawer, BlockPos controller, String expected) {
        String actual = onServer(() -> linkState(drawer, controller));
        if (actual.equals(expected)) {
            log("PASS | " + what);
            passed++;
        } else {
            fail(what, "expected " + expected + ", got " + actual);
        }
    }

    private void expectTrue(String what, boolean condition) {
        if (condition) {
            log("PASS | " + what);
            passed++;
        } else {
            fail(what, "false");
        }
    }

    private void fail(String what, String why) {
        log("FAIL | " + what + " | " + why);
        failed++;
    }

    /** Both halves of a link, read on the server: the drawer's controllerPos and the controller's list. */
    private String linkState(BlockPos drawerPos, BlockPos controllerPos) {
        BlockEntity drawer = level().getBlockEntity(drawerPos);
        BlockEntity controller = level().getBlockEntity(controllerPos);
        if (!(drawer instanceof ControllableDrawerTile<?> d)) {
            return "no drawer at " + drawerPos.toShortString();
        }
        if (!(controller instanceof StorageControllerTile<?> c)) {
            return "no controller at " + controllerPos.toShortString();
        }
        boolean pointsAt = controllerPos.equals(d.getControllerPos());
        boolean listed = c.getConnectedDrawers().getConnectedDrawers().contains(drawerPos.asLong());
        return pointsAt && listed ? "linked" : !pointsAt && !listed ? "free" : "mixed(controllerPos=" + pointsAt + ", list=" + listed + ")";
    }

    private String designSide(BlockPos pos) {
        if (level().getBlockEntity(pos) instanceof FramedTile tile && tile.getFramedDrawerModelData() != null) {
            Item side = tile.getFramedDrawerModelData().getDesign().get("side");
            return side == null ? "none" : BuiltInRegistries.ITEM.getKey(side).toString();
        }
        return "no framed tile";
    }

    private boolean hasModRecipes() {
        return mc.getSingleplayerServer().getRecipeManager().getRecipes().stream()
                .anyMatch(recipe -> recipe.id().identifier().getNamespace().equals("contiguousstorage"));
    }

    private boolean inCreativeTabs(String id) {
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        Item item = item(id);
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && tab.getDisplayItems().stream().anyMatch(stack -> stack.is(item))) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack styledFramedTrim() {
        ItemStack stack = new ItemStack(item("contiguousstorage:framed_trim"), 16);
        CompoundTag style = new CompoundTag();
        style.putString("particle", "minecraft:diamond_block");
        style.putString("side", "minecraft:diamond_block");
        style.putString("front", "minecraft:gold_block");
        style.putString("front_divider", "minecraft:diamond_block");
        stack.set(FSAttachments.STYLE.get(), style);
        return stack;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    // --- Server access -------------------------------------------------------------------------

    private MinecraftServer server() {
        return mc.getSingleplayerServer();
    }

    private ServerLevel level() {
        return server().overworld();
    }

    private ServerPlayer player() {
        return server().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private <T> T onServer(Supplier<T> supplier) {
        return server().submit(supplier).join();
    }

    private void run(Runnable runnable) {
        server().submit(runnable).join();
    }

    private void log(String line) {
        report.println(line);
    }
}
