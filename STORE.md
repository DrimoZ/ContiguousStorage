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
- **License:** MIT
- **Source / issues:** https://github.com/DrimoZ/ContiguousStorage

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
