# VS Energy Shields — Player Guide

## Quick Start

1.  Craft a **Shield Generator** (Iron, Diamond, or Netherite).
2.  Place it on your Valkyrien Skies 2 ship.
3.  Connect a power source (FE) — any pipes/cables from other mods, or a rotation shaft from the Create mod will work.
4.  Right-click the generator and press "Activate" in the GUI.
5.  An ellipsoidal energy shield will appear around your ship.

---

## Blocks

### Shield Generator

The core block. Creates a protective field around the ship. It features a unique, high-poly 3D model for each tier. Three tiers are available:

| Tier | HP | Radius (Coverage) | Regen | FE/tick | FE Capacity |
|------|-----|--------|-------|---------|-------------|
| **Iron** | 200 | Dynamic | 1.0/tick | 20 | 50,000 |
| **Diamond** | 500 | Dynamic | 2.0/tick | 50 | 200,000 |
| **Netherite** | 1000 | Dynamic | 4.0/tick | 100 | 500,000 |

**Rules:**
- Only **one generator per ship**. A second one will show a red "Duplicate" message.
- **Dynamic Radius:** The shield no longer has a fixed size. It automatically scans the physical bounding box bounds of your entire ship and generates a protective dome perfectly wrapped around the hull, with an extra 10-block padding in all directions (5 blocks in vertical directions). Adding more blocks to the ship automatically expands the shield.
- The generator consumes FE every tick. Without power, the shield turns off.
- Shield HP scales with energy level (50% HP at 0% FE, 100% HP at 100% FE).
- After taking damage, recharge starts after a cooldown (5-10 seconds depending on tier).
- If the shield is fully depleted (0 HP), it enters a longer cooldown before it can start regenerating.

**MASTER KEY Slot:**
The generator GUI now has a dedicated slot for a **Frequency ID Card**. Insert your own card here — it will be used for ship-to-ship passthrough: a foreign ship whose generator holds a matching card will not be repelled by your solid barrier. The slot does not change the card's code — it only stores it.

---

### Shield Capacitor

A simple addon block with a 3D model. Place it on the same ship to automatically add **+100 HP** to the shield's maximum health. The effect stacks: 4 capacitors = +400 HP.

### Shield Emitter

Place on the same ship to add **+1.0 HP/tick** to the shield's recharge rate. The effect stacks.
**Note:** Each emitter increases the generator's energy consumption by **50 FE/tick**, but *only* while the shield is actively recharging. If your generator does not have enough FE to support the emitters, recharge will stall.

---

## Shield Battery — Multiblock (3x3x3)

A 3x3x3 multiblock that stores a massive amount of energy and instantly restores shield HP when it takes damage.
**Warning:** Only **ONE** active shield battery per ship! Placing a second one will display a `DUPLICATE` error.

### How to Build

```
Front face (controller side):      Middle layer:      Back layer:
 [C] [C] [C]                       [C] [C] [C]       [C] [C] [C]
 [C] [@] [C]  ← @ = Controller     [C] [C] [C]       [C] [C] [C]
 [C] [C] [C]                       [C] [C] [C]       [C] [C] [C]

[C] = Shield Battery Cell or Shield Battery Input
[@] = Shield Battery Controller (facing outward)
```

1.  Place a **Shield Battery Controller** — this is the center of one face of the 3x3x3 cube.
2.  Fill the remaining **26 positions** with **Shield Battery Cell** and/or **Shield Battery Input** blocks.
3.  The Controller must face outward. The multiblock builds BEHIND it.

### Multiblock Blocks

| Block | Description |
|-------|-------------|
| **Shield Battery Controller** | The brain. Right-click to open the GUI and see the status. |
| **Shield Battery Cell** | A structural block. **Increases the battery's capacity.** |
| **Shield Battery Input** | Accepts FE/SU. **Does not increase capacity.** |

**Tip:** Replace a few `Cell` blocks with `Input` blocks for faster charging. Each `Input` has its own internal 50k FE buffer.

### GUI Status

| Status | Meaning |
|--------|---------|
| **Incomplete** (red) | Not all 26 blocks of the structure are in place. |
| **No Shield** (yellow) | The multiblock is formed, but there is no active shield on the ship. |
| **Ready** (green) | It's working! The battery is charged and ready to restore the shield. |
| **Depleted** (red) | Out of energy. Recharge the battery. |

### How It Works

The battery has two independent mechanisms:

**1. Passive Absorption** *(silent, fires on every hit)*
Every time the shield takes a hit, the battery silently absorbs **20% of that damage** — reducing the effective HP loss. Costs **1500 FE per 1 HP** absorbed. If not enough energy is available, the absorption is skipped. **Note:** Passive absorption is disabled when the shield's HP drops to 1% or lower, allowing the shield to shatter normally rather than becoming stuck at low health.

**2. Emergency Regen** *(with sound, fires when HP is critically low)*
When the shield's HP drops below **20%**, the battery immediately dumps as much energy as possible into restoring HP — calculated by how much FE is currently stored:

> `HP restored = energyStored ÷ 250`  (capped at current HP deficit)

| Scenario | Battery FE | HP restored |
|----------|-----------|-------------|
| Iron shield at 0 HP, battery full (400k FE) | −50,000 FE | 200 HP (fully restored) |
| Diamond shield at 0 HP, battery full | −125,000 FE | 500 HP (fully restored) |
| Netherite shield at 0 HP, battery full | −250,000 FE | 1000 HP (fully restored) |
| Any shield, battery at 50k FE | −50,000 FE | 200 HP |

- **Cooldown:** 30 seconds between emergency bursts — the battery will not fire again until the cooldown expires, even if HP stays below 20%. This prevents the unit from getting stuck in a loop at 0 HP.
- **Sound:** The `shield_regeneration` sound plays only during this emergency burst, not during passive absorption.

---

## Shield Jammer — Electronic Warfare Station

The Shield Jammer is the ultimate Electronic Warfare (EW) station, designed to burn out enemy shield energy upon physical collision (ramming) of ships.

**Warning:** Only **ONE** jammer per ship! Placing a second one will display a `DUPLICATE` error.

### How to Build

A 3x3x3 multiblock built similarly to the Shield Battery:

1. Place the **Shield Jammer Controller** in the center of the front face. It must face outward (towards the enemy).
2. Fill the remaining **26 positions** with **Shield Jammer Frame** and **Shield Jammer Input** blocks.
3. The more Frame blocks used, the higher the maximum energy capacity of the station (up to 3,900,000 FE).
4. Input blocks are necessary to connect external power cables to the Jammer.

### How It Works

- **Scanning:** The Jammer constantly scans the surrounding area and nearby ships.
- **Jamming:** If the physical bounding boxes of the ships intersect, the Jammer activates. It rapidly burns **50,000 FE per tick** from the enemy shield, while consuming **5,000 FE per tick** from its own reserves. This is accompanied by loud sonic boom effects in the air.
- **Direct Damage:** If the enemy shield is depleted to 0 FE, the Jammer begins dealing direct piercing damage (3 HP per tick) to the enemy generator.
- **Cooldown:** During active jamming and cooldown, the Jammer hardware-locks external energy reception. If its internal reserve runs dry (0 FE), it aggressively powers down and enters a forced stasis until it recharges 2,500,000 FE (it can receive power during stasis).
- **Manual Reset:** The Controller GUI has a "Reload" button for a manual, safe 60-second cooldown dump.
- **Disable / Enable:** The Controller GUI also has a "Disable" button that fully shuts the Jammer off and triggers the same 60-second recharge cooldown. Use it to conserve power. Click "Enable" once the cooldown ends to bring it back online.



---

## Gravity Field Generator

A high-energy device that creates an artificial gravity field around your ship, granting special effects to all players inside the shield radius.

**Warning:** Only **ONE** per ship! A second one shows a "DUPLICATE" error.

### How to Use

1. Craft a **Gravity Field Generator**.
2. Place it on your VS2 ship.
3. Connect a powerful FE source (it consumes a lot of energy).
4. Right-click to open the GUI and press **"Activate"**.
5. Toggle the desired effects: **Flight** and/or **Fall Protection**.

### Effects

| Effect | Extra Cost | Description |
|--------|-----------|-------------|
| Base activation | 100 FE/tick | Device running overhead |
| **Flight** | +400 FE/tick | Players inside the field gain creative-style flight (`mayfly`) |
| **Fall Protection** | +100 FE/tick | Players inside the field take no fall damage |
| **Maximum (all on)** | 600 FE/tick | Both effects active simultaneously |

- **Radius:** Matches the ship's shield bubble (ship AABB + shield padding from config).
- **Energy:** 1,000,000 FE buffer. Accepts up to 50,000 FE/tick. Auto-shuts off when empty.
- **Flight:** Grants `mayfly` — players can fly freely as in Creative mode. Removed immediately if the player leaves the field or the device runs out of power.
- **Fall protection:** All fall damage is cancelled for players inside the field.

### Crafting Recipe

**Gravity Field Generator**
```
[Copper Coil]     [Void Capacitor]  [Copper Coil]
[Void Capacitor]  [Stabilized Core] [Void Capacitor]
[Copper Coil]     [Void Capacitor]  [Copper Coil]
```

### GUI

- **Energy bar** — shows current charge.
- **Usage: X FE/t** — live cost based on active effects.
- **Status**: ACTIVE / OFFLINE / GROUNDED / DUPLICATE.
- **[Activate / Deactivate]** — master on/off toggle.
- **[Flight: ON/OFF]** — toggle creative flight for players in field.
- **[Fall Prot: ON/OFF]** — toggle fall damage immunity.

---

## Tools & Equipment

### Ship Analyzer
A handheld scanning device used to identify ships and shielded structures.
- **Scanning:** Hold right-click to scan the area in front of you. 
- **HUD Info:** While scanning, a temporary HUD appears showing the name and distance of the ship you are looking at.
- **Glowing Effect:** Scanned ships and their shield generators will briefly glow through blocks, making them visible to you and your teammates.

### Tactical Goggles
Wearable headgear that can be equipped in the **regular helmet armor slot** or the **Curios head slot**. EPIC rarity, Leather-tier armor.

**Abilities (all work in either slot):**
- **Night Vision** — passive effect applied automatically every 5 seconds while worn (15-second duration, no flicker).
- **Ship Analyzer HUD** — press **Y** to toggle the same analyzer overlay as the handheld Ship Analyzer. Press **Y** again to turn it off. While active, the HUD refreshes every 0.5 seconds.
- **Zoom** — hold **Shift+V** for 4x zoom while goggles are equipped. Release to return to normal FOV.

**Custom 3D model** renders on the player's head — visible in third-person, multiplayer, and Curios slot rendering.

**Crafting Recipe:**
```
[Resonance Lens]  [Void Capacitor]  [Resonance Lens]
[Void Capacitor]  [Ship Analyzer]   [Void Capacitor]
[Resonance Lens]  [Void Capacitor]  [Resonance Lens]
```

### Gravitational Mine
A deployable space-mine designed to destabilize enemy ships.
- **Phases:** Fired at high speed (**Flight**), then enters a 3-second **Arming** phase (clicking sound), and finally stays active (**Armed**).
- **Detonation:** If an Armed mine collides with a ship or shield, it applies massive physical torque and force, causing the ship to spin and drift violently.
- **Fragile:** Mines hitting targets during the Flight phase will break without detonating. They can also be shot and destroyed by arrows.

### Gravitational Mine Launcher
A heavy-duty launcher for deploying Gravitational Mines.
- **Cooldown:** Has a 5-second (100-tick) cooldown between shots to prevent rapid-fire bombardment.
- **Variable Range:** Shift+Right-click to cycle through deployment distances (15, 30, 50, 70 blocks). The mine will stop and arm at exactly this distance from the firing point.

### Solid Projection Module
Turns the energy shield into a physical barrier. One per ship maximum.

**How it works:**
- Connect a power source (FE or Create rotation shaft). Buffer capacity: **1,000,000 FE**, cost: **500 FE/tick** while the barrier is active.
- Right-click to open the GUI. Enter an **ACCESS CODE** (up to 8 characters \, case-sensitive), then press **Activate**.
- While the module is active and powered, the shield becomes a physical wall: unauthorized entities are pushed back at the shield boundary, and foreign ships receive an elastic impulse when approaching.

**Password bypass:**
- Players carrying a **Frequency ID Card** with a matching code (in inventory, offhand, or a Curios Charm slot) **pass through the barrier freely**.
- A foreign ship whose generator holds a matching card in its MASTER KEY slot also **will not be repelled**.

**GUI Statuses:**
| Status | Meaning |
|--------|---------|
| ACTIVE | Barrier is active, consuming energy |
| OFFLINE | Manually disabled |
| GROUNDED | Block is not on a ship |
| DUPLICATE | A second module already exists on this ship |
| NO ENERGY | Buffer is empty |

### Frequency ID Card
A programmable access card. Stacks up to 8 when they share the same code.

**Programming a card:**
1. Hold a blank card in your hand.
2. Press **Shift+Right-click** — a programming screen opens.
3. Enter a code (up to 8 characters \, case-sensitive).
4. Press **Save** or **Enter**.

Carry the programmed card in your inventory, offhand, or a Curios **Charm** slot — you will pass freely through any barrier that uses a matching code.

> **Tip:** Insert your own card into the MASTER KEY slot of your ship's Shield Generator to allow allied ships with matching cards to pass through your barrier unhindered.

---

### Boarding Pod --- 2x1x1 Multiblock

A two-block assault craft for boarding enemy ships.

**How to deploy:**
1. Craft a **Boarding Pod Cockpit** and a **Boarding Pod Engine** — exactly **one of each**.
2. Place them side-by-side on your ship — the engine must be directly adjacent to the cockpit horizontally. More than one cockpit or engine in a connected group, or any other VS Shields block touching the structure, will prevent assembly.
3. Orient your ship so the **cockpit faces the target** — the pod always launches in the cockpit block's **FACING** direction.
4. Right-click the **cockpit** to board. A HUD shows your current yaw/pitch.
5. Press the **Fire** key (unbound by default — assign it in **Options → Controls → VS Shields**).
6. Press **Sneak** at any time to safely dismount without firing.
7. Press **F5** at any time to toggle between first-person and third-person view.

**Flight phases:**

| Phase | Duration | Description |
|-------|----------|-------------|
| **Aiming** | Until you fire | Pod is frozen in place (VS2 static); initial launch direction = cockpit **FACING** |
| **Boost** | Up to 80 ticks (4 sec) | Hold **Space** to fire the rocket; pod ramps up to 40 m/s |
| **Coast** | Until impact | Ballistic arc under full gravity |
| **Drilling** | 40 ticks (2 sec) | Rigidly attached to the target hull via VS2 joint; drilling through |

**Mouse Steering:**

During **Boost** and **Coast** the pod automatically turns toward where your crosshair is pointing — up to **3° per tick** (60°/sec). Works correctly regardless of how the pod ship is physically oriented (VS2-native camera). Simply look at your target and the pod curves toward it. The HUD shows:
- **Speed** — current velocity in m/s at the top of the flight display
- **BOOST fuel bar** — green → yellow → red; empties if you hold Space continuously for 4 seconds

**Hold Space to thrust:**
- While in Boost phase, holding **Space** applies rocket force and consumes fuel
- Release Space to glide at current speed (no deceleration, gravity still applies)
- When fuel runs out the pod transitions to Coast automatically

**On impact with a VS2 ship:**

1. **Drilling phase** — the pod locks rigidly to the hull via a VS2 fixed joint for 2 seconds. Metal-grinding sounds, sparks, and a camera shake play. If the target ship moves or rotates during drilling, the pod follows it exactly.
2. **Breach** — the pod cuts a clean **2×2×4** tunnel at the exact angle of approach.
3. Deals **100 HP** to any active **Solid Projection Module** barrier on the target.
4. The passenger receives **200 ticks (10 sec)** of trusted status — they temporarily bypass solid barriers.

**Countermeasures:**
- The pod has **10 HP** during Boost and Coast — it can be shot down by arrows, cannon shells, or melee
- If the pod is destroyed mid-flight, the passenger falls freely with no teleport assist
- The pod cannot be damaged while Drilling (it is already embedded in the hull)

**Tips:**
- Aim slightly above horizontal for maximum range
- Use short Space bursts early in flight to conserve fuel for final approach corrections
- Keep looking at the target — the mouse steering handles the rest
- The pod does not detonate on non-ship terrain — it simply breaks, dropping the passenger safely
- The engine block provides thrust; without it, the cockpit cannot be activated
- Only **1 cockpit + 1 engine** per pod — extra adjacent blocks of either type, or any other VS Shields mod block touching the structure, will show an error and block assembly

---

## Aetheric Anomaly

Mysterious floating islands that periodically materialize high in the sky. They are full VS2 physics ships — hovering in place with a slow drift and gentle swaying motion.

### How It Works

1. **Spawning:** An island appears automatically every ~60 minutes (configurable) at a random position within 500–1500 blocks of world spawn, altitude 200–250. The spawn position respects world borders — if you use the [World Border by Serilum](https://modrinth.com/mod/world-border) mod or vanilla world border, the island will always appear inside the playable area. The island builds itself incrementally (50 blocks/tick) over a few seconds; during construction the VS2 ship is frozen in place to prevent drifting.
2. **Exploration:** Fly up and land on the island. It has a mesa-shaped top with rolling hills, a tapered stalactite-like bottom, and caves inside. Mine the structural Aetheric Stone blocks (requires **iron pickaxe or better**) to explore.
3. **Ores:** Inside you'll find **Aether Crystal Ore** (12–20 veins), **Resonance Cluster** (3–5, deep inside, requires diamond pickaxe), and **Concentrated Void Deposit** (1, near the center).
4. **Timers:** The island has two timers:
   - **Global lifetime:** 20 minutes from spawn (the island dissolves if nobody visits)
   - **Extraction timer:** 7 minutes, starts when a player gets within 50 blocks
5. **Destabilisation:** When you **right-click** or **destroy** the Concentrated Void Deposit, the island immediately enters the WARNING phase — get off quickly!

### Lifecycle Phases

| Phase | Duration | What Happens |
|-------|----------|-------------|
| **ACTIVE** | Until player approaches or global timer expires | Island hovers peacefully, drifting slowly |
| **EXTRACTION** | 7 minutes (or until global timer expires) | Player detected — extraction timer running |
| **WARNING** | 60 seconds | Island shakes violently (strong random torque) |
| **DISSOLVING** | 45 seconds | Blocks vanish from edges inward; island disappears |

### Island Blocks

| Block | Hardness | Tool Required | Notes |
|-------|----------|--------------|-------|
| **Aetheric Stone** | 50 | Iron+ pickaxe | Main structural block |
| **Cracked Aetheric Stone** | 40 | Iron+ pickaxe | Found on cave ceilings |
| **Void Moss** | 0.3 | Any | Thin carpet on top surface |
| **Aether Crystal Ore** | 15 | Iron+ pickaxe | Glows (light level 8), sparkle particles within 3 blocks |
| **Resonance Cluster** | 30 | Diamond+ pickaxe | Glows (light level 12), energy arc particles + glow |
| **Concentrated Void Deposit** | Unbreakable | — | Glows (light level 10), dark corona + void drip particles |

### Resource Mining

The island contains valuable resources:

| Source | Drop | Tool | Notes |
|--------|------|------|-------|
| **Aether Crystal Ore** | 1–3 Raw Aether Crystal | Iron+ pickaxe | Fortune-affected |
| **Resonance Cluster** | 1–2 Resonance Fragment | Diamond+ pickaxe | Found deep inside / on cave ceilings |
| **Concentrated Void Deposit** | Void Essence (via extraction) | — | Hold RMB to extract; progress bar shown on HUD |
| **Guardian mobs** | Void Essence, Raw Aether Crystal, Resonance Fragment | — | Custom drops from island guardians |

**Raw Aether Crystal** can be smelted into **Refined Aether Crystal** in a furnace.

### Void Deposit Extraction

The Concentrated Void Deposit at the island's core can be mined by **holding right-click**. A progress bar appears on your HUD. When the deposit is exhausted, the island immediately enters the WARNING phase — be ready to evacuate!

### Periodic Aetheric Pulse

While players are on or near the island, the anomaly releases an **aetheric pulse** every ~30 seconds. The pulse:
- Knocks back all nearby players
- Deals damage to nearby shields
- Serves as a warning that the island's energy is unstable

### Guardian Mobs

The island is defended by hostile mobs that spawn in escalating waves:

| Mob | Chance | Behaviour |
|-----|--------|-----------|
| **Enderman** | 50% | Teleport-clamped to the island — cannot escape |
| **Phantom** | 35% | Orbits around the island center instead of flying away |
| **Shulker** | 15% | Stationary turret; fires homing projectiles |

- Spawn rate **escalates** over time: interval decreases every 3 waves, max mob count increases every 5 waves
- Guardians drop **custom loot**: Void Essence, Raw Aether Crystal, or Resonance Fragment (vanilla drops are replaced)
- All guardians are **killed automatically** when the island enters DISSOLVING phase

### Protection Mechanics

The anomaly island has built-in defenses:
- **Ship repulsion** — VS2 ships approaching the island are pushed away
- **Projectile absorption** — All incoming projectiles are silently destroyed
- **Explosion suppression** — Explosions on the island deal no block damage
- **Block placement prevention** — Players cannot place blocks on the anomaly ship

### Anomaly Detection Tools

#### Aetheric Compass

A handheld compass that detects Aetheric Anomalies. Hold it in either hand and watch the needle:

| State | Condition | Needle Behavior |
|-------|-----------|-----------------|
| **Searching** | No anomaly active | Slow steady spin |
| **Signal Detected** | Anomaly active, >500 blocks away | Points toward the anomaly |
| **Interference!** | Anomaly active, ≤500 blocks away | Wild erratic spin |

The compass uses 32 rotated needle textures — the animation works just like a vanilla compass. The interference radius (500 blocks) is configurable via `anomaly.compassChaosRadius`.

**Recipe:**
```
[Resonance Lens] [Void Shard]     [Resonance Lens]
[Void Shard]     [Compass]        [Void Shard]
[Resonance Lens] [Void Shard]     [Resonance Lens]
```

#### Resonance Beacon

A scanning station that reveals the exact coordinates and remaining time of an active anomaly.

**How to use:**
1. Place the beacon and connect FE power (1,000,000 FE buffer, accepts up to 50,000 FE/tick).
2. Open the GUI (right-click).
3. Insert a **Refined Aether Crystal** into the crystal slot.
4. Ensure the beacon has at least **500,000 FE** stored.
5. Click **SCAN** — a 10-second progress bar fills.
6. Result appears on screen: `"Anomaly at X, Y, Z — Time: M:SS"` or `"No anomaly detected"`.

Each scan consumes 500,000 FE + 1 Refined Aether Crystal.

**Recipe:**
```
[Void Capacitor]  [Resonance Lens]  [Void Capacitor]
[Resonance Lens]  [Stabilized Core] [Resonance Lens]
[Void Capacitor]  [Resonance Lens]  [Void Capacitor]
```

#### Extraction Timer HUD

When you are within 100 blocks of an active anomaly, a countdown timer appears at the top-center of your screen: **"ANOMALY: M:SS"**. Colors change as time runs out:
- **White** — more than 50% time remaining
- **Yellow** — 25–50% remaining
- **Red** — less than 25% remaining
- **Blinking** — less than 30 seconds remaining

#### Anomaly Particle Effects

| Effect | When | Particles |
|--------|------|-----------|
| **Spawn beam** | Anomaly spawns | Vertical light column (END_ROD + sparks), 30 seconds |
| **Ambient motes** | Player within 100 blocks | Purple/portal particles floating near the island |
| **Pulse shockwave** | Each aetheric pulse | Expanding SONIC_BOOM ring |
| **Warning shimmer** | WARNING phase | Scattered electric sparks around the island |
| **Dissolution smoke** | DISSOLVING phase | Dense smoke as blocks vanish |

#### Extraction Torque

When you mine **Aether Crystal Ore** on the anomaly island, each block mined applies a small rotational kick to the island. The island slowly starts spinning as you extract its resources — be careful not to lose your footing!

### Derivative Items

| Item | Type | Recipe | Notes |
|------|------|--------|-------|
| **Aetheric Energy Cell** | Consumable | Shapeless: Refined Aether Crystal + Energy Cell | Right-click Shield Generator → +75,000 FE |
| **Attuned Void Shard** | Ingredient (stack 16) | Shapeless: Refined Aether Crystal + Void Shard | For future tier-4 recipes |
| **Calibrated Oscillator** | Ingredient (stack 16) | Shapeless: Resonance Fragment + Freq. Oscillator | For future tier-4 recipes |

### Admin Commands

All commands require permission level 2 (op).

| Command | Description |
|---------|-------------|
| `/vs_shields anomaly spawn` | Spawn island at random position |
| `/vs_shields anomaly spawn <x> <y> <z>` | Spawn at specific coordinates |
| `/vs_shields anomaly despawn` | Immediately remove active island |
| `/vs_shields anomaly info` | Show position, phase, timers, block count |
| `/vs_shields anomaly timer set <seconds>` | Override remaining global TTL |
| `/vs_shields anomaly reload` | Reload config from file |

---

## Crafting Components

All recipes in VS Shields use custom intermediate components instead of raw vanilla materials. This creates a structured progression path.

### Base Components (Tier 1)

| Component | Recipe | Yields |
|-----------|--------|--------|
| **Charged Redstone Crystal** | Shapeless: Redstone Block + Amethyst Shard + Glowstone Dust | 1 |
| **Copper Coil** | 8× Copper Ingot around Iron Ingot | 2 |
| **Insulated Wire** | 3× Copper Ingot over 3× Leather | 6 |
| **Tempered Glass Pane** | Smelt Glass Pane in furnace | 1 |
| **Reinforced Plate** | Iron Ingot, Iron Block, Obsidian (3×3) | 2 |

### Mid Components (Tier 2)

| Component | Key Ingredients | Yields |
|-----------|----------------|--------|
| **Signal Board** | Insulated Wire, Charged Redstone Crystal, Gold Ingot, Redstone Torch, Quartz | 1 |
| **Resonance Lens** | 4× Amethyst Shard around Tempered Glass Pane | 1 |
| **Energy Cell** | Copper Coil, Charged Redstone Crystal, Redstone Block, Iron Block | 1 |
| **Frequency Oscillator** | Signal Board, Copper Coil, Amethyst Shard, Quartz | 1 |

### Advanced Components (Tier 3)

| Component | Key Ingredients | Yields | Notes |
|-----------|----------------|--------|-------|
| **Hardened Casing** | Reinforced Plate, Obsidian, **Netherite Ingot** | 1 (stack 4) | Netherite gate for all endgame recipes |
| **Stabilized Core** | Charged Redstone Crystal, Ender Pearl, Echo Shard, **Nether Star** | 1 (stack 1) | Requires Wither kill |
| **Void Shard** | *Drop only* — Enderman (5% chance, 1 shard), Ender Dragon (4–8 shards) | — (stack 16) | Drop rates configurable |
| **Void Capacitor** | Void Shard, Energy Cell, Stabilized Core, Echo Shard | 1 (stack 1) | Ultimate endgame component |

### Special: Energy Cell

Right-click a **Shield Generator** with an Energy Cell to instantly inject **25,000 FE** into it. The cell is consumed on use. The FE amount is configurable in `config/vs_shields.json` → `general.energyCellFE`.

---

## Crafting Recipes

### Generators & Modules

**Iron Shield Generator**
```
[Reinforced Plate] [Copper Coil]       [Reinforced Plate]
[Copper Coil]      [Tempered Glass]    [Copper Coil]
[Reinforced Plate] [Copper Coil]       [Reinforced Plate]
```

**Diamond Shield Generator** *(upgrade — requires Iron Generator)*
```
[Diamond]       [Resonance Lens] [Diamond]
[Energy Cell]   [Iron Generator] [Energy Cell]
[Diamond]       [Resonance Lens] [Diamond]
```

**Netherite Shield Generator** *(upgrade — requires Diamond Generator)*
```
[Void Capacitor]  [Hardened Casing]    [Void Capacitor]
[Hardened Casing] [Diamond Generator]  [Hardened Casing]
[Void Capacitor]  [Hardened Casing]    [Void Capacitor]
```

**Shield Capacitor**
```
[Copper Coil]      [Charged Redstone] [Copper Coil]
[Reinforced Plate] [Reinforced Plate] [Reinforced Plate]
[Copper Coil]      [Charged Redstone] [Copper Coil]
```

**Shield Emitter**
```
[Copper Coil]     [Insulated Wire]    [Copper Coil]
[Insulated Wire]  [Tempered Glass]    [Insulated Wire]
[Copper Coil]     [Insulated Wire]    [Copper Coil]
```

### Shield Battery

**Shield Battery Cell (x4)**
```
[Insulated Wire]    [Charged Redstone] [Insulated Wire]
[Charged Redstone]  [Tempered Glass]   [Charged Redstone]
[Insulated Wire]    [Charged Redstone] [Insulated Wire]
```

**Shield Battery Input**
```
[Energy Cell]    [Insulated Wire]  [Energy Cell]
[Insulated Wire] [Battery Cell]   [Insulated Wire]
[Energy Cell]    [Insulated Wire]  [Energy Cell]
```

**Shield Battery Controller**
```
[Echo Shard]     [Battery Input]    [Echo Shard]
[Battery Input]  [Hardened Casing]  [Battery Input]
[Echo Shard]     [Battery Input]    [Echo Shard]
```

### Electronic Warfare Station (Shield Jammer)

**Shield Jammer Frame (x4)**
```
[Reinforced Plate] [Obsidian]         [Reinforced Plate]
[Obsidian]         [Reinforced Plate] [Obsidian]
[Reinforced Plate] [Obsidian]         [Reinforced Plate]
```

**Shield Jammer Input**
```
[     ]     [Reinforced Plate]   [     ]
[Obsidian]  [Jammer Frame]      [Obsidian]
[     ]     [Reinforced Plate]   [     ]
```

**Shield Jammer Controller**
```
[Hardened Casing]    [Freq. Oscillator]  [Hardened Casing]
[Freq. Oscillator]   [Stabilized Core]  [Freq. Oscillator]
[Hardened Casing]    [Freq. Oscillator]  [Hardened Casing]
```

### Gravity Field Generator

```
[Copper Coil]     [Void Capacitor]  [Copper Coil]
[Void Capacitor]  [Stabilized Core] [Void Capacitor]
[Copper Coil]     [Void Capacitor]  [Copper Coil]
```

### Tools & Weapons

**Ship Analyzer**
```
[Freq. Oscillator] [Resonance Lens]   [Freq. Oscillator]
[Resonance Lens]   [Signal Board]     [Resonance Lens]
[Freq. Oscillator] [Resonance Lens]   [Freq. Oscillator]
```

**Tactical Goggles**
```
[Resonance Lens]  [Void Capacitor]  [Resonance Lens]
[Void Capacitor]  [Ship Analyzer]   [Void Capacitor]
[Resonance Lens]  [Void Capacitor]  [Resonance Lens]
```

**Gravitational Mine (x4)**
```
[Charged Redstone]  [Freq. Oscillator] [Charged Redstone]
[Freq. Oscillator]  [Iron Ingot]       [Freq. Oscillator]
[Charged Redstone]  [Freq. Oscillator] [Charged Redstone]
```

**Gravitational Mine Launcher**
```
[Copper Coil]      [Reinforced Plate] [Copper Coil]
[Reinforced Plate] [Signal Board]     [Reinforced Plate]
[Copper Coil]      [Reinforced Plate] [Copper Coil]
```

### Boarding Pod

**Boarding Pod Cockpit**
```
[Reinforced Plate] [Tempered Glass]  [Reinforced Plate]
[Copper Coil]      [Compass]         [Copper Coil]
[Reinforced Plate] [Redstone]        [Reinforced Plate]
```

**Boarding Pod Engine**
```
[Reinforced Plate] [Copper Coil]      [Reinforced Plate]
[Copper Coil]      [Gunpowder]        [Copper Coil]
[Reinforced Plate] [Copper Coil]      [Reinforced Plate]
```

### Solid Projection Module & Frequency ID Card

**Solid Projection Module**
```
[Obsidian]        [Hardened Casing]  [Obsidian]
[Hardened Casing] [Ender Eye]        [Hardened Casing]
[Copper Coil]     [Redstone Block]   [Copper Coil]
```

**Frequency ID Card (x8)**
```
[              ] [              ] [              ]
[Insulated Wire] [Signal Board]  [Insulated Wire]
[Paper]          [Paper]         [Paper]
```

---

## Energy

### Forge Energy (FE)

Connect any FE source to a generator or battery input:
- Thermal Expansion ducts
- Mekanism cables
- Flux Networks
- Any other mod with `IEnergyStorage`.

### Create SU (Stress Units)

Place a **Create rotation shaft** next to a **Shield Generator**, **Shield Battery Input**, or **Gravity Field Generator**. Rotation speed is converted automatically to FE via mechanical coupling:
- **1 FE/tick per 1 RPM**
- A shaft at 256 RPM = 256 FE/tick
- Works from all 6 sides.

---

## Damage Mechanics

- **Projectile Barrier (Force Field):** The shield acts as a physical force field against projectiles (arrows, cannonballs).
  - **External Threats:** Any projectile striking the shield from the outside is intercepted and destroyed at the shield's boundary.
  - **Friendly Fire Protection:** Projectiles fired from *inside* the shield can freely exit. However, if an internal projectile attempts to strike the ship's own blocks, the shield intercepts it at the hull, preventing friendly fire damage.
- **Explosions (TNT, Creepers, Nukes):** If the center of an explosion is inside an active shield, the explosion is **completely absorbed**. The shield takes damage, but all blocks and entities are protected.

---

## Mod Compatibility

### Create: Big Cannons (CBC)

All CBC projectiles are intercepted at the shield boundary. Damage scales by shell type:

| Projectile | Shield Damage |
|------------|--------------|
| Autocannon round | 8 HP |
| Machine gun bullet | 8 HP |
| Smoke Shell | 10 HP |
| Mortar Stone | 20 HP |
| Bag of Grapeshot | 30 HP |
| Drop Mortar Shell | 40 HP |
| Solid Shot / Cannonball | 50 HP |
| Shrapnel Shell | 55 HP |
| Fluid Shell | 55 HP |
| HE Shell | 60 HP |
| AP Shot / AP Shell | 80 HP |
| **Nuke Shell** *(CBC Nukes addon)* | **500 HP** + nuclear flash |

Explosions from cannon shells are also absorbed (formula: `power² × 5.5`).
When a Nuke Shell is intercepted by the shield, the full nuclear explosion visual fires at the impact point on the shield boundary.

### Create: Gunsmithing (CGS)

**Projectile weapons** (Nailgun, Launcher, Blazegun, Incendiary) are intercepted at the shield boundary:

| Projectile | Shield Damage |
|------------|--------------|
| Nail / Steel Nail | 6 / 8 HP |
| Blaze Ball | 8 HP |
| Incendiary | 12 HP |
| Spear | 20 HP |
| Rocket | 40 HP |

**Hitscan weapons** (Gatling, Revolver, Flintlock, Shotgun) are intercepted server-side. The shot is canceled when the raycast crosses the shield boundary. Shooters *inside* the shield can fire outward freely.

| Weapon | Shield Damage per trigger |
|--------|--------------------------|
| Gatling gun | 4 HP/bullet |
| Revolver | 8 HP |
| Flintlock (ball) | 15 HP |
| Shotgun (burst) | 16 HP |

> All values above are configurable in `config/vs_shields.json`.

---

## Visual & Audio Effects

### Shield Appearance
- **Honeycomb Pattern:** The shield renders as a sci-fi hexagonal energy grid that slowly rotates around the ship.
- **Color Scales with HP:**
  - **Blue** — HP > 50% (healthy)
  - **Yellow** — HP 25–50% (damaged)
  - **Red** — HP < 25% (critical)
- **Low Energy Distortions:** If the generator's FE drops below 20%, the shield will begin to violently flicker and glitch, shifting to a red-orange hue.
- **Inside view:** By default the hex grid is visible from both sides — crew members on the ship can see it around them. Set `hideShieldBubbleInside: true` in the config to hide it for crew.

### Impact & Destruction
- **Hit Sparks:** Any projectile or explosion striking the shield creates a burst of electrical sparks at the point of impact. Heavier hits produce larger bursts.
- **Shield Break:** When HP reaches 0, the shield shatters in a 1-second animation with sparks flying outward.

### Sounds

| Event | Sound |
|-------|-------|
| Shield active (continuous) | Low ambient hum, audible up to ~48 blocks, fades with distance |
| Projectile / explosion hit | Electrical impact crackle |
| Shield destroyed (HP → 0) | Loud electrical discharge |
| Shield activated | Rising power-up tone |
| Shield deactivated | Fading power-down click |
| Battery regen (Shield Battery) | Short energy pulse |

### HUD
When inside the shield bubble, an HP bar appears at the top of your screen.

If a Solid Projection Module is active on your ship, a **`⛔ SOLID`** indicator appears in the top-right corner of your HUD.

### Generator GUI
Right-click the generator to see:
- HP bar (color-coded)
- FE bar (orange)
- Status: Active / Inactive
- Activate / Deactivate button

---

## Redstone Integration

The **Shield Generator** supports both redstone **input** and **output**.

### Redstone Input — Activation Control

Apply a **redstone signal** to the Shield Generator to control shield activation:

- **Signal HIGH** → shield **activates**
- **Signal LOW** → shield **deactivates**

The generator detects the rising and falling edge each server tick. Use a lever for a simple on/off switch, a comparator output to react to another block's state, or any other redstone circuit.

> **Tip:** This interacts with the GUI toggle — the shield state follows whichever control acted last. If you wire up redstone control, leave the GUI toggle as-is.

### Redstone Output — Hit Detection

The generator also **emits** a redstone signal when struck. Signal strength is proportional to the damage received. Place a **comparator** next to the generator to wire it into automation circuits (e.g., alert systems, auto-activating backup generators or lights when under attack).

### Combining Both

You can use both at the same time. For example: route the generator's output signal through an inverter (NOT gate) back into its input — the shield shuts off for one second every time it's hit (emergency-vent behaviour).

---

## Configuration

All settings are stored in `config/vs_shields.json`, generated automatically on first launch. Missing keys are back-filled on the next server start, so updating the mod never breaks your existing config.

### `general` — visual & gameplay options

| Key | Default | Description |
|-----|---------|-------------|
| `shieldPadding` | `10.0` | Extra blocks added to every side of the ship AABB when sizing the shield sphere |
| `showShieldBubble` | `true` | Set to `false` to hide the hex bubble completely for all players (damage/logic unaffected) |
| `hideShieldBubbleInside` | `false` | Set to `true` to hide the bubble for crew members standing inside it (outside view is unaffected) |
| `syncIntervalTicks` | `10` | How often (in ticks) the server sends shield HP/state updates to clients |
