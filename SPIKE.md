# Spike — does an unlink survive a reload?

**What this file is:** the record of task 1 in `CLAUDE.md` §8: the analysis, what actually happened
when it ran, and where the analysis was wrong. **What it is not:** the design (`DESIGN.md`) or the
operational brief (`CLAUDE.md`).

Run on 16 October 2026 against Functional Storage **1.21-1.5.7** (the published jar), Titanium
1.21-4.0.34, NeoForge 21.1.248.

---

## 1. The question

The whole mod unlinks drawers through `StorageControllerTile.addConnectedDrawers(REMOVE, …)`.
Functional Storage has a self-repair path: a drawer that is loaded with a `controllerPos` but is not in
its controller's list adds itself back. If that path also caught drawers we had removed, every unlink
would be undone by the next chunk load, and the mod could not exist.

## 2. The analysis (from the 1.5.7 bytecode, `javap -c -p`)

- `ControllableDrawerTile.onLoad` exists in 1.5.7. On the server it does the following when
  `controllerPos != null && level.isLoaded(controllerPos)`:
  - it reads the block entity there, which is a **blocking** `getBlockEntity`;
  - if that is a `StorageControllerTile` whose list lacks this drawer, it calls
    `addConnectedDrawers(ADD, getBlockPos())`;
  - if it is anything else, it clears `controllerPos`.
- `REMOVE` takes the drawer out of the list **and** calls `clearControllerPos()` on it. The drawer then
  reaches `onLoad` with `controllerPos == null`, so nothing relinks it.
- `controllerPos` is a Titanium `@Save` field, so it is persisted, and a null one loads back as null.

Prediction: a full `REMOVE` holds across a reload, and a drawer that only left the *list* (which is
what `rebuild()` does to unloaded drawers) relinks itself.

## 3. What was run

`gametest/CSGameTests`, two tests in the `wall` template:

| Test | Steps | Result |
|---|---|---|
| `aRemovedDrawerStaysUnlinkedAfterReload` | ADD, wait 3 ticks, REMOVE, reload the drawer, wait 3 ticks | **Passed.** Not in the list, `controllerPos` null. |
| `aDrawerThatKeptItsControllerRelinksItselfOnLoad` (control) | ADD, wait, take it out of the list by hand (keeping `controllerPos`), reload, wait | **Passed.** Back in the list. |

**How a reload is simulated:**
1. Save the drawer's block entity with `saveWithFullMetadata`.
2. Remove it with `removeBlockEntity`, which leaves the block in place so `Drawer.onRemove` never runs.
3. Put back a fresh one with `BlockEntity.loadStatic` and `setBlockEntity`.

`setBlockEntity` goes through `LevelChunk.addAndRegisterBlockEntity`, then
`Level.addFreshBlockEntities`, so `onLoad` runs on the next tick, which is the same path a chunk load
takes. The control test exists to prove this: if the simulation never reached `onLoad`, the first test
would pass for nothing. The control relinking shows that it does reach it.

```
./gradlew runGameTestServer  →  All 2 required tests passed :)
```

## 4. Where the analysis was wrong, or incomplete

- **Nothing in the prediction failed.** The REMOVE-based design stands.
- **Not covered: a real chunk unload.** The simulation skips `onChunkUnloaded`. In 1.5.7 that hook
  only calls `rebuild()` on the controller, and it does not touch `controllerPos`.
  - Game tests run around (−14 486 521, −6 583 996), far from spawn chunks. A real-unload test using
    `ServerLevel.setChunkForced(false)` therefore looks feasible later, if the manual check below
    raises any doubt.
  - **Manual check still to do:** link a drawer, unlink it, walk out of range, come back, and look at it
    with Jade.
- **Brief corrections found while preparing the spike** (now folded into `CLAUDE.md`):
  - `displayTest` does not exist in FML 21.1. A mod with no payloads is already joinable by vanilla
    clients.
  - `NeighborNotifyEvent` also fires client-side and from repeaters and comparators.
  - `EntityPlaceEvent` fires while block snapshots are being captured, so auto-linking must be deferred
    to the tick.
  - `getPriority` / `setPriority` are absent from 1.5.7.
