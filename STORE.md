# Store page

**What this file is:** the CurseForge and Modrinth page, ready to paste. **What it is not:** the
reference. Anything that changes goes into `README.md` first, then here.

- **Name:** Contiguous Storage
- **Slug:** `contiguous-storage` (same on both sites; check the spelling before submitting)
- **Summary (one line):** Functional Storage drawers join a controller by touching it, like Storage Drawers.
- **Categories:** Storage, Utility
- **Loader / version:** NeoForge, Minecraft 1.21.1
- **Environment:** client and server, both required
- **Dependencies:** Functional Storage (required), Titanium (required)
- **License:** MIT for the code; the logo is All Rights Reserved
- **Source / issues:** https://github.com/DrimoZ/ContiguousStorage

## Publishing 0.1.0+1.21.1

Checked on 21 October 2026:
- the slug `contiguous-storage` is free on Modrinth and on CurseForge (both answer 404);
- the 36 required game tests pass, and so do the in-game scenario (67/0, 13/0) and the client
  without the mod (`MANUAL_TESTS.md`).

Still to do, by hand, since each step publishes something:
1. **GitHub.** Create `DrimoZ/ContiguousStorage`: `neoforge.mods.toml` links to it for the
   homepage and the issue tracker. The branch for 1.21.1 is `main` (the local branch is still
   `master`). Push, then tag `v0.1.0+1.21.1`.
2. **Changelog.** Replace "unreleased" with the release date in `CHANGELOG.md`, then
   `./gradlew build`.
3. **File:** `build/libs/contiguousstorage-0.1.0+1.21.1.jar`. Minecraft 1.21.1, NeoForge, client
   and server.
4. **Dependencies:** Functional Storage and Titanium, both required. Nothing else.
5. **Page:** the description below, with the logo `logo/logo.png`. The changelog is the
   0.1.0 section of `CHANGELOG.md`.
6. **Screenshots:** none yet. The dev captures in `run/screenshots` are 854×480 with the HUD showing,
   too poor for a store page.

---

## Description

**Build your storage, don't configure it.**

In Functional Storage, drawers join a Storage Controller with the Linking Tool, at a distance.
**Contiguous Storage** links them the way Storage Drawers does: a drawer placed against the controller,
or against a drawer already in its network, is linked. No tool, no clicking.

### Features

- **Link by touching.** Through the controller, other drawers, and trims. Faces only, within the
  controller's usual range.
- **Three modes.**
  - `ASSIST` (default): placing links, and the Linking Tool still works at a distance. Nothing is ever
    unlinked, so it is safe on an existing world.
  - `STRICT`: a drawer that no longer touches its network is unlinked. Break the middle of a wall and
    the far side is cut. You are told how many drawers, and they puff smoke.
  - `OFF`: Functional Storage as shipped.
- **Trims** in every Functional Storage wood, including woods added by its addons, plus a framed trim.
  They carry a network without storing anything. They can be turned off in the config.
- **Works with Storage Drawers' trims**, and with any block a pack adds to the
  `#contiguousstorage:conductors` tag.
- **Works with Functional Storage addons**: every drawer, compacting, fluid, ender and framed drawer,
  controller extensions, and addon drawers such as Immaterial Drawers'.
- **`/contiguousstorage dryrun`** tells you what `STRICT` would unlink before you switch.
- **Safe with unloaded chunks**: a chain you cannot see is never cut.
- **Light on the server**: networks are indexed, so a redstone clock next to your wall costs nothing.

### Good to know

- Switching an existing world to `STRICT` unlinks drawers that were linked at a distance. Run
  `dryrun` first.
- Land claim mods are not checked when a drawer is placed: only spawn protection and the world border
  are.
- A drawer moved back by a machine, such as a Create contraption, is not linked again by placement.
  Use the Linking Tool.
- Very large networks (thousands of drawers) are slow in Functional Storage itself. Split them across
  several controllers.
- Includes Functional Storage's own fix for a world-loading freeze in Functional Storage 1.5.7. It is
  applied to 1.5.7 and older only.

Not affiliated with Functional Storage or Storage Drawers.
