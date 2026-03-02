# Changelog — VS Energy Shields

All notable changes to this project are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

> **Note:** Versions 0.0.7 and 0.0.8 were internal builds, never publicly released.
> All changes since 0.0.6 will be included in the upcoming public update.

---

## [0.0.9] — Components, Goggles & Polish

> ⚠ **Config notice:** Default values for shield HP, recharge, capacitor, emitter, and battery capacity have changed. If you have an existing `config/vs_shields.json`, **delete it** before launching so the new defaults are applied. Your customisations will need to be re-entered.

### Added
- **Tactical Goggles** — new headgear item replacing the Tactical Netherite Helm. Equippable in the regular helmet armor slot OR the Curios head slot. Three abilities:
  - **Night Vision** — passive effect applied every 5 seconds while worn (server-side, 15-second duration)
  - **Ship Analyzer HUD** — press **Y** to toggle the analyzer overlay (same HUD as Tactical Helm / handheld Analyzer)
  - **Zoom** — hold **Ctrl** for 4× zoom while goggles are equipped (OptiFine-style, no keybind needed)
  - Custom 3D model rendered on the player's head via Curios renderer and ArmorItem armor layer (brim removed to fix UV artifacts)
  - Recipe: Resonance Lens × 4, Void Capacitor × 4, Ship Analyzer × 1 (center)
- **Crafting component system** — 13 new craftable items used across all mod recipes. Components span three tiers:
  - *Base:* Charged Redstone Crystal (shapeless), Copper Coil, Insulated Wire, Tempered Glass Pane (smelting), Reinforced Plate
  - *Mid:* Signal Board, Resonance Lens, Energy Cell, Hardened Casing (contains Netherite — netherite gate)
  - *Advanced:* Stabilized Core, Frequency Oscillator, Void Shard (dropped by Endermen 5% / Ender Dragon 4–8), Void Capacitor
- **Energy Cell item** — Right-click a Shield Generator to charge it with 25,000 FE (consumed on use). Amount configurable via `energyCellFE`.
- **Void Shard drops** — Endermen drop 1 Void Shard at 5% chance; Ender Dragon drops 4–8 on death. Rates configurable via `voidShardEndermanChance`, `voidShardDragonMin`, `voidShardDragonMax`.
- **Redstone activation** — Redstone signal HIGH activates the shield; LOW deactivates it. Rising/falling edge is detected each server tick via `hasNeighborSignal`. Combines with the existing redstone *output* (signal on hit) — wire a lever to toggle the shield, or hook up a comparator for a damage-triggered alarm that also controls your defences.

### Removed
- **Tactical Netherite Helm** — fully removed from registry, creative tab, recipes, textures, and model. Superseded by Tactical Goggles.

### Changed
- **All crafting recipes overhauled** — All 21 mod recipes now use the new crafting components instead of raw vanilla materials. Upgrade chain enforced: Iron → Diamond → Netherite Shield Generator now requires the previous tier. Netherite-tier and endgame devices require Hardened Casing (which contains Netherite Ingot), enforcing a hard netherite gate.
- **Boarding Pod: new cockpit model** — Replaced solid single-mesh with a two-layer composite model: opaque structural frame + translucent glass panels with two-sided rendering. Crew can now see through cockpit windows from inside.
- **Boarding Pod: player size in cockpit** — Player collision box shrunk to 0.5×0.7 while seated; eye height set to `blockY + 0.7`.
- **Boarding Pod: assembly whitelist** — Any VS Shields mod block adjacent to the pod structure (other than Cockpit and Engine) now blocks assembly with a clear error: *"Pod contains an unsupported block — only Cockpit and Engine are allowed!"* System is extensible for future pod block types via `allowedPodBlocks()`.
- **Boarding Pod: Russian lang keys** — Added missing Russian translations for all boarding pod messages.
- **Solid Projection Module: directional placement** — Block now places facing the player (like a furnace/screen), instead of always facing north.

### Balance
- **Shield HP doubled** across all tiers: Iron 100 → **200**, Diamond 250 → **500**, Netherite 500 → **1000**
- **Shield recharge doubled** across all tiers: Iron 0.5 → **1.0**/tick, Diamond 1.0 → **2.0**/tick, Netherite 2.0 → **4.0**/tick
- **Shield Capacitor** bonus doubled: +50 → **+100** max HP per block
- **Shield Emitter** bonus doubled: +0.5 → **+1.0** HP/tick per block
- **Shield Battery** capacity doubled: 200,000 → **400,000** FE

### Fixed
- **Create: Gunsmithing — Nail Gun and Blaze Gun projectiles** were silently passing through shields without dealing any damage. Root cause: NTGL's `ProjectileEntity` base class extends Minecraft `Entity` directly, not `Projectile` — so `ProjectileImpactEvent` never fired for these entities and the barrier handler's `entity is Projectile` check skipped them. Fixed by adding the `cgs` and `ntgl` namespaces to the `isCbcEntity()` filter in `ShieldBarrierHandler`, putting these projectiles through the same boundary-crossing detection used for CBC shells. All five CGS projectile types (Nail, Steel Nail, Blaze Ball, Incendiary, Spear, Rocket) are now intercepted correctly.
- **Shield crew-fire** — Arrows, CBC cannon shells, and other projectiles fired *from inside* the shield by crew members were incorrectly absorbed by the ship's own shield. Now crew fire passes freely through in both directions. This fix covers both standard MC projectiles (via `Projectile.getOwner()` check) and CBC shells (via `inflate(200)` sanity check on uninitialized `xOld`).
- **Boarding Pod: camera rubber-band at speed** — Camera no longer jags at high velocity. Root cause: old mixin read `seat.x/y/z + ship.worldToShip` each frame, which drifted by ~2 blocks when entity packets and ship packets arrived in different ticks at 20 Hz. Fixed via `COCKPIT_SY_POS EntityDataAccessor` — a constant shipyard position synced once, read by VS2-native `setupWithShipMounted` each render frame.
- **Boarding Pod: forced first-person view** — Game no longer forcibly switches to first-person upon boarding the cockpit.
- **Boarding Pod: flight direction** — Pod now flies toward the cockpit FACING direction (180° blockstate rotation).
- **Boarding Pod: steering accuracy** — Steering now uses `Camera.getLookVector()` instead of raw `getYRot/getXRot`, giving crosshair-accurate guidance regardless of ship physical orientation.
- **Boarding Pod: purple particle on cockpit break** — Particle texture now correctly assigned to the root composite-model element.

### Known Issues
- **CGS Nail Gun** projectiles still pass through shields without dealing damage. Blaze Gun works correctly. Requires runtime debugging to identify the exact entity type registration and boundary-crossing behavior.

---

## [0.0.8] — Boarding Pod (Internal Build)

### Added
- **Boarding Pod** — 2-block multiblock assault craft for boarding enemy ships.
  - Cockpit + Engine placed adjacent assembles into a full **VS2 physics ship** via `BoardingPodAssembler` + `ShipAssembler`.
  - Player rides an invisible `CockpitSeatEntity` (0.3×0.3) inside the VS2 pod ship.
  - **Phases:** AIMING → BOOST (up to 80 ticks, hold Space) → COAST → DRILLING (40 ticks) → BREACH.
  - **Mouse Steering** — pod turns up to 3°/tick toward the player's crosshair during Boost and Coast. Works correctly at any physical ship orientation (VS2-native camera via `ShipMountedToDataProvider`).
  - **Space bar thrust** — hold Space to apply rocket force; pod ramps to ~40 m/s. Release to glide (no drag, gravity applies). Fuel depletes over 4 continuous seconds.
  - **RCS thrusters** — `Space` = upward burst, `C` = downward burst. Available during Boost and Coast.
  - **On impact with a VS2 ship:** Drilling phase — VS2 FixedJoint rigidly locks pod to target hull for 2 seconds (metal-grinding sound, sparks, camera shake). Pod follows target if it moves or rotates.
  - **Hull Breach** — cuts a clean **2×2×4** tunnel at the exact angle of approach.
  - **Solid Module damage** — deals **100 HP** to any active Solid Projection Module on the target.
  - **Trusted status** — passenger receives 200 ticks (10 sec) of trusted status, temporarily bypassing solid barriers.
  - **Fragile** — pod has 10 HP during Boost/Coast; can be shot down. Invulnerable during Drilling.
  - **HUD** — shows current speed (m/s) and Boost fuel bar (green → yellow → red).
  - **Camera** — VS2-native camera via `setupWithShipMounted`; no drift or lag at any speed.
  - **Keybind** — `Fire` key (unbound by default — set in Controls → VS Shields).
  - **Assembly validation** — red error message if cockpit count ≠ 1 or engine count ≠ 1 in the connected pod structure.

### Fixed
- **CBC autocannon self-damage** — Firing CBC autocannons from inside a ship was damaging the ship's own shield. Root cause: `onExplosionStart` was intercepting the muzzle-blast explosion (physically inside the ship's `worldAABB`). Fixed: inner explosions (center inside `worldAABB` without padding) are now ignored.

---

## [0.0.7] — ID + Solid (Internal Build)

### Added
- **Solid Projection Module** — peripheral block (one per ship) that turns the energy shield into a physical wall.
  - 1,000,000 FE buffer; accepts FE cables and Create rotation shaft (500 FE/tick while active).
  - Right-click to set an ACCESS CODE (up to 8 alphanumeric chars, case-sensitive) and activate.
  - Unauthorized entities are pushed back at the shield boundary; foreign ships receive an elastic repulsion impulse.
  - `⛔ SOLID` indicator on the HUD when the barrier is active.
  - GUI statuses: ACTIVE / OFFLINE / GROUNDED / DUPLICATE / NO ENERGY.
  - Configurable: `solidModuleEnergyCost`, `solidModuleMaxEnergy`, `solidModuleEnergyInput`, `shipRepulsionForce`.

- **Frequency ID Card** — programmable access card.
  - Shift+Right-click to open programming screen and enter a code (up to 8 chars, case-sensitive).
  - Carry in inventory, offhand, or a **Curios Charm** slot — passes freely through matching barriers.
  - Stacks up to 8 when codes match.
  - Curios integration (reflection-based, graceful no-op without Curios).

- **Master Key Slot** — Shield Generator GUI gained a dedicated card slot. Insert a Frequency ID Card to allow allied ships carrying a matching card to pass through your solid barrier without repulsion.

- **Shield Renderer: inside/outside culling option** — Added `hideShieldBubbleInside` config key (`general` section, default `false`). When `true`, the hex grid is hidden for crew standing inside the shield. Default behavior (both sides visible) corrected: winding order of sphere triangles was reversed so front faces are correctly outward, enabling `disableCull()` for crew visibility.

- **Config merge fix** — `ShieldConfig.merge()` now backfills missing keys in existing configs without overwriting player settings. Covers all `GeneralConfig` boolean fields (GSON sets absent primitives to `false`, not null — each key checked via `rawGeneral.has()`). New projectile damage entries are added automatically on mod update.

### Changed
- Mod renamed: **VS Energy Shields** → **VS Shields** (display name, creative tab, keybind category).
- Access code length increased from 6 to 8 characters; character set expanded to case-sensitive `[a-zA-Z0-9]`.

---

## [0.0.6] — Public Release Baseline

Last publicly released version.

Features present in 0.0.6:
- Shield Generators (Iron / Diamond / Netherite tiers)
- Shield Capacitor (+50 max HP) and Shield Emitter (+0.5 HP/tick recharge)
- Shield Battery 3×3×3 multiblock — passive absorption (20% per hit) + emergency regen burst below 20% HP
- Shield Jammer 3×3×3 Electronic Warfare multiblock — burns enemy shield FE on ship collision; Disable/Enable control
- Gravity Field Generator — creative-style flight and fall protection inside shield radius
- Ship Analyzer (handheld)
- Gravitational Mine + Gravitational Mine Launcher (variable deployment range, 5-sec cooldown)
- Full mod compatibility: Create Big Cannons (all shell types including Nuke Shell), Create Gunsmithing (projectile + hitscan), Alex's Caves (nuclear bomb + torpedo)
- Dynamic shield bubble — honeycomb hex-grid, color by HP (blue / yellow / red), flicker at low FE
- Redstone output from Shield Generator on hit (signal strength = damage received)
- JSON config (`config/vs_shields.json`) with automatic merge for new keys
