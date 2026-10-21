# Roadmap

**What this file is:** what may come after 0.1.0, and what will not. **What it is not:** a promise.
Nothing here has a date. Decisions and their reasons are in `DESIGN.md` (French).

## Before 0.1.0 is published

- Check that the slug `contiguous-storage` is free on CurseForge (it is on Modrinth), then create
  both pages from `STORE.md`.

## Maybe later

- **Land claims.** An optional check against claim mods (FTB Chunks, Open Parties and Claims) before
  auto-link. Today only spawn protection and the world border are checked.
- **Linking what machines put back.** A drawer set down by a Create contraption or a deployer
  against a network is not linked, because no player placed it. Linking it would need an owner for
  the machine, so it waits for the claims work.
- **A network size cap** for packs with very large walls, if measurements on real servers call for
  it. Functional Storage's own cost dominates long before this mod's does (README, *Known limits*).
- **Ports:** Minecraft 26.1 (Functional Storage 1.6) and 1.20.1 (Forge, Functional Storage 1.2). Each
  starts by checking that Functional Storage's linking API is the same there.

## Not planned

- Changing Functional Storage itself, or fixing its slowness on big networks.
- A permanent network overlay or outline.
- Other storage mods (Storage Drawers, Sophisticated Storage…). This mod talks to Functional Storage
  only.
