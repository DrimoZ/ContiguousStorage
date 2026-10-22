# Contiguous Storage

**What this file is:** what the mod does, how to configure it, and what it does not do, for players
and pack makers. **What it is not:** the design notes (`DESIGN.md`, in French), the build brief
(`CLAUDE.md`) or the store page (`STORE.md`).

An addon for [Functional Storage](https://modrinth.com/mod/functional-storage) on Forge 1.20.1.
A drawer joins a Storage Controller's network by **touching** it, directly or through a chain of
drawers and trims, as in Storage Drawers. Place a drawer against your wall and it is linked; break the
middle of a chain and, if you want, the far side is cut off.

Download: [Modrinth](https://modrinth.com/mod/contiguous-storage) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/contiguous-storage)

## How it works

- **Faces only.** A drawer touches a network through one of its six faces, never a diagonal.
- **What carries a network:** every Functional Storage drawer, compacting drawer, fluid drawer,
  ender drawer, framed drawer and controller extension, and the drawers of any Functional Storage
  addon built on its drawer class (tested with Immaterial Drawers on 1.21.1). Also this mod's **trims**, and any
  block in the `#contiguousstorage:conductors` tag.
- **The controller's range still applies.** A network cannot grow past the controller's range, Range
  Upgrades included. The mod only adds a rule on top of Functional Storage's own.
- **Two controllers: the first one wins.** A drawer already linked to a controller keeps that link.
  A drawer linked to another controller stops the network, so two walls side by side never merge.
- **An unloaded part never cuts anything.** If part of a chain is in an unloaded chunk, nothing is
  unlinked; it is checked again once everything is loaded. Walking away from your base does not cut
  your walls.

## Modes

Set in `config/contiguousstorage-common.toml`. It is a world setting, not a player one.

| `mode` | What it does |
|---|---|
| `OFF` | Functional Storage as shipped. |
| `ASSIST` (default) | A drawer placed against a controller, or against anything linked to one, is linked. The Linking Tool still links at a distance. **Nothing is ever unlinked.** |
| `STRICT` | As `ASSIST`, and a drawer that no longer touches its controller's network is unlinked. The Linking Tool only keeps the drawers that touch. |

> **Switching an existing world to `STRICT` unlinks every drawer that was linked at a distance.** It
> is the only thing this mod does that undoes a player's work. Run `/contiguousstorage dryrun` first:
> it says how many drawers `STRICT` would unlink, in any mode, and changes nothing.

In `STRICT`:

- breaking a block that cuts a chain unlinks the far side. The player who broke it reads
  *"N drawer(s) disconnected"* in the action bar, and each cut drawer puffs smoke (at most 32 per
  cut);
- the Linking Tool, in single or area mode, links first, then unlinks what does not touch, with
  *"N drawer(s) not touching the controller's network were not linked"*;
- a safety net checks every loaded network every `validationIntervalTicks` (100 by default). It
  catches what block updates miss, such as a mod that removes blocks without notifying neighbours.

A drawer linked by placement shows a small green sparkle.

## Trims

One trim per Functional Storage wood (oak, spruce, birch, jungle, acacia, dark oak, crimson, warped,
mangrove, cherry, and any wood a Functional Storage addon adds), plus a **framed trim**. A trim stores
nothing: it carries a network, so you can space drawers out or frame a wall.

- **Recipe:** eight sticks around one plank of the wood gives 4 trims. For the framed trim, eight
  sticks around an iron nugget gives 4.
- The framed trim is dressed like Functional Storage's framed drawers.
- Mined fastest with an axe. Found in Functional Storage's creative tab and in Building Blocks.
- `trimsEnabled = false` removes the recipes (after `/reload`) and the creative tab entries. Trims
  already placed stay as plain decoration and stop carrying networks. The blocks stay registered, so
  worlds and clients stay compatible.

## The conductor tag

`#contiguousstorage:conductors` (`data/contiguousstorage/tags/block/conductors.json`) lists blocks
that carry a network without being drawers. By default it holds **Storage Drawers' trims**, as
optional entries, so a pack with both mods links through them out of the box. Add anything you want by
datapack: decorative blocks, the Armory Cabinet… It reloads with `/reload`.

## Command

`/contiguousstorage dryrun` (permission level 2) reports how many loaded controllers were checked,
how many were skipped because part of their network is not loaded, and how many drawers `STRICT` would
unlink. It works in any mode and changes nothing. It returns the drawer count, so it works with
`execute store`.

## Install

- **Both client and server** need the mod: it registers the trims. A client without it is refused
  with NeoForge's *"registries with unknown keys … contiguousstorage:…_trim"*.
- Needs Forge 47 for Minecraft 1.20.1, Functional Storage 1.20.1-1.2.14 or newer, and Titanium.
  For Minecraft 1.21.1, see the `1.21.1` branch; for 26.1, `main`.

## Known limits

- **Land claims are not respected by auto-link.** A placed drawer is linked only if the player could
  interact with the controller. That is checked with Minecraft's own test, which covers spawn
  protection and the world border. Claim mods (FTB Chunks, Open Parties and Claims…) protect by other
  means. On a claims server, a player can still link a drawer to someone else's wall by placing it
  outside the claim, against that wall.
- **Only players link by placement.** A drawer put back by a Create contraption, a deployer or any
  other machine is not linked when it lands against a network. Link it with the Linking Tool, or
  break it and place it again.
- **Create.** Create cannot move Functional Storage's own drawers or controller: Functional Storage
  forbids it.
- **Big networks are slow in Functional Storage itself.** This mod keeps an index of every network,
  so its own work stays small:
  - placing a drawer costs about what Functional Storage's own linking costs;
  - the safety net checks a 20,000-drawer network in 2 to 3 ms without walking it;
  - a redstone clock next to a wall costs nothing.

  Functional Storage's own work grows with the network, and this mod does not change it. Every link
  or unlink rebuilds its whole list: about 2 ms at 2,000 drawers, 15 ms at 20,000. Linking 20,000
  free drawers at once takes seconds. Its controller inventory checks every access against the full
  drawer list. Very large networks are best split across several controllers.

## License

MIT for the code (`LICENSE`). The logo is All Rights Reserved (`LICENSE-ASSETS`).
