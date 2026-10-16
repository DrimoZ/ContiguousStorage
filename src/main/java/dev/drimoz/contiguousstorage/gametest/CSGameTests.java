package dev.drimoz.contiguousstorage.gametest;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.LinkingToolItem;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The blocking spike of CLAUDE.md §8, task 1. The whole mod rests on one reading of Functional
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

    private static StorageControllerTile<?> placeLinkedPair(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, FunctionalStorage.DRAWER_CONTROLLER.getBlock());
        helper.setBlock(DRAWER, FunctionalStorage.DRAWER_TYPES.get(FunctionalStorage.DrawerType.X_1).get(0).getBlock());
        BlockEntity be = helper.getBlockEntity(CONTROLLER);
        helper.assertTrue(be instanceof StorageControllerTile<?>, "no Storage Controller was placed");
        StorageControllerTile<?> controller = (StorageControllerTile<?>) be;
        controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, helper.absolutePos(DRAWER));
        return controller;
    }

    /** Both halves of a link must agree: the controller's list and the drawer's controllerPos. */
    private static void assertLinked(GameTestHelper helper, StorageControllerTile<?> controller,
                                     boolean expected, String when) {
        BlockPos drawerAbs = helper.absolutePos(DRAWER);
        ControllableDrawerTile<?> drawer = helper.getBlockEntity(DRAWER);
        helper.assertValueEqual(inList(controller, drawerAbs), expected, "drawer in controller list " + when);
        helper.assertValueEqual(controller.getBlockPos().equals(drawer.getControllerPos()), expected,
                "drawer controllerPos points at the controller " + when);
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
