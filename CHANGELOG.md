# Changelog

**What this file is:** what changed in each release, newest first. **What it is not:** the plan
(`ROADMAP.md`) or the full feature list (`README.md`).

## 0.1.0+1.21.1 — 2026-10-22

### Added
- Drawers link to a Storage Controller by touching it, directly or through other drawers, trims and
  blocks of the `#contiguousstorage:conductors` tag. Faces only. The controller's range still applies.
- Three modes in `contiguousstorage-common.toml`: `OFF`, `ASSIST` (default, never unlinks) and
  `STRICT` (a drawer that no longer touches its network is unlinked).
- `STRICT`: the Linking Tool, in single and area mode, keeps only the drawers that touch, and says how
  many it dropped.
- `STRICT`: a safety net re-checks every loaded network every `validationIntervalTicks` (100).
- Feedback: *"N drawer(s) disconnected"* to the player who cut a chain, smoke on cut drawers (at most
  32 per cut), and a sparkle on drawers linked by placement.
- `/contiguousstorage dryrun`: how many drawers `STRICT` would unlink, in any mode, without changing
  anything.
- Trims: one per Functional Storage wood, addon woods included, and a framed trim. `trimsEnabled`
  turns their recipes, creative tab entries and conduction off.
- Storage Drawers' trims in the conductor tag, as optional entries.
- Auto-link requires that the player who placed the drawer may interact with the controller (spawn
  protection, world border).

### Fixed
- Functional Storage 1.5.7's server freeze while loading a drawer linked from another chunk than its
  controller. This backports Functional Storage's own fix, and applies only to 1.5.7 and older.

### Notes
- Needed on both client and server, because of the trims.
- Land claims are not checked by auto-link.
- A drawer put back by a machine (Create contraption, deployer…) is not linked by placement.
- Tested with Immaterial Drawers 0.2.1 and Create 6.0.10. Create cannot move Functional Storage's
  own drawers.
- Functional Storage's own cost on very large networks is documented in the README, not changed.
