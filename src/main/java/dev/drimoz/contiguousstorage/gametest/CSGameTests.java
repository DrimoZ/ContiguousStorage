package dev.drimoz.contiguousstorage.gametest;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.LinkingToolItem;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import dev.drimoz.contiguousstorage.network.NetworkWalker;
import dev.drimoz.contiguousstorage.network.Networks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
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
