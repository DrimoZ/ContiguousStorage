# Store copy

Paste-ready text for the CurseForge and Modrinth project pages. Not documentation: this file sells
the mod, the [README](README.md) explains it. Every claim here is something the mod does today:
check it against `CHANGELOG.md` and `MANUAL_TESTS.md` before changing a number.

Images live in `docs/store-art/` and the description links them from GitHub
(`raw.githubusercontent.com`, `main`), never from the description editor: an image uploaded there
joins the CurseForge gallery. Change an image, push, and both pages show it. They are made in two
steps:

```bash
./gradlew runClient -Pshowcase
java tools/Banners.java run/store-screenshots docs/store-art
```

The first builds the scenes in the dev world and captures them at 1920×1080 into
`run/store-screenshots/`. The second makes the 850 px JPGs. Only the gallery is uploaded by hand,
from the full-size captures.


## Summary

> One line, 256 characters at most, shown under the name in every search result.

Functional Storage drawers join a Storage Controller by touching it, directly or through other drawers and trims, like Storage Drawers. Optional STRICT mode cuts off what no longer touches. Trims in every wood.

## Categories

CurseForge: main **Addons** (of Functional Storage). Additional: Storage, Utility & QoL.
Modrinth: **storage**, **utility**.

## Licence field

Custom: `MIT (code) / All Rights Reserved (assets)`, pointing at
`https://github.com/DrimoZ/ContiguousStorage/blob/main/LICENSE`. MIT for the code; the logo is all
rights reserved (`LICENSE-ASSETS`).

## Description

<!-- Everything below this line is pasted into the site's Markdown editor as-is. -->

![Contiguous Storage](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/banner.jpg)

**In Functional Storage, drawers join a controller with the Linking Tool. Here, they join it by touching it.**

Place a drawer against your Storage Controller, or against any drawer already in its network, and
it is linked. No tool, no clicking, no hunting for the one drawer you forgot. Storage that you
build instead of configure, the way Storage Drawers players know it.

![Touch to link](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/header_touch.jpg)

- ✅ **Links on contact**: through the controller, through other drawers, through trims. Faces
  only, never diagonals.
- ✅ **Every drawer Functional Storage has**, in every size, plus compacting, fluid, ender and
  framed drawers, and controller extensions.
- ✅ **Addon drawers too**: anything built on Functional Storage's drawers conducts and links,
  Immaterial Drawers' energy drawers included.
- ✅ **The controller's range still applies**, Range Upgrades included.
- ✅ **A sparkle** on every drawer the moment it joins.

![A wall of drawers around one controller](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/wall.jpg)

Place the controller last, against a row of free drawers, and the whole row joins at once:

![Placing the controller links every drawer it touches](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/autolink.jpg)

![STRICT mode](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/header_strict.jpg)

The default, **ASSIST**, only ever adds links: install it on an existing world and nothing breaks.
Switch to **STRICT** and the network is exactly what touches:

- **Break the middle of a chain and the far side is cut off.** You read *"9 drawer(s)
  disconnected"* and see the cut drawers puff smoke.
- **The Linking Tool** still works, in single and area mode, but keeps only the drawers that touch.
- **A safety net** re-checks every loaded network every few seconds, for blocks other mods remove
  without a word.
- **Unloaded chunks never cut anything.** Walk away from your base and come back: your walls are
  as you left them.

![Breaking the chain in STRICT mode](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/cut.jpg)

![Trims](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/header_trims.jpg)

Trims carry the network and store nothing. Use them to space drawers out or frame a wall.

- **One per Functional Storage wood**: oak, spruce, birch, jungle, acacia, dark oak, crimson,
  warped, mangrove, cherry, and any wood a Functional Storage addon adds.
- **A framed trim**, dressed like Functional Storage's framed drawers.
- **Eight sticks around a plank** give four trims; around an iron nugget, four framed ones.
- Packs can **turn them off** in the config: no recipe, and the ones already placed stay as
  decoration.

![Every trim, between drawers](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/trims.jpg)

![You stay in control](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/header_control.jpg)

- **Three modes**: `OFF` (Functional Storage as shipped), `ASSIST` (default), `STRICT`.
- **`/contiguousstorage dryrun`** tells you how many drawers STRICT would unlink, before you
  switch, without changing anything.
- **No stealing**: a drawer is linked on placement only if the player who placed it could use the
  controller (spawn protection, world border).
- **Two networks side by side never merge**: the first controller keeps its drawers.

![The dry run, in the chat](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/dryrun.jpg)

![Plays well with others](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/header_compat.jpg)

- **Storage Drawers' trims** carry the network out of the box, if both mods are installed.
- **Any block** a pack adds to the `#contiguousstorage:conductors` tag carries it too.
- **Create**: Functional Storage's drawers cannot be moved by contraptions. An Immaterial Drawers
  drawer moved back and forth by a piston was never duplicated in testing.
- **Light on the server**: every network is indexed, so a redstone clock next to your wall costs
  nothing, and the safety net checks a 20,000-drawer network in about 3 ms.
- **Includes Functional Storage's own fix** for a world-loading freeze in Functional Storage 1.5.7,
  applied to 1.5.7 and older only.

![The wall, from the side](https://raw.githubusercontent.com/DrimoZ/ContiguousStorage/main/docs/store-art/wall_angle.jpg)

### Requirements

| | 1.21.1 |
|---|---|
| Loader | NeoForge 21.1+ |
| [Functional Storage](https://www.curseforge.com/minecraft/mc-mods/functional-storage) | 1.21-1.5.7+ |
| [Titanium](https://www.curseforge.com/minecraft/mc-mods/titanium) | required |

Needed on both the client and the server: the trims are blocks.

### FAQ

**Will it break my existing world?** Not in the default mode: ASSIST never removes a link.
STRICT unlinks drawers linked at a distance, so run `/contiguousstorage dryrun` first.

**Do I still need the Linking Tool?** No, but it still works. In STRICT it only keeps drawers that
touch.

**Does it work with land claims?** Spawn protection and the world border are respected. Claim mods
(FTB Chunks, Open Parties and Claims) are not checked yet.

**A drawer moved by a Create contraption doesn't relink.** Only players link by placing. Use the
Linking Tool, or break and place it again.

**Huge networks?** This mod's own work stays small. Functional Storage itself slows down past a few
thousand drawers on one controller, so split very large walls across several controllers.

**Fabric? Other versions?** No Fabric: Functional Storage is NeoForge. Only 1.21.1 for now.

### Permissions

**Modpacks: yes.** No permission needed, no message required, public or private, monetised or not,
on any platform or launcher.

**Credit** is appreciated and never required.

**Forks and addons: yes**, under the MIT terms. Please do not publish a fork under the name
*Contiguous Storage*: the name is not covered by the licence.

**The logo** is the one exception: redistribute it with the mod freely, but do not lift it into
another project.

### Links

Built on Functional Storage by Buuz135 and Rid, and on Titanium. Not affiliated with Functional
Storage or Storage Drawers.
[Source](https://github.com/DrimoZ/ContiguousStorage) ·
[Report a bug](https://github.com/DrimoZ/ContiguousStorage/issues)

<!-- End of the pasted description. -->


## CurseForge

Main category **Addons** (of Functional Storage). Additional: Storage, Utility & QoL.
Slug `contiguous-storage`, free on 21 Oct. 2026.

<https://www.curseforge.com/minecraft/mc-mods/contiguous-storage>

## Modrinth — creating the project

Slug `contiguous-storage` was still free on 21 Oct. 2026. Create at <https://modrinth.com/dashboard/projects>.

| Field | Value |
|---|---|
| Name / slug | `Contiguous Storage` / `contiguous-storage` |
| Summary | the one above |
| Icon | `src/main/resources/logo.png` |
| Categories | **storage**, **utility** |
| Environment | Client: required · Server: required |
| License | Custom: `MIT (code) / All Rights Reserved (assets)`, URL `https://github.com/DrimoZ/ContiguousStorage/blob/main/LICENSE` |
| Links | Source `https://github.com/DrimoZ/ContiguousStorage` · Issues `…/issues` |
| Description | the block above, as-is |
| Gallery | from `run/store-screenshots/` (full PNG): `01_wall`, `03_autolink`, `04_cut`, `05_trims`, `02_wall_angle`, `06_dryrun`; first one featured |

Versions: version number `0.1.0+1.21.1`, changelog from `CHANGELOG.md`, release type as on
CurseForge.

| Loader / game | Required | Optional |
|---|---|---|
| NeoForge / 1.21.1 | `functional-storage`, `titanium` | — |

Modrinth reviews a project before it goes public: submit it with its first version attached.


## Release checklist: 0.1.0

- [x] Slug `contiguous-storage` free on both sites (21 Oct. 2026).
- [x] 36/36 required game tests, in-game scenario 67/0 and 13/0, client without the mod refused
  cleanly (`MANUAL_TESTS.md`).
- [x] GitHub `DrimoZ/ContiguousStorage`, public, branch `main`, tag `v0.1.0+1.21.1`.
- [x] Images in `docs/store-art/`, pushed.
- [ ] `CHANGELOG.md`: replace "unreleased" with the release date, then `./gradlew build`.
- [ ] Paste the description on both sites.
- [ ] One file, release type **Release**, with the `0.1.0+1.21.1` section of `CHANGELOG.md`:

| File | Game version | Loader | Java |
|---|---|---|---|
| `build/libs/contiguousstorage-0.1.0+1.21.1.jar` | 1.21.1 | NeoForge | 21 |

- [ ] Required dependencies on the file: Functional Storage and Titanium.
- [ ] Gallery: `01_wall` (featured), `03_autolink`, `04_cut`, `05_trims`, `02_wall_angle`, `06_dryrun`.
