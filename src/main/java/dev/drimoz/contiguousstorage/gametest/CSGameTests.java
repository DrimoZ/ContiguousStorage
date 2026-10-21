package dev.drimoz.contiguousstorage.gametest;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.FSAttachments;
import com.buuz135.functionalstorage.item.LinkingToolItem;
import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import dev.drimoz.contiguousstorage.network.NetworkWalker;
import dev.drimoz.contiguousstorage.network.Networks;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.Items;
import com.buuz135.functionalstorage.item.component.SizeProvider;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.world.phys.Vec2;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.network.chat.Component;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Game tests, in the order of CLAUDE.md §8. First, the blocking spike of task 1. The whole mod rests on one reading of Functional
 * Storage 1.5.7: a drawer that went through {@code addConnectedDrawers(REMOVE, …)} stays unlinked
 * when it is loaded again, because its {@code onLoad} only relinks a drawer that still has a
 * {@code controllerPos}.
 *
 * <p>A chunk reload is simulated by saving the drawer's block entity, removing it, and putting a
 * fresh one back from the saved tag - what the chunk loader does. The second test is the control:
 * it proves the simulation does reach {@code onLoad}, by showing FS's relink actually firing on a
 * drawer that kept its {@code controllerPos}. Without it, the first test could pass for nothing.
 *
 * <p>Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(ContiguousStorage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CSGameTests {

    /** src/main/resources/data/contiguousstorage/structure/wall.nbt - 11 x 5 x 11, empty above the floor. */
    private static final String WALL = "wall";

    private static final BlockPos CONTROLLER = new BlockPos(5, 1, 5);
    private static final BlockPos DRAWER = new BlockPos(5, 1, 6);

    /**
     * {@code ConnectedDrawers} is built with a null level; the controller's first serverTick is
     * what rebuilds it for real, and {@code onLoad} runs on the tick after a block entity is added.
     */
    private static final int SETTLE_TICKS = 3;

    private CSGameTests() {
    }

    @GameTest(template = WALL)
    public static void aRemovedDrawerStaysUnlinkedAfterReload(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeLinkedPair(helper);
        BlockPos drawerAbs = helper.absolutePos(DRAWER);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertLinked(helper, controller, true, "after ADD");
                    controller.addConnectedDrawers(LinkingToolItem.ActionMode.REMOVE, drawerAbs);
                    assertLinked(helper, controller, false, "right after REMOVE");
                    reloadFromSave(helper, DRAWER);
                })
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertLinked(helper, controller, false,
                        "after the drawer was loaded again. FS relinked a drawer we had removed: "
                                + "every unlink this mod makes would be undone by the next chunk "
                                + "load. Stop - see CLAUDE.md §8, task 1."))
                .thenSucceed();
    }

    @GameTest(template = WALL)
    public static void aDrawerThatKeptItsControllerRelinksItselfOnLoad(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeLinkedPair(helper);
        BlockPos drawerAbs = helper.absolutePos(DRAWER);
        BlockEntity[] before = new BlockEntity[1];

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertLinked(helper, controller, true, "after ADD");
                    // What rebuild() does to a drawer whose chunk is not loaded: out of the list,
                    // controllerPos untouched. Only a test may edit the list by hand.
                    controller.getConnectedDrawers().getConnectedDrawers().remove(Long.valueOf(drawerAbs.asLong()));
                    helper.assertFalse(inList(controller, drawerAbs), "drawer still in the list");
                    before[0] = helper.getBlockEntity(DRAWER);
                    reloadFromSave(helper, DRAWER);
                })
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(DRAWER) != before[0],
                            "the block entity was not replaced - the reload simulation did nothing");
                    helper.assertTrue(inList(controller, drawerAbs),
                            "FS's onLoad did not relink a drawer that kept its controllerPos. Either "
                                    + "1.5.7 no longer relinks on load (CLAUDE.md §3 is wrong) or this "
                                    + "simulation never reaches onLoad - and then the REMOVE test proves nothing.");
                })
                .thenSucceed();
    }

    // --- NetworkWalker (CLAUDE.md §8, task 2) -------------------------------------------------

    /** West edge of the wall, so a line of drawers can run east for the whole template. */
    private static final BlockPos EDGE_CONTROLLER = new BlockPos(0, 1, 5);

    @GameTest(template = WALL)
    public static void theWalkerFollowsTheChainAndNothingElse(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        List<BlockPos> chain = List.of(new BlockPos(1, 1, 5), new BlockPos(2, 1, 5), new BlockPos(3, 1, 5));
        chain.forEach(pos -> helper.setBlock(pos, drawerBlock()));
        helper.setBlock(new BlockPos(5, 1, 5), drawerBlock()); // across a gap
        helper.setBlock(new BlockPos(1, 2, 6), drawerBlock()); // diagonal to the chain

        helper.assertValueEqual(walk(helper, controller, null), absolute(helper, chain),
                "reached from the controller (the drawer across a gap and the diagonal one must not be)");
        helper.succeed();
    }

    @GameTest(template = WALL)
    public static void theWalkerStopsAtAnotherController(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        helper.setBlock(new BlockPos(1, 1, 5), drawerBlock());
        placeController(helper, new BlockPos(2, 1, 5));
        helper.setBlock(new BlockPos(3, 1, 5), drawerBlock());

        helper.assertValueEqual(walk(helper, controller, null), absolute(helper, List.of(new BlockPos(1, 1, 5))),
                "reached - a second controller must neither be traversed nor bridge to its drawers");
        helper.succeed();
    }

    @GameTest(template = WALL)
    public static void theWalkerStaysInsideTheControllerRange(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        List<BlockPos> line = IntStream.rangeClosed(1, 10).mapToObj(x -> new BlockPos(x, 1, 5)).toList();
        line.forEach(pos -> helper.setBlock(pos, drawerBlock()));

        AABB range = NetworkWalker.range(controller);
        Set<BlockPos> inRange = absolute(helper, line).stream()
                .filter(pos -> range.contains(Vec3.atCenterOf(pos)))
                .collect(Collectors.toSet());
        helper.assertTrue(inRange.size() < line.size(),
                "the whole line is in range, so this test proves nothing - make it longer");
        helper.assertValueEqual(walk(helper, controller, null), inRange, "reached");
        helper.succeed();
    }

    /**
     * Test 6 of CLAUDE.md §7 at the walker's level: a chain running into a chunk that is not loaded
     * gives no answer at all, rather than a short one. A short one would unlink the far side.
     */
    @GameTest(template = WALL)
    public static void anUnloadedChunkOnTheChainAbortsTheWalk(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        List<BlockPos> chain = List.of(new BlockPos(1, 1, 5), new BlockPos(2, 1, 5), new BlockPos(3, 1, 5));
        chain.forEach(pos -> helper.setBlock(pos, drawerBlock()));
        BlockPos farEnd = helper.absolutePos(chain.get(2));

        helper.assertTrue(walk(helper, controller, null) != null, "control: with every chunk loaded the walk answers");
        helper.assertTrue(walk(helper, controller, farEnd) == null,
                "the walk answered although the chunk holding the end of the chain was unavailable");
        helper.succeed();
    }

    /** Walks with the real, non-blocking chunk access - minus the chunk holding {@code unloaded}, if given. */
    private static Set<BlockPos> walk(GameTestHelper helper, StorageControllerTile<?> controller, BlockPos unloaded) {
        return NetworkWalker.walk(controller.getBlockPos(), NetworkWalker.range(controller), chunks(helper, unloaded));
    }

    private static LongFunction<LevelChunk> chunks(GameTestHelper helper, BlockPos unloaded) {
        LongFunction<LevelChunk> loaded = NetworkWalker.loadedChunks(helper.getLevel());
        long hidden = unloaded == null ? Long.MIN_VALUE : ChunkPos.asLong(unloaded);
        return key -> key == hidden ? null : loaded.apply(key);
    }

    // --- Validation on demand (CLAUDE.md §8, task 3) ------------------------------------------

    /** Six drawers running east from {@link #EDGE_CONTROLLER}, all well inside its range. */
    private static final List<BlockPos> CHAIN = IntStream.rangeClosed(1, 6).mapToObj(x -> new BlockPos(x, 1, 5)).toList();

    /** Test 3. */
    @GameTest(template = WALL)
    public static void breakingTheMiddleOfAChainUnlinksTheFarSide(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeLinkedChain(helper);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    helper.assertValueEqual(Networks.validate(helper.getLevel(), controller), 0,
                            "drawers unlinked from an intact chain");
                    helper.destroyBlock(CHAIN.get(2));
                    helper.assertValueEqual(Networks.validate(helper.getLevel(), controller), 3,
                            "drawers unlinked once the third one is broken");
                    for (BlockPos pos : List.of(CHAIN.get(0), CHAIN.get(1))) {
                        assertLinked(helper, controller, pos, true, "on the near side");
                    }
                    for (BlockPos pos : CHAIN.subList(3, 6)) {
                        assertLinked(helper, controller, pos, false, "on the far side");
                    }
                })
                .thenSucceed();
    }

    /** Test 4: whatever the blast leaves, a drawer stays linked exactly when it still touches the controller. */
    @GameTest(template = WALL)
    public static void anExplosionThatCutsTheChainUnlinks(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeLinkedChain(helper);
        BlockPos blast = CHAIN.get(3);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    Vec3 at = Vec3.atCenterOf(helper.absolutePos(blast));
                    helper.getLevel().explode(null, at.x, at.y, at.z, 2.0F, Level.ExplosionInteraction.BLOCK);
                    helper.assertFalse(isDrawer(helper, blast), "the explosion did not break the drawer it hit");
                    helper.assertTrue(isDrawer(helper, CHAIN.get(5)),
                            "the explosion left nothing beyond the gap, so this test proves nothing - make it smaller");

                    helper.assertTrue(Networks.validate(helper.getLevel(), controller) > 0, "nothing was unlinked");
                    boolean touching = true;
                    for (BlockPos pos : CHAIN) {
                        touching &= isDrawer(helper, pos);
                        if (isDrawer(helper, pos)) {
                            assertLinked(helper, controller, pos, touching, "after the explosion");
                        }
                    }
                })
                .thenSucceed();
    }

    /**
     * Test 6, end to end: an intact chain whose middle cannot be read is not a broken chain. The
     * result must be "no answer" - a walk that treated the missing chunk as a gap would unlink
     * whatever lies beyond it in loaded chunks. (A chain that is really cut stops the walk at the cut,
     * before any unreadable chunk, and its unreadable far end is simply not a candidate.)
     */
    @GameTest(template = WALL)
    public static void anUnloadedPartOfTheChainUnlinksNothing(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeLinkedChain(helper);
        BlockPos middle = helper.absolutePos(CHAIN.get(2));

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    helper.assertValueEqual(Networks.validate(controller, chunks(helper, middle)), Networks.ABORTED,
                            "validation with the chunk holding the middle of the chain unavailable");
                    for (BlockPos pos : CHAIN) {
                        assertLinked(helper, controller, pos, true, "while part of the chain was unreadable");
                    }
                    helper.assertValueEqual(Networks.validate(helper.getLevel(), controller), 0,
                            "control: drawers unlinked from the same intact chain once every chunk is readable");
                })
                .thenSucceed();
    }

    // --- Auto-link on placement (CLAUDE.md §8, task 4) ----------------------------------------
    //
    // Placed by a mock player through ItemStack.useOn, which is what fires EntityPlaceEvent -
    // helper.setBlock does not. The mode is global config and game tests share one level, so each
    // mode other than the default (ASSIST) gets a batch of its own, set before and restored after.

    private static final String STRICT = "contiguousstorage_strict";
    private static final String OFF = "contiguousstorage_off";

    @BeforeBatch(batch = STRICT)
    public static void strictMode(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.STRICT);
    }

    @BeforeBatch(batch = OFF)
    public static void offMode(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.OFF);
    }

    @AfterBatch(batch = STRICT)
    public static void restoreModeAfterStrict(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.ASSIST);
    }

    @AfterBatch(batch = OFF)
    public static void restoreModeAfterOff(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.ASSIST);
    }

    /** Test 1, ASSIST. */
    @GameTest(template = WALL)
    public static void aDrawerPlacedAgainstTheControllerIsLinked(GameTestHelper helper) {
        placedAgainstTheController(helper, true);
    }

    /** Test 1, STRICT. */
    @GameTest(template = WALL, batch = STRICT)
    public static void aDrawerPlacedAgainstTheControllerIsLinkedInStrict(GameTestHelper helper) {
        placedAgainstTheController(helper, true);
    }

    /** Test 1, inverse: OFF is Functional Storage as shipped. */
    @GameTest(template = WALL, batch = OFF)
    public static void aDrawerPlacedAgainstTheControllerIsNotLinkedWhenOff(GameTestHelper helper) {
        placedAgainstTheController(helper, false);
    }

    private static void placedAgainstTheController(GameTestHelper helper, boolean linked) {
        StorageControllerTile<?> controller = placeController(helper, CONTROLLER);
        placeAsPlayer(helper, drawerBlock(), DRAWER);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertLinked(helper, controller, DRAWER, linked, "after being placed against it in " + CSConfig.mode()))
                .thenSucceed();
    }

    /** Test 2: three drawers away from the controller - and, inverse, not across a gap. */
    @GameTest(template = WALL)
    public static void aDrawerPlacedAgainstTheChainIsLinked(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        List<BlockPos> chain = CHAIN.subList(0, 3);
        chain.forEach(pos -> helper.setBlock(pos, drawerBlock()));
        controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, chain.stream().map(helper::absolutePos).toArray(BlockPos[]::new));
        BlockPos againstTheChain = CHAIN.get(3);
        BlockPos acrossAGap = CHAIN.get(5);

        placeAsPlayer(helper, drawerBlock(), againstTheChain);
        placeAsPlayer(helper, drawerBlock(), acrossAGap);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertLinked(helper, controller, againstTheChain, true, "placed against the end of the chain");
                    assertLinked(helper, controller, acrossAGap, false, "placed one block away from the chain");
                })
                .thenSucceed();
    }

    /**
     * Test 9, first one wins: a second controller placed against a linked drawer takes nothing from
     * the first, and each network grows only from what touches it.
     */
    @GameTest(template = WALL)
    public static void aSecondControllerDoesNotStealOrBridge(GameTestHelper helper) {
        StorageControllerTile<?> first = placeController(helper, EDGE_CONTROLLER);
        BlockPos shared = new BlockPos(1, 1, 5);
        helper.setBlock(shared, drawerBlock());
        first.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, helper.absolutePos(shared));

        BlockPos secondPos = new BlockPos(2, 1, 5);
        BlockPos againstSecond = new BlockPos(3, 1, 5);
        BlockPos againstShared = new BlockPos(1, 1, 6);
        placeAsPlayer(helper, FunctionalStorage.DRAWER_CONTROLLER.getBlock(), secondPos);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertLinked(helper, first, shared, true, "after a second controller was placed against it");
                    placeAsPlayer(helper, drawerBlock(), againstSecond);
                    placeAsPlayer(helper, drawerBlock(), againstShared);
                })
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    StorageControllerTile<?> second = helper.getBlockEntity(secondPos);
                    assertLinked(helper, second, againstSecond, true, "placed against the second controller only");
                    assertLinked(helper, first, againstShared, true, "placed against the first controller's drawer");
                    assertLinked(helper, first, shared, true, "at the end");

                    // The same bridge, without depending on which controller the tick handles first:
                    // a free drawer against the first one's drawer is out of the second one's reach.
                    BlockPos free = new BlockPos(1, 1, 4);
                    helper.setBlock(free, drawerBlock());
                    helper.assertValueEqual(Networks.linkFree(second, chunks(helper, null)), 0,
                            "drawers the second controller linked through the first one's drawer");
                    ControllableDrawerTile<?> freeDrawer = helper.getBlockEntity(free);
                    helper.assertTrue(freeDrawer.getControllerPos() == null, "the free drawer was linked across");
                })
                .thenSucceed();
    }

    /**
     * Test 10: a drawer from another mod that extends ControllableDrawerTile links and carries the
     * network, with no line of code for it here. Needs Immaterial Drawers in the dev run (see
     * build.gradle), hence not required.
     */
    @GameTest(template = WALL, required = false)
    public static void anAddonDrawerConductsAndLinks(GameTestHelper helper) {
        Block addonDrawer = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("immaterialdrawers", "energy_drawer"));
        if (addonDrawer == Blocks.AIR) {
            helper.fail("Immaterial Drawers is not in the run - build it next door to enable this test");
        }
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        BlockPos addon = CHAIN.get(0);
        BlockPos beyond = CHAIN.get(1);
        placeAsPlayer(helper, addonDrawer, addon);
        placeAsPlayer(helper, drawerBlock(), beyond);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertLinked(helper, controller, addon, true, "the addon drawer, placed against the controller");
                    assertLinked(helper, controller, beyond, true, "a drawer reached only through the addon drawer");
                })
                .thenSucceed();
    }

    // --- Detection by NeighborNotifyEvent (CLAUDE.md §8, task 5) ------------------------------

    /** Test 3 again, with nobody calling validate: the block update of the break is enough. */
    @GameTest(template = WALL, batch = STRICT)
    public static void inStrictBreakingTheMiddleOfAChainUnlinksTheFarSideByItself(GameTestHelper helper) {
        breakTheMiddleAndLook(helper, false);
    }

    /** Inverse: ASSIST never unlinks. */
    @GameTest(template = WALL)
    public static void inAssistBreakingTheMiddleOfAChainUnlinksNothing(GameTestHelper helper) {
        breakTheMiddleAndLook(helper, true);
    }

    private static void breakTheMiddleAndLook(GameTestHelper helper, boolean farSideLinked) {
        StorageControllerTile<?> controller = placeLinkedChain(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> helper.destroyBlock(CHAIN.get(2)))
                .thenIdle(2)
                .thenExecute(() -> {
                    for (BlockPos pos : CHAIN.subList(0, 2)) {
                        assertLinked(helper, controller, pos, true, "on the near side in " + CSConfig.mode());
                    }
                    for (BlockPos pos : CHAIN.subList(3, 6)) {
                        assertLinked(helper, controller, pos, farSideLinked, "on the far side in " + CSConfig.mode());
                    }
                })
                .thenSucceed();
    }

    // --- Controller registry and safety net (CLAUDE.md §8, task 6) ----------------------------

    /** Test 11 counts every validation on the server, so it runs alone. */
    private static final String STRICT_ALONE = "contiguousstorage_strict_alone";

    @BeforeBatch(batch = STRICT_ALONE)
    public static void strictModeAlone(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.STRICT);
    }

    @AfterBatch(batch = STRICT_ALONE)
    public static void restoreModeAfterStrictAlone(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.ASSIST);
    }

    /**
     * Test 5: {@code setBlock(pos, AIR, 2)} - no neighbour update, so no event sees the cut. Nothing
     * happens until the safety net's next pass, which unlinks the far side. The controller is placed
     * by a player, which is how it joins the registry, and auto-links the chain already there.
     */
    @GameTest(template = WALL, batch = STRICT, timeoutTicks = 400)
    public static void aSilentSetBlockIsCaughtByTheSafetyNet(GameTestHelper helper) {
        int interval = CSConfig.VALIDATION_INTERVAL_TICKS.get();
        CHAIN.forEach(pos -> helper.setBlock(pos, drawerBlock()));
        placeAsPlayer(helper, FunctionalStorage.DRAWER_CONTROLLER.getBlock(), EDGE_CONTROLLER);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    StorageControllerTile<?> controller = helper.getBlockEntity(EDGE_CONTROLLER);
                    CHAIN.forEach(pos -> assertLinked(helper, controller, pos, true, "after the controller was placed against the chain"));
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getGameTime() % interval == 1,
                        "waiting for the tick after a safety net pass"))
                .thenExecute(() -> helper.getLevel().setBlock(helper.absolutePos(CHAIN.get(2)), Blocks.AIR.defaultBlockState(), 2))
                .thenIdle(interval - 4)
                .thenExecute(() -> {
                    StorageControllerTile<?> controller = helper.getBlockEntity(EDGE_CONTROLLER);
                    for (BlockPos pos : CHAIN.subList(3, 6)) {
                        assertLinked(helper, controller, pos, true, "before the safety net's next pass - something else saw the silent cut");
                    }
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    StorageControllerTile<?> controller = helper.getBlockEntity(EDGE_CONTROLLER);
                    for (BlockPos pos : CHAIN.subList(0, 2)) {
                        assertLinked(helper, controller, pos, true, "on the near side after the safety net");
                    }
                    for (BlockPos pos : CHAIN.subList(3, 6)) {
                        assertLinked(helper, controller, pos, false, "on the far side after the safety net");
                    }
                })
                .thenSucceed();
    }

    /**
     * Test 11, the counterpart of Immaterial Drawers' anti-rebuild test: fifty drawers sitting still
     * are walked once per safety net pass, and never in between - and never unlinked.
     */
    @GameTest(template = WALL, batch = STRICT_ALONE, timeoutTicks = 400)
    public static void aWallOfDrawersDoesNotRevalidateEveryTick(GameTestHelper helper) {
        int interval = CSConfig.VALIDATION_INTERVAL_TICKS.get();
        int idle = interval * 5 / 2;
        // Rows x = 0..3 whole, then x = 4 up to the controller's z: fifty drawers, all touching.
        List<BlockPos> wall = IntStream.range(0, 50).mapToObj(i -> new BlockPos(i / 11, 1, i % 11)).toList();
        wall.forEach(pos -> helper.setBlock(pos, drawerBlock()));
        placeAsPlayer(helper, FunctionalStorage.DRAWER_CONTROLLER.getBlock(), CONTROLLER);
        long[] before = new long[1];

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    StorageControllerTile<?> controller = helper.getBlockEntity(CONTROLLER);
                    wall.forEach(pos -> assertLinked(helper, controller, pos, true, "once the wall has settled"));
                    before[0] = Networks.validations;
                })
                .thenIdle(idle)
                .thenExecute(() -> {
                    long walks = Networks.validations - before[0];
                    helper.assertTrue(walks >= idle / interval,
                            walks + " validations in " + idle + " ticks - the safety net never ran, so this test proves nothing");
                    helper.assertTrue(walks <= idle / interval + 1,
                            walks + " validations in " + idle + " ticks with nothing happening - expected one per safety net pass");
                    StorageControllerTile<?> controller = helper.getBlockEntity(CONTROLLER);
                    wall.forEach(pos -> assertLinked(helper, controller, pos, true, "after idling"));
                })
                .thenSucceed();
    }

    // --- Registry across a live mode switch (found on the dedicated server, 17 October 2026) --

    /** Each starts in a mode other than STRICT and switches to STRICT halfway, so each runs alone. */
    private static final String SWITCH_FROM_ASSIST = "contiguousstorage_switch_assist";
    private static final String SWITCH_FROM_OFF = "contiguousstorage_switch_off";

    @BeforeBatch(batch = SWITCH_FROM_ASSIST)
    public static void assistBeforeSwitch(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.ASSIST);
    }

    @BeforeBatch(batch = SWITCH_FROM_OFF)
    public static void offBeforeSwitch(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.OFF);
    }

    @AfterBatch(batch = SWITCH_FROM_ASSIST)
    public static void restoreAfterSwitchFromAssist(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.ASSIST);
    }

    @AfterBatch(batch = SWITCH_FROM_OFF)
    public static void restoreAfterSwitchFromOff(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.ASSIST);
    }

    /** A controller set by command in ASSIST must be on the safety net once the world goes STRICT. */
    @GameTest(template = WALL, batch = SWITCH_FROM_ASSIST, timeoutTicks = 400)
    public static void aControllerSetInAssistIsCheckedAfterSwitchingToStrict(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        distantLinkThenSwitchToStrict(helper, controller);
    }

    /** Same for a controller a player places while the mod is OFF. */
    @GameTest(template = WALL, batch = SWITCH_FROM_OFF, timeoutTicks = 400)
    public static void aControllerPlacedWhileOffIsCheckedAfterSwitchingToStrict(GameTestHelper helper) {
        placeAsPlayer(helper, FunctionalStorage.DRAWER_CONTROLLER.getBlock(), EDGE_CONTROLLER);
        distantLinkThenSwitchToStrict(helper, helper.getBlockEntity(EDGE_CONTROLLER));
    }

    private static void distantLinkThenSwitchToStrict(GameTestHelper helper, StorageControllerTile<?> controller) {
        BlockPos distant = new BlockPos(3, 1, 5); // x = 1 and 2 stay andesite
        helper.setBlock(distant, drawerBlock());
        controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, helper.absolutePos(distant));
        int interval = CSConfig.VALIDATION_INTERVAL_TICKS.get();
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertLinked(helper, controller, distant, true, "linked at a distance in " + CSConfig.mode());
                    CSConfig.MODE.set(CSConfig.Mode.STRICT);
                })
                .thenIdle(interval + 5)
                .thenExecute(() -> assertLinked(helper, controller, distant, false,
                        "a safety net pass after switching to STRICT - the controller is not in the registry"))
                .thenSucceed();
    }

    // --- Access control on auto-link (CLAUDE.md §8, task 9) -----------------------------------

    /** Moves the world border, which every other test would feel, so it runs alone. */
    private static final String BORDER = "contiguousstorage_border";

    @AfterBatch(batch = BORDER)
    public static void restoreWorldBorder(ServerLevel level) {
        level.getWorldBorder().setCenter(0, 0);
        level.getWorldBorder().setSize(WorldBorder.MAX_SIZE);
    }

    /**
     * Test 12: a drawer placed against a controller its placer may not interact with is not linked.
     * {@code Level.mayInteract} is spawn protection and the world border; spawn protection is off on a
     * game test server, so the border stands in: it runs between the controller (outside) and the
     * drawer (inside). Inverse, in the same test: a controller inside the border links its drawer.
     */
    @GameTest(template = WALL, batch = BORDER)
    public static void aDrawerPlacedByAnOutsiderIsNotLinked(GameTestHelper helper) {
        StorageControllerTile<?> forbidden = placeController(helper, EDGE_CONTROLLER);
        StorageControllerTile<?> allowed = placeController(helper, CONTROLLER);
        BlockPos againstForbidden = new BlockPos(1, 1, 5);
        BlockPos againstAllowed = DRAWER;
        BlockPos edge = helper.absolutePos(EDGE_CONTROLLER);
        // West edge half a block east of the forbidden controller: x = 0 is out, x >= 1 is in.
        helper.getLevel().getWorldBorder().setCenter(edge.getX() + 0.5 + 100, edge.getZ());
        helper.getLevel().getWorldBorder().setSize(200);
        helper.assertFalse(helper.getLevel().mayInteract(helper.makeMockPlayer(GameType.CREATIVE), forbidden.getBlockPos()),
                "the border does not shut out the first controller, so this test proves nothing");

        placeAsPlayer(helper, drawerBlock(), againstForbidden);
        placeAsPlayer(helper, drawerBlock(), againstAllowed);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertLinked(helper, forbidden, againstForbidden, false, "placed against a controller its placer may not interact with");
                    assertLinked(helper, allowed, againstAllowed, true, "placed against a controller its placer may interact with");
                })
                .thenSucceed();
    }

    // --- Cut feedback (CLAUDE.md §8, task 10) -------------------------------------------------

    /** Counts particles across the server, so it runs alone. */
    private static final String STRICT_CUT = "contiguousstorage_strict_cut";

    @BeforeBatch(batch = STRICT_CUT)
    public static void strictModeForCut(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.STRICT);
    }

    @AfterBatch(batch = STRICT_CUT)
    public static void restoreModeAfterCut(ServerLevel level) {
        CSConfig.MODE.set(CSConfig.Mode.ASSIST);
    }

    /**
     * Test 13: the player who cuts a chain is told how many drawers it cut off, in the English
     * fallback a client without the mod gets, and each of them gets particles. Then a cut of 120
     * drawers: the count is still right, and the particles stop at the cap.
     */
    @GameTest(template = WALL, batch = STRICT_CUT, timeoutTicks = 200)
    public static void aCutReportsTheDisconnectedDrawers(GameTestHelper helper) {
        // A chain of six along the top layer, clear of the slab below.
        BlockPos smallController = new BlockPos(0, 4, 0);
        List<BlockPos> chain = IntStream.rangeClosed(1, 6).mapToObj(x -> new BlockPos(x, 4, 0)).toList();
        StorageControllerTile<?> small = placeController(helper, smallController);
        chain.forEach(pos -> helper.setBlock(pos, drawerBlock()));
        small.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, chain.stream().map(helper::absolutePos).toArray(BlockPos[]::new));

        // A full 11 x 11 slab on y = 2, touching the controller below it at one drawer only.
        StorageControllerTile<?> big = placeController(helper, CONTROLLER);
        List<BlockPos> slab = IntStream.range(0, 121).mapToObj(i -> new BlockPos(i / 11, 2, i % 11)).toList();
        slab.forEach(pos -> helper.setBlock(pos, drawerBlock()));
        big.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, slab.stream().map(helper::absolutePos).toArray(BlockPos[]::new));
        BlockPos slabJoint = CONTROLLER.above();

        ListeningPlayer player = new ListeningPlayer(helper.getLevel());
        long[] before = new long[1];
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    before[0] = Networks.particlesSent;
                    breakAsPlayer(helper, chain.get(2), player);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    chain.subList(3, 6).forEach(pos -> assertLinked(helper, small, pos, false, "past the cut"));
                    helper.assertValueEqual(player.messages, List.of("3 drawer(s) disconnected"), "messages to the player who cut");
                    helper.assertValueEqual(Networks.particlesSent - before[0], 3L, "drawers given particles for a cut of 3");
                    player.messages.clear();
                    before[0] = Networks.particlesSent;
                    breakAsPlayer(helper, slabJoint, player);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    slab.stream().filter(pos -> !pos.equals(slabJoint))
                            .forEach(pos -> assertLinked(helper, big, pos, false, "in the slab cut off"));
                    helper.assertValueEqual(player.messages, List.of("120 drawer(s) disconnected"), "messages to the player who cut");
                    helper.assertValueEqual(Networks.particlesSent - before[0], (long) Networks.PARTICLE_CAP,
                            "drawers given particles for a cut of 120");
                })
                .thenSucceed();
    }

    /** What {@code ServerPlayerGameMode.destroyBlock} does: the event, then the break. */
    private static void breakAsPlayer(GameTestHelper helper, BlockPos pos, Player player) {
        BlockPos abs = helper.absolutePos(pos);
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player));
        helper.destroyBlock(pos);
    }

    /** A player that keeps what the action bar would have shown, as plain text. */
    private static final class ListeningPlayer extends Player {

        final List<String> messages = new ArrayList<>();

        ListeningPlayer(Level level) {
            super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-listening-player"));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message.getString());
        }
    }

    // --- Dry run (CLAUDE.md §8, task 11) ------------------------------------------------------

    /**
     * Test 14, in ASSIST: a cut chain is counted as STRICT would unlink it, and stays linked. The
     * count is taken on this test's controller only - other tests leave controllers in the level -
     * then the command itself runs through the dispatcher, to prove it is registered and answers.
     */
    @GameTest(template = WALL)
    public static void dryrunCountsWithoutRemoving(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeLinkedChain(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> helper.destroyBlock(CHAIN.get(2)))
                .thenIdle(2)
                .thenExecute(() -> {
                    Networks.DryRun result = Networks.dryRun(helper.getLevel(), List.of(controller.getBlockPos()));
                    helper.assertValueEqual(result, new Networks.DryRun(1, 0, 3), "dry run of a chain cut after its second drawer");
                    for (BlockPos pos : CHAIN.subList(3, 6)) {
                        assertLinked(helper, controller, pos, true, "after the dry run");
                    }

                    List<String> replies = new ArrayList<>();
                    helper.getLevel().getServer().getCommands().performPrefixedCommand(listeningSource(helper, replies),
                            "/contiguousstorage dryrun");
                    helper.assertTrue(replies.size() == 1 && replies.get(0).contains("STRICT would unlink"),
                            "reply to /contiguousstorage dryrun: " + replies);
                })
                .thenSucceed();
    }

    /** An operator-level command source that keeps what it is told, as plain text. */
    private static CommandSourceStack listeningSource(GameTestHelper helper, List<String> replies) {
        CommandSource listener = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                replies.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return new CommandSourceStack(listener, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)), Vec2.ZERO,
                helper.getLevel(), Commands.LEVEL_OWNERS, "contiguousstorage-test", Component.literal("test"),
                helper.getLevel().getServer(), null);
    }

    // --- Load measurement (CLAUDE.md §8, task 13) ---------------------------------------------

    /** Each builds far outside the template and forces chunks, so each runs alone. */
    private static final String LOAD_2K = "contiguousstorage_load_2k";
    private static final String LOAD_20K = "contiguousstorage_load_20k";

    private static final Logger LOGGER = LogUtils.getLogger();

    @GameTest(template = WALL, batch = LOAD_2K, required = false, timeoutTicks = 600)
    public static void loadOf2000Drawers(GameTestHelper helper) {
        measureLoad(helper, 13, 12, 1.0F);
    }

    @GameTest(template = WALL, batch = LOAD_20K, required = false, timeoutTicks = 1200)
    public static void loadOf20000Drawers(GameTestHelper helper) {
        measureLoad(helper, 28, 26, 2.0F);
    }

    /**
     * Not a pass/fail test: a measurement, logged as "LOAD ...", of what the mod adds to a big
     * network (Buuz135/FunctionalStorage#335). A {@code side x height x side} block of drawers above
     * the template, the controller in its middle, its range raised by a range-modifier item when
     * {@code rangeFactor > 1}. It only fails if the network itself is wrong.
     */
    private static void measureLoad(GameTestHelper helper, int side, int height, float rangeFactor) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 10, 0));
        BlockPos controllerPos = origin.offset(side / 2, height / 2, side / 2);
        List<ChunkPos> forced = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(origin.getX()) - 1; cx <= SectionPos.blockToSectionCoord(origin.getX() + side) + 1; cx++) {
            for (int cz = SectionPos.blockToSectionCoord(origin.getZ()) - 1; cz <= SectionPos.blockToSectionCoord(origin.getZ() + side) + 1; cz++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new ChunkPos(cx, cz));
            }
        }
        long t = System.nanoTime();
        List<BlockPos> drawers = new ArrayList<>();
        BlockState drawer = drawerBlock().defaultBlockState();
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < side; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!pos.equals(controllerPos)) {
                        level.setBlock(pos, drawer, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        drawers.add(pos);
                    }
                }
            }
        }
        level.setBlock(controllerPos, FunctionalStorage.DRAWER_CONTROLLER.getBlock().defaultBlockState(), Block.UPDATE_CLIENTS);
        StorageControllerTile<?> controller = (StorageControllerTile<?>) level.getBlockEntity(controllerPos);
        if (rangeFactor > 1) {
            ItemStack range = new ItemStack(Items.STICK);
            range.set(FSAttachments.CONTROLLER_RANGE_MODIFIER, new SizeProvider.ModifyFactor(rangeFactor));
            controller.getStorageUpgrades().setStackInSlot(0, range);
            controller.recalculateUpgrades();
        }
        LOGGER.info("LOAD {} drawers placed in {} ms, controller range {}", drawers.size(), ms(t), controller.getStorageMultiplier());
        AABB range = NetworkWalker.range(controller);
        long outOfRange = drawers.stream().filter(pos -> !range.contains(Vec3.atCenterOf(pos))).count();
        helper.assertValueEqual(outOfRange, 0L, "drawers outside the controller's range - make the range bigger");

        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        t = System.nanoTime();
        int linked = Networks.linkFree(controller, chunks);
        LOGGER.info("LOAD linkFree of {} drawers (one ADD, FS's own work included): {} ms", linked, ms(t));
        helper.assertValueEqual(linked, drawers.size(), "drawers linked");

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    long[] walks = new long[3];
                    for (int i = 0; i < walks.length; i++) {
                        long start = System.nanoTime();
                        helper.assertValueEqual(Networks.validate(controller, chunks), 0, "drawers unlinked from an intact network");
                        walks[i] = System.nanoTime() - start;
                    }
                    long start = System.nanoTime();
                    Networks.DryRun dry = Networks.dryRun(level, List.of(controllerPos));
                    LOGGER.info("LOAD validate of {} drawers (a safety net pass, network intact): {} / {} / {} ms; dryrun {} ms",
                            drawers.size(), walks[0] / 1_000_000.0, walks[1] / 1_000_000.0, walks[2] / 1_000_000.0,
                            (System.nanoTime() - start) / 1_000_000.0);
                    helper.assertValueEqual(dry, new Networks.DryRun(1, 0, 0), "dry run of the intact network");

                    // The everyday case: one more drawer set against the network. Auto-link walks the
                    // whole network to find it.
                    BlockPos extra = controllerPos.offset(0, height - height / 2, 0);
                    level.setBlock(extra, drawer, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    drawers.add(extra);
                    long one = System.nanoTime();
                    helper.assertValueEqual(Networks.linkFree(controller, chunks), 1, "drawers linked when one more is set against the network");
                    LOGGER.info("LOAD linkFree of 1 drawer set against {} linked ones: {} ms", drawers.size() - 1, ms(one));

                    // Clean up outside the template: unlink all at once, then clear without updates.
                    controller.addConnectedDrawers(LinkingToolItem.ActionMode.REMOVE, drawers.toArray(BlockPos[]::new));
                    for (BlockPos pos : drawers) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                    level.setBlock(controllerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                })
                .thenSucceed();
    }

    private static double ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    // --- Conductor tag (CLAUDE.md §8, task 8) -------------------------------------------------

    /**
     * Test 8: a drawer placed at the end of a run of conductors - copper, in the test-only
     * src/gametest tag - is linked; the same run in planks, out of the tag, carries nothing.
     */
    @GameTest(template = WALL)
    public static void aConductorTagBlockCarriesTheNetwork(GameTestHelper helper) {
        helper.assertTrue(Blocks.COPPER_BLOCK.defaultBlockState().is(NetworkWalker.CONDUCTORS),
                "copper is not in #contiguousstorage:conductors - the src/gametest tag did not reach the run");
        helper.assertFalse(Blocks.OAK_PLANKS.defaultBlockState().is(NetworkWalker.CONDUCTORS), "planks are a conductor");
        // The shipped tag lists Storage Drawers' trims as optional entries, and Storage Drawers is not
        // in the run. Had one of them failed the load, the whole tag would be gone, copper with it.
        helper.assertValueEqual(BuiltInRegistries.BLOCK.getTag(NetworkWalker.CONDUCTORS).map(HolderSet.Named::size).orElse(0), 1,
                "blocks in #contiguousstorage:conductors (the shipped tag merged with the test one, trims skipped)");
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        helper.setBlock(new BlockPos(1, 1, 5), Blocks.COPPER_BLOCK);
        helper.setBlock(new BlockPos(2, 1, 5), Blocks.COPPER_BLOCK);
        helper.setBlock(new BlockPos(0, 1, 4), Blocks.OAK_PLANKS);
        helper.setBlock(new BlockPos(0, 1, 3), Blocks.OAK_PLANKS);
        BlockPos pastCopper = new BlockPos(3, 1, 5);
        BlockPos pastPlanks = new BlockPos(0, 1, 2);
        placeAsPlayer(helper, drawerBlock(), pastCopper);
        placeAsPlayer(helper, drawerBlock(), pastPlanks);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertLinked(helper, controller, pastCopper, true, "placed at the end of two conductor blocks");
                    assertLinked(helper, controller, pastPlanks, false, "placed at the end of two planks");
                })
                .thenSucceed();
    }

    // --- Linking Tool (CLAUDE.md §8, task 7) --------------------------------------------------

    /** Test 7, STRICT: the tool links across a gap, and the next tick undoes it. */
    @GameTest(template = WALL, batch = STRICT)
    public static void theLinkingToolCannotLinkAcrossAGap(GameTestHelper helper) {
        linkAcrossAGap(helper, false);
    }

    /** Test 7, inverse: in ASSIST the tool still links at a distance. */
    @GameTest(template = WALL)
    public static void inAssistTheLinkingToolLinksAcrossAGap(GameTestHelper helper) {
        linkAcrossAGap(helper, true);
    }

    private static void linkAcrossAGap(GameTestHelper helper, boolean staysLinked) {
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        // x = 1 and 2 stay as the template's andesite: in range, not touching.
        BlockPos distant = new BlockPos(3, 1, 5);
        helper.setBlock(distant, drawerBlock());
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    useLinkingTool(helper, EDGE_CONTROLLER, distant);
                    assertLinked(helper, controller, distant, true, "right after the Linking Tool - FS did not link it, so this test proves nothing");
                })
                .thenIdle(2)
                .thenExecute(() -> assertLinked(helper, controller, distant, staysLinked, "a tick after the Linking Tool in " + CSConfig.mode()))
                .thenSucceed();
    }

    /** What {@code ServerPlayerGameMode.useItemOn} does with a Linking Tool: the event, then the item. */
    private static void useLinkingTool(GameTestHelper helper, BlockPos controller, BlockPos target) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack tool = new ItemStack(FunctionalStorage.LINKING_TOOL.get());
        tool.set(FSAttachments.CONTROLLER, helper.absolutePos(controller));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        BlockPos abs = helper.absolutePos(target);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs, hit));
        tool.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /**
     * What a player's right click does: {@code ItemStack.useOn}, through NeoForge's placement hook.
     * The click lands on {@code pos} itself, which is cleared first so the block replaces it rather
     * than going on top - the wall template's y = 1 layer is solid.
     */
    private static void placeAsPlayer(GameTestHelper helper, Block block, BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack stack = new ItemStack(block);
        // placeAt's UseOnContext reads the item from the player's hand, not from the stack it is given.
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.placeAt(player, stack, pos.below(), Direction.UP);
        helper.assertBlockPresent(block, pos);
    }

    private static StorageControllerTile<?> placeLinkedChain(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeController(helper, EDGE_CONTROLLER);
        CHAIN.forEach(pos -> helper.setBlock(pos, drawerBlock()));
        controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD,
                CHAIN.stream().map(helper::absolutePos).toArray(BlockPos[]::new));
        return controller;
    }

    private static boolean isDrawer(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof ControllableDrawerTile<?>;
    }

    private static Set<BlockPos> absolute(GameTestHelper helper, List<BlockPos> relative) {
        return relative.stream().map(helper::absolutePos).collect(Collectors.toCollection(HashSet::new));
    }

    private static StorageControllerTile<?> placeController(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, FunctionalStorage.DRAWER_CONTROLLER.getBlock());
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof StorageControllerTile<?>, "no Storage Controller was placed");
        return (StorageControllerTile<?>) be;
    }

    private static Block drawerBlock() {
        return FunctionalStorage.DRAWER_TYPES.get(FunctionalStorage.DrawerType.X_1).get(0).getBlock();
    }

    // --- Shared ---------------------------------------------------------------------------------

    private static StorageControllerTile<?> placeLinkedPair(GameTestHelper helper) {
        StorageControllerTile<?> controller = placeController(helper, CONTROLLER);
        helper.setBlock(DRAWER, drawerBlock());
        controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, helper.absolutePos(DRAWER));
        return controller;
    }

    /** Both halves of a link must agree: the controller's list and the drawer's controllerPos. */
    private static void assertLinked(GameTestHelper helper, StorageControllerTile<?> controller,
                                     boolean expected, String when) {
        assertLinked(helper, controller, DRAWER, expected, when);
    }

    private static void assertLinked(GameTestHelper helper, StorageControllerTile<?> controller, BlockPos pos,
                                     boolean expected, String when) {
        BlockPos drawerAbs = helper.absolutePos(pos);
        ControllableDrawerTile<?> drawer = helper.getBlockEntity(pos);
        helper.assertValueEqual(inList(controller, drawerAbs), expected, "drawer " + pos + " in controller list " + when);
        helper.assertValueEqual(controller.getBlockPos().equals(drawer.getControllerPos()), expected,
                "drawer " + pos + " controllerPos points at the controller " + when);
    }

    private static boolean inList(StorageControllerTile<?> controller, BlockPos abs) {
        return controller.getConnectedDrawers().getConnectedDrawers().contains(abs.asLong());
    }

    /**
     * What a chunk unload then load does to one block entity: saved to a tag, dropped, and created
     * again from that tag, which queues {@code onLoad} for the next tick. The block itself is never
     * touched, so {@code Drawer.onRemove} - which would unlink it - does not run.
     */
    private static void reloadFromSave(GameTestHelper helper, BlockPos rel) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = level.getBlockState(abs);
        CompoundTag tag = level.getBlockEntity(abs).saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(abs);
        BlockEntity fresh = BlockEntity.loadStatic(abs, state, tag, level.registryAccess());
        helper.assertTrue(fresh != null, "could not load the drawer back from its saved tag");
        level.setBlockEntity(fresh);
    }
}
