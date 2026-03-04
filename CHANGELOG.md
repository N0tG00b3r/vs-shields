# Changelog — VS Energy Shields

All notable changes to this project are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

> **Note:** Versions 0.0.7 and 0.0.8 were internal builds, never publicly released.
> All changes since 0.0.6 will be included in the upcoming public update.

---

## [0.0.9] — Components, Goggles, Aetheric Anomaly & Polish

> ⚠ **Config notice:** Default values for shield HP, recharge, capacitor, emitter, battery capacity, and the new `anomaly` section have changed. If you have an existing `config/vs_shields.json`, **delete it** before launching so the new defaults are applied. Your customisations will need to be re-entered.

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
- **Aetheric Anomaly** (Phase 1) — procedurally generated floating islands that periodically spawn high in the sky as VS2 ships:
  - **Procedural island generation** — mesa-shaped top with rolling hills, stalactite-like bottom, noise-carved caves with ore veins
  - **6 new blocks** — Aetheric Stone, Cracked Aetheric Stone, Void Moss, Aether Crystal Ore, Resonance Cluster, Concentrated Void Deposit
  - **Dynamic hovering** — anti-gravity physics with spring restoring force, velocity damping, slow Lissajous drift, and gentle sine-wave swaying
  - **Dual timer system** — global lifetime (20 min default) + extraction timer (7 min, starts when player approaches)
  - **4-phase lifecycle** — ACTIVE → EXTRACTION → WARNING (island shakes violently) → DISSOLVING (blocks vanish edge-inward over 45s)
  - **Void Deposit destabilisation** — right-clicking or destroying a Concentrated Void Deposit immediately forces the island into WARNING phase
  - **Admin commands** — `/vs_shields anomaly spawn [x y z]`, `despawn`, `info`, `timer set <seconds>`, `reload`
  - **World persistence** — active anomaly survives server restarts via SavedData; deferred VS2 ship verification prevents false orphan cleanup
  - **Configurable** — 18 settings in the `anomaly` section: spawn interval, timers, island size (40–80 blocks), altitude, physics multipliers

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

### Hotfixes (post-release)

#### Boarding Pod
- **Drilling rotation fix** — Pod no longer flips backwards when drilling into a target hull. Root cause: `VSFixedJoint` used identity quaternions on both sides, which forced the pod's local frame to snap to the target's local frame. Fixed by computing the relative rotation at contact time: `inverse(podWorldRot) × targetWorldRot`, preserving the pod's world orientation at the moment of impact.
- **Blockstate facing fix** — Pod now correctly faces the cockpit FACING direction upon boarding. Previously the blockstate rotation was off, causing a 180° snap.
- **Mount yaw fix** — Player yaw is computed from the cockpit FACING direction before `startRiding()`, preventing the camera from snapping to north on mount.

#### Aetheric Anomaly
- **Incremental spawn** — Island blocks are now placed at 50 blocks/tick instead of all at once, eliminating the physics spike on spawn. The VS2 ship is kept static during the entire build process and released to anti-gravity physics once complete.
- **Client sync fix** — Subsequent block batches in the shipyard now use `setBlock(pos, state, 2)` (flag 2 = SEND_TO_CLIENTS) instead of flag 0, fixing invisible blocks after the first batch.
- **Spatial shuffle** — Block entries are shuffled before storage so the first 50-block batch is spatially diverse, not a single vertical column (HashMap iteration order clusters by BlockPos hash).
- **Offset calculation fix** — World-to-shipyard offset now uses `ctx.toCenter - ctx.fromCenter` from the `AssembleContext` (always valid immediately after assembly) instead of `ship.worldToShip` which may not be fully initialized on the same tick.
- **AnomalyIslandControl** — Anti-gravity and drift physics moved to a VS2 `ShipPhysicsListener` running at 60 Hz on the physics thread, replacing the 20 Hz game-thread approach. Spring-damper + Lissajous drift + gentle sway + WARNING torque all run natively in the physics pipeline.
- **WorldBorder integration** — Anomaly spawn position is now clamped to the world border. Supports [World Border by Serilum](https://modrinth.com/mod/world-border) (config read via reflection) with automatic fallback to vanilla `WorldBorder`. A margin of `maxIslandSize/2 + 10` blocks ensures the entire island fits inside.

#### Aetheric Anomaly — Phase 2 (Protection & Interaction)
- **Ship repulsion** — VS2 ships approaching the anomaly island are pushed away by `AnomalyShipRepulsion.kt`, preventing players from ramming or docking.
- **Projectile absorption** — `ShieldBarrierHandler` now recognizes anomaly ships and silently discards all incoming projectiles (infinite shield).
- **Explosion suppression** — `ShieldDamageHandler` suppresses all explosions originating on the anomaly island.
- **Block placement prevention** — Players cannot place blocks on the anomaly VS2 ship (enforced in `VSShieldsModForge`).
- **Resource mining** — Loot tables added: Aether Crystal Ore drops 1–3 Raw Aether Crystal (Fortune-affected), Resonance Cluster drops 1–2 Resonance Fragment.
- **4 resource items** — Raw Aether Crystal, Refined Aether Crystal (smelted from raw), Void Essence, Resonance Fragment; all registered with item models and lang keys.
- **Void Deposit extraction** — Hold RMB on the Concentrated Void Deposit for a timed extraction (progress HUD bar via `VoidDepositProgressHud`). Exhaustion triggers destabilisation.
- **Periodic aetheric pulse** — Every 30 seconds (`pulseCooldownTicks`) when players are near the island, a knockback + shield-damage pulse fires via `AnomalyPulseHandler`.
- **Resonance Cluster loot fix** — Was incorrectly dropping `void_essence` instead of `resonance_fragment`.

#### Aetheric Anomaly — Phase 3 (Guardians)
- **Guardian spawning** — `AnomalyGuardianManager.kt` spawns hostile mobs on the island during ACTIVE and EXTRACTION phases: Enderman (50%), Phantom (35%), Shulker (15%).
- **Teleport clamping** — `AnomalyGuardianEventHandler.kt` prevents guardian Endermen from teleporting off the island.
- **Phantom orbiting** — Guardian Phantoms orbit around the island center instead of flying away.
- **Custom drops** — `AnomalyGuardianDropHandler.kt` replaces vanilla loot: guardians drop Void Essence, Raw Aether Crystal, or Resonance Fragment.
- **Spawn escalation** — Guardian spawn interval decreases every 3 waves, max mob count increases every 5 waves.
- **Cleanup on dissolution** — All guardian mobs are killed when the island enters DISSOLVING phase.

#### Aetheric Anomaly — Phase 4 (Detection & Items)
- **Aetheric Compass** — new item that detects anomaly islands. Three states: slow spin (no anomaly), points toward island (>500 blocks away), wild erratic spin (≤500 blocks — interference zone). 32-texture animated needle like a vanilla compass. Recipe: Resonance Lens × 4, Void Shard × 4, Compass × 1.
- **Resonance Beacon** — new block that performs a charged scan to reveal exact anomaly coordinates. Requires 500,000 FE + 1 Refined Aether Crystal per scan. 10-second scan progress bar; results display anomaly position and remaining TTL. Recipe: Void Capacitor × 4, Resonance Lens × 4, Stabilized Core × 1.
- **Aetheric Energy Cell** — consumable item that injects **75,000 FE** into a Shield Generator on right-click (configurable via `aethericEnergyCellFE`). Shapeless: Refined Aether Crystal + Energy Cell.
- **Attuned Void Shard** — crafting ingredient for future tier-4 recipes. Shapeless: Refined Aether Crystal + Void Shard.
- **Calibrated Oscillator** — crafting ingredient for future tier-4 recipes. Shapeless: Resonance Fragment + Frequency Oscillator.
- **Config fields** — `anomaly.compassChaosRadius` (500), `anomaly.beaconMaxEnergy` (1M), `anomaly.beaconEnergyInput` (50k), `anomaly.beaconScanCost` (500k), `anomaly.beaconScanTicks` (200), `general.aethericEnergyCellFE` (75k).

#### Aetheric Anomaly — Phase 5 (Polish & Effects)
- **Extraction Timer HUD** — top-center timer "ANOMALY: M:SS" shown to all players within 100 blocks of an active anomaly. Color-coded: white >50%, yellow 25-50%, red <25%, blinking <30s.
- **Spawn beam** — vertical END_ROD + ELECTRIC_SPARK particle column from Y=0 to Y=320 when an anomaly spawns (visible for 30 seconds).
- **Ambient motes** — WITCH + PORTAL particles near the anomaly island when a player is within 100 blocks.
- **Pulse shockwave** — expanding SONIC_BOOM ring on each aetheric pulse.
- **Warning shimmer** — scattered ELECTRIC_SPARK particles during the WARNING phase.
- **Dissolution smoke** — SMOKE + LARGE_SMOKE particles during the DISSOLVING phase.
- **Extraction torque** — mining Aether Crystal Ore on the anomaly island applies a small yaw impulse (±20,000) to the island, causing it to slowly rotate.
- **Ship Analyzer integration** — scanning an anomaly island now shows a special purple panel: "AETHERIC ANOMALY", "Shield: IMPERVIOUS", remaining TTL countdown, and guardian count.

#### Aetheric Anomaly — Block Visuals
- **Updated light levels** — Aether Crystal Ore: 5 → **8**, Resonance Cluster: 8 → **12**, Concentrated Void Deposit: 10 (unchanged).
- **Aether Crystal Ore particles** — ELECTRIC_SPARK sparkle when player is within 3 blocks.
- **Resonance Cluster particles** — 2–3 ELECTRIC_SPARK energy arcs per tick + 25% chance END_ROD glow.
- **Concentrated Void Deposit particles** — PORTAL corona (3/tick), FALLING_OBSIDIAN_TEAR void drip (33%), WITCH purple motes (50%).

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
