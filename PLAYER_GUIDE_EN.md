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
| **Iron** | 100 | Dynamic | 0.5/tick | 20 | 50,000 |
| **Diamond** | 250 | Dynamic | 1.0/tick | 50 | 200,000 |
| **Netherite** | 500 | Dynamic | 2.0/tick | 100 | 500,000 |

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

A simple addon block with a 3D model. Place it on the same ship to automatically add **+50 HP** to the shield's maximum health. The effect stacks: 4 capacitors = +200 HP.

### Shield Emitter

Place on the same ship to add **+0.5 HP/tick** to the shield's recharge rate. The effect stacks.
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
| Iron shield at 0 HP, battery full (200k FE) | −25,000 FE | 100 HP (fully restored) |
| Diamond shield at 0 HP, battery full | −62,500 FE | 250 HP (fully restored) |
| Netherite shield at 0 HP, battery full | −125,000 FE | 500 HP (fully restored) |
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
[Ender Pearl]    [Netherite Block] [Ender Pearl]
[Netherite Block] [Nether Star]    [Netherite Block]
[Ender Pearl]    [Netherite Block] [Ender Pearl]
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

### Tactical Netherite Helm
An advanced helmet with integrated Ship Analyzer circuitry.
- **Passive HUD:** Simply wearing the helmet provides a constant HUD readout of the ship you are currently looking at (no scanning required).
- **Auto-Glow:** Provides a subtle, constant highlight for shielded generators within line of sight.

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
1. Craft a **Boarding Pod Cockpit** and a **Boarding Pod Engine**.
2. Place them side-by-side on your ship --- the engine must be directly adjacent to the cockpit horizontally.
3. Right-click the **cockpit** to board --- you will be seated inside the pod.
4. Aim with your mouse. A targeting HUD appears showing your yaw/pitch.
5. Press the **Fire** key (unbound by default --- assign it in **Options -> Controls -> VS Shields**).
6. Press **Sneak** at any time to safely dismount without firing.

**Flight phases:**

| Phase | Duration | Description | RCS |
|-------|----------|-------------|-----|
| **Aiming** | Until you fire | Pod is stationary; you control aim | — |
| **Boost** | 40 ticks (2 sec) | Rapid acceleration, near-zero gravity | Active |
| **Coast** | Until impact | Ballistic arc under full gravity | Active |
| **Drilling** | 40 ticks (2 sec) | Locked to target hull; drilling through | — |

**Terminal Magnetic Lock:**

In the final 7 blocks before reaching a ship, the pod automatically steers its velocity perpendicular to the nearest armour face. This guarantees the breach tunnel is always straight — no more staggered "staircase" holes from oblique approaches.

**RCS Thrusters (Reaction Control System):**

During Boost and Coast you can make lateral course corrections using the **strafe keys (A / D)**:

- Each press fires a sideways thruster burst; the pod rotates to face its new heading
- The pod starts every launch with **5 charges** --- unused charges are *not* carried over
- There is a **0.6-second cooldown** (12 ticks) between bursts so you cannot spend all charges instantly
- While in flight the HUD displays charge pips above the hotbar:
  - **Green ●** = ready
  - **Yellow ●** = on cooldown
  - **Grey ○** = spent
- A white cloud particle burst vents from the opposite side of the thrust, and a short mechanical hiss plays

**On impact with a VS2 ship:**

1. **Drilling phase** — the pod locks to the hull for 2 seconds. Metal-grinding sounds, sparks, and a camera shake play. If the target ship moves or rotates during this time, the pod moves with it.
2. **Breach** — the pod cuts a clean **2×2×4** tunnel at the exact angle of approach.
3. Deals **100 HP** to any active **Solid Projection Module** barrier on the target.
4. The passenger receives **200 ticks (10 sec)** of trusted status — they temporarily bypass solid barriers.

**Countermeasures:**
- The pod has **10 HP** during Boost and Coast --- it can be shot down by arrows, cannon shells, or melee
- If the pod is destroyed mid-flight, the passenger falls freely with no teleport assist
- The pod cannot be damaged while Drilling (it is already embedded in the hull)

**Tips:**
- Aim slightly above horizontal for maximum range (the pod follows a proper ballistic trajectory)
- The Terminal Magnetic Lock handles the final alignment — you do not need to hit perfectly straight
- Use RCS early in Boost phase when you still have high velocity --- corrections are most effective then
- Save 1--2 charges for late course correction if the target ship is maneuvering
- The pod does not detonate on non-ship terrain --- it simply breaks, dropping the passenger safely
- The engine block provides thrust; without it, the cockpit cannot be activated

---

## Crafting Recipes

### Generators & Modules

**Iron Shield Generator**
```
[Iron Block] [Redstone]   [Iron Block]
[Iron Block] [Glass]      [Iron Block]
[Iron Block] [Redstone]   [Iron Block]
```

**Shield Capacitor**
```
[Iron Ingot]   [Gold Ingot]      [Iron Ingot]
[Redstone Block] [Redstone Block] [Redstone Block]
[Iron Ingot]   [Gold Ingot]      [Iron Ingot]
```

**Shield Emitter**
```
[Iron Ingot] [Redstone Torch] [Iron Ingot]
[Gold Ingot] [Glass]          [Gold Ingot]
[Iron Ingot] [Redstone Torch] [Iron Ingot]
```

### Shield Battery

**Shield Battery Cell (x4)**
```
[Iron Ingot] [Redstone] [Iron Ingot]
[Redstone]   [Glass]    [Redstone]
[Iron Ingot] [Redstone] [Iron Ingot]
```

**Shield Battery Input**
```
[Gold Ingot] [Diamond]          [Gold Ingot]
[Iron Ingot] [Battery Cell]     [Iron Ingot]
[Gold Ingot] [Diamond]          [Gold Ingot]
```

**Shield Battery Controller**
```
[Echo Shard] [Battery Input] [Echo Shard]
[Battery Input] [Netherite Ingot] [Battery Input]
[Echo Shard] [Battery Input] [Echo Shard]
```

### Electronic Warfare Station (Shield Jammer)

**Shield Jammer Controller**
```text
[Diamond Block] [End Crystal]     [Diamond Block]
[Redstone Block] [Netherite Block] [Redstone Block]
[Diamond Block] [Obsidian]        [Diamond Block]
```

**Shield Jammer Frame (x4)**
```text
[Iron Block] [Obsidian]   [Iron Block]
[Obsidian]   [Iron Block] [Obsidian]
[Iron Block] [Obsidian]   [Iron Block]
```

**Shield Jammer Input**
```text
[Empty]    [Iron Block] [Empty]
[Obsidian] [Jammer Frame] [Obsidian]
[Empty]    [Iron Block] [Empty]
```

### Tools & Weapons

**Ship Analyzer**
```text
[Ender Eye] [Amethyst Shard] [Ender Eye]
[Netherite Ingot] [Spyglass] [Netherite Ingot]
[Redstone] [Compass] [Redstone]
```

**Tactical Netherite Helm**
```text
[Echo Shard] [Spyglass] [Echo Shard]
[Ender Eye] [Netherite Helmet] [Ender Eye]
[Netherite Ingot] [Ship Analyzer] [Netherite Ingot]
```

**Gravitational Mine (x4)**
```text
[Ender Pearl] [Phantom Membrane] [Ender Pearl]
[Blaze Powder] [Iron Ingot] [Blaze Powder]
[Gunpowder] [Iron Ingot] [Gunpowder]
```

**Gravitational Mine Launcher**
```text
[Ender Eye] [Dropper] [Ender Eye]
[Quartz] [Netherite Ingot] [Quartz]
[Redstone] [Piston] [Redstone]
```

### Boarding Pod

**Boarding Pod Cockpit**
```text
[Iron Ingot]   [Glass]        [Iron Ingot]
[Smooth Stone] [Compass]      [Smooth Stone]
[Iron Ingot]   [Redstone]     [Iron Ingot]
```

**Boarding Pod Engine**
```text
[Iron Ingot]   [Blaze Powder] [Iron Ingot]
[Blaze Powder] [Gunpowder]    [Blaze Powder]
[Iron Ingot]   [Blaze Powder] [Iron Ingot]
```

### Solid Projection Module & Frequency ID Card

**Solid Projection Module**
```text
[Obsidian]    [Diamond]        [Obsidian]
[Diamond]     [Ender Eye]      [Diamond]
[Iron Block]  [Redstone Block] [Iron Block]
```

**Frequency ID Card (x1)**
```text
[Empty]        [Empty]     [Empty]
[Iron Nugget]  [Name Tag]  [Iron Nugget]
[Paper]        [Paper]     [Paper]
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

The **Shield Generator** outputs a **redstone signal** when it takes damage — the signal strength is proportional to the damage received. Place a **comparator** next to the generator to wire it into automation circuits (e.g., alert systems, auto-activating backup batteries when under attack).

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
