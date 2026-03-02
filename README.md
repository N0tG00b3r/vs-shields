# VS Shields

**Energy shield systems for [Valkyrien Skies 2](https://modrinth.com/mod/valkyrien-skies) ships.**

Minecraft 1.20.1 · Forge · Requires VS2 + Architectury API

---

![Active Shield](https://cdn.modrinth.com/data/cached_images/e6ef52dcc76da17970f8160a2dd0552ec7d60490.jpeg)

Wrap your VS2 warship in a procedural energy shield that stops projectiles, absorbs explosions, and pulses with a honeycomb glow. Add a battery multiblock for emergency regen, an electronic warfare jammer to burn out enemy shields on collision, a gravity field generator to grant your crew flight and fall immunity, and deploy gravitational mines to send enemy ships into a spin.

---

## Features at a glance

- **Dynamic shield bubble** — automatically conforms to the full size of your ship; grows as you add blocks
- **Honeycomb visual** — slowly rotating hex-grid that shifts from blue → yellow → red as HP drops, with violent flicker at low energy
- **Three tiers** — Iron / Diamond / Netherite with escalating HP, recharge, and FE costs
- **Capacitors & emitters** — stackable addon blocks for more HP and faster recharge
- **Shield Battery multiblock** — 3×3×3 structure that silently absorbs 20% of every hit, then dumps an emergency HP restore when you're near death
- **Shield Jammer** — 3×3×3 electronic warfare station that burns out enemy shield energy when your ships collide
- **Gravity Field Generator** — grants creative-style flight and fall protection to your crew inside the shield radius
- **Ship Analyzer** — handheld scanner that highlights ship systems through walls and shows a tactical HUD
- **Tactical Goggles** — wearable headgear (helmet slot or Curios head slot) with night vision, ship analyzer HUD (Y toggle), and 4× zoom (Shift+V)
- **Gravitational Mine + Launcher** — deployable space-mines that apply massive physical torque to enemy ships on detonation
- **Redstone I/O** — Signal **in** activates/deactivates the shield; signal **out** pulses when struck — combine both sides for fully automated defence logic
- **Solid Projection Module** — turns the energy shield into a physical barrier; access controlled by programmable **Frequency ID Cards** (up to 8-char codes, Curios-compatible)
- **Boarding Pod** — 2-block multiblock assault craft that assembles into a full VS2 physics ship; aim with the mouse, hold **Space** to fire the rocket booster, steer mid-flight by looking where you want to go (crosshair-accurate steering at any ship rotation); breaches a 2×2×4 tunnel into the target hull
- **Crafting component system** — 13 custom intermediate items (Charged Redstone Crystal, Copper Coil, Void Shard, etc.) form a three-tier progression (Base → Mid → Advanced); netherite is gated behind Hardened Casing, generators upgrade Iron → Diamond → Netherite
- **Energy Cell** — consumable item that instantly charges a Shield Generator with 25,000 FE on right-click
- **Void Shard** — rare drop from Endermen (5%) and Ender Dragon (4–8); required for endgame Void Capacitor
- **Forge Energy & Create SU** — power from any FE cable, or directly from Create rotation shafts
- **Mod compatibility** — full damage tables for Create: Big Cannons (all shell types including Nuke Shell) and Create: Gunsmithing (projectile + hitscan weapons)
- **JSON config** — every damage value, energy cost, recharge rate, and visual option is configurable in `config/vs_shields.json`

---

## Blocks & Systems

### Shield Generator

The core block. Place one on your VS2 ship, supply FE, and right-click to activate. The shield wraps the ship's bounding box with a 10-block padding on all sides.

| Tier | Max HP | Regen | FE/tick | FE Capacity |
|------|--------|-------|---------|-------------|
| **Iron** | 200 | 1.0/tick | 20 | 50,000 |
| **Diamond** | 500 | 2.0/tick | 50 | 200,000 |
| **Netherite** | 1000 | 4.0/tick | 100 | 500,000 |

- One generator per ship — a second shows a **Duplicate** warning
- HP scales with energy level (50% HP at 0% FE, 100% at full charge)
- Recharge starts after a cooldown (5–10 sec depending on tier); if the shield is fully depleted, a longer cooldown applies
- **Redstone input** — signal HIGH activates the shield; signal LOW deactivates it (rising/falling edge detection)
- **Redstone output** — pulses a signal when struck; wire a comparator to trigger alarm systems or backup logic

### Shield Capacitor

Adds **+100 max HP** to the ship's shield. Stacks: four capacitors = +400 HP.

### Shield Emitter

Adds **+1.0 HP/tick** recharge. Stacks. Each emitter costs **+50 FE/tick** while the shield is recharging.

---

### Shield Battery — 3×3×3 Multiblock

A massive energy buffer that automatically restores shield HP in two modes.

**Building:**
1. Place a **Shield Battery Controller** facing outward — it becomes the center of one face
2. Fill the remaining 26 positions with **Battery Cell** and/or **Battery Input** blocks
3. Use **Input** blocks where you want to connect FE cables (each has its own 50k FE buffer)

| Mode | Trigger | Effect | Cost |
|------|---------|--------|------|
| **Passive Absorption** | Every hit, silently | Restores 20% of absorbed damage | 1,500 FE / HP |
| **Emergency Regen** | HP drops below 20% | Dumps all stored energy: `HP = FE ÷ 250` | 250 FE / HP, 30 sec cooldown |

> One battery per ship. Passive absorption disables itself at ≤1% HP so the shield can shatter normally.

---

### Shield Jammer — Electronic Warfare Station

A 3×3×3 EW station that punishes enemy ships for getting close.

**How it works:** When ship bounding boxes overlap, the Jammer activates — it burns **50,000 FE/tick** from the enemy shield while consuming **5,000 FE/tick** from its own reserves. If the enemy shield runs dry, the Jammer deals 3 HP/tick directly to their generator. Sonic boom particles fill the air.

- Capacity scales with the number of Jammer Frame blocks used (up to **3,900,000 FE**)
- When the Jammer's own reserves hit 0, it hard-locks and reboots until it reaches 2,500,000 FE
- **Disable/Enable** button in the GUI for manual power management
- **Reload** button for a clean 60-second cooldown dump
- One jammer per ship

---

### Gravity Field Generator

Creates a ship-wide artificial gravity zone covering the same area as the shield bubble.

| Effect | Extra Cost | Description |
|--------|-----------|-------------|
| Base activation | 100 FE/tick | Device running |
| **Flight** | +400 FE/tick | Grants creative-style `mayfly` to all players inside the field |
| **Fall Protection** | +100 FE/tick | Cancels all fall damage inside the field |

- 1,000,000 FE buffer; accepts up to 50,000 FE/tick
- Auto-shuts off when empty; immediately removes flight from players who leave the field
- One generator per ship

---

## Tools & Equipment

### Ship Analyzer
Hold right-click to scan ships in front of you. A tactical HUD appears showing shield HP, energy, crew count, turret positions, and detected gravitational mines. Scanned blocks glow through walls.

### Tactical Goggles
Wearable headgear that fits in the regular **helmet armor slot** or the **Curios head slot**. Three abilities:

- **Night Vision** — passive effect applied automatically while worn
- **Ship Analyzer HUD** — press **Y** to toggle the same analyzer overlay as the handheld Ship Analyzer
- **Zoom** — hold **Shift+V** for 4× zoom

Custom 3D goggles model renders on the player's head. Recipe: Resonance Lens × 4, Void Capacitor × 4, Ship Analyzer × 1.

### Gravitational Mine
A deployable space-mine that hangs in midair after arming and applies massive physical torque to any ship it touches — sending it spinning and drifting.

- **Phases:** Flight → Pre-Armed (3 sec clicking sound) → Armed (hovers, slowly rotates)
- **Detonation:** Collision with a ship or shield applies a large force impulse (configurable)
- **Fragile:** Can be destroyed mid-flight by projectiles; mines that strike a ship during the Flight phase break without detonating
- WAILA-style name tag shows arming progress and status
- Explosion effects: portal vortex, electric sparks, smoke, and a custom detonation sound

### Gravitational Mine Launcher
Heavy-duty launcher that fires Gravitational Mines.

- **Cooldown:** 5 seconds between shots
- **Variable Range:** Shift+Scroll to cycle deployment distance (15 / 30 / 50 / 70 blocks); the mine arms at exactly that distance from the launch point
- Consumes one mine from the inventory per shot (free in Creative)

---

### Solid Projection Module

A peripheral block (one per ship) that turns the energy shield into a physical wall.

- Connect FE or Create rotation shaft (**1,000,000 FE** buffer, **500 FE/tick** while active)
- Right-click to open GUI; enter an **ACCESS CODE** (up to 8 chars, case-sensitive), then Activate
- While active: unauthorised entities are pushed back at the shield boundary; foreign ships receive an elastic repulsion impulse when approaching
- Players carrying a **Frequency ID Card** with a matching code (inventory, offhand, or Curios Charm slot) pass through freely
- A foreign ship whose generator holds a matching card in its **MASTER KEY** slot is also not repelled

| GUI Status | Meaning |
|------------|---------|
| ACTIVE | Barrier on, consuming energy |
| OFFLINE | Manually disabled |
| GROUNDED | Not on a ship |
| DUPLICATE | Second module on same ship |
| NO ENERGY | Buffer empty |

### Frequency ID Card

A programmable access card. Shift+Right-click to open the programming screen and enter a code. Stacks up to 8 when codes match. Wearable in a Curios **Charm** slot.

**Master Key Slot:** The Shield Generator GUI has a dedicated MASTER KEY slot — insert your card there so allied ships with matching cards can pass through your barrier without being repelled.

---

### Boarding Pod — 2×1×1 Multiblock

A two-block assault craft for boarding enemy ships.

**Setup:**
1. Craft exactly **one Boarding Pod Cockpit** and **one Boarding Pod Engine**
2. Place them side-by-side (engine adjacent to cockpit in any horizontal direction) — extra cockpits or engines in the connected group, or any other VS Shields block touching the structure, will block assembly
3. Right-click the cockpit to board — you mount the pod
4. Aim with the mouse, then press the **Fire** key (unbound by default — set in Controls → VS Shields)
5. Sneak to dismount and cancel

**Flight phases:**

| Phase | Duration | Description |
|-------|----------|-------------|
| **Aiming** | Until fire | Stationary (VS2 frozen); aim with the mouse |
| **Boost** | Up to 80 ticks (4 sec) | Hold **Space** to thrust; pod ramps to 40 m/s and steers toward your look direction |
| **Coast** | Until impact | Ballistic arc; continue steering with the mouse |
| **Drilling** | 40 ticks (2 sec) | VS2 FixedJoint to hull; follows target if it moves |

**Mouse Steering:**
The pod turns up to **3°/tick** toward where the player is looking during Boost and Coast — no separate keybinds needed. The HUD shows current speed (m/s) and remaining boost fuel.

**On impact with a VS2 ship:**
1. **Drilling phase** — pod locks rigidly to the hull for 2 seconds with metal-grinding sound, sparks, and camera shake; follows the ship if it moves or rotates
2. **Breach** — drills a clean **2×2×4** tunnel into the hull at the exact angle of approach
3. Deals **100 HP** to any active Solid Projection Module barrier on the target
4. Passenger receives **200 ticks (10 sec)** of trusted status — bypasses solid barriers briefly

**Countermeasures:**
- The pod has **10 HP** during Boost/Coast — arrows, shells, and melee can destroy it mid-air
- Passenger falls freely if the pod is shot down; pod cannot be damaged during Drilling

---

## Mod Compatibility

### Create: Big Cannons

All CBC shell types are intercepted at the shield boundary:

| Projectile | Shield Damage |
|------------|--------------|
| Autocannon / Machine Gun | 8 HP |
| Smoke Shell | 10 HP |
| Mortar Stone | 20 HP |
| Bag of Grapeshot | 30 HP |
| Drop Mortar Shell | 40 HP |
| Solid Shot | 50 HP |
| Shrapnel / Fluid Shell | 55 HP |
| HE Shell | 60 HP |
| AP Shell | 80 HP |
| **Nuke Shell** *(CBC Nukes addon)* | **500 HP** + nuclear flash at impact |

### Create: Gunsmithing

**Projectile weapons** intercepted at the shield boundary:

| Projectile | Shield Damage |
|------------|--------------|
| Nail / Steel Nail | 6 / 8 HP |
| Blaze Ball | 8 HP |
| Incendiary | 12 HP |
| Spear | 20 HP |
| Rocket | 40 HP |

**Hitscan weapons** intercepted server-side (raycast cancelled at shield surface):

| Weapon | Shield Damage |
|--------|--------------|
| Gatling | 4 HP/bullet |
| Revolver | 8 HP |
| Flintlock | 15 HP |
| Shotgun burst | 16 HP |

### Alex's Caves

Nuclear bomb and torpedo are fully intercepted (500 HP and 80 HP respectively).

> All damage values are configurable in `config/vs_shields.json`.

---

## Configuration

All settings live in `config/vs_shields.json`. The file is auto-generated on first launch and updated automatically when new keys are added by a mod update.

### `general` section — visual & gameplay

| Key | Default | Description |
|-----|---------|-------------|
| `shieldPadding` | `10.0` | Extra blocks added to each side of the ship AABB when sizing the shield sphere |
| `showShieldBubble` | `true` | `false` hides the hex bubble completely for all players (client-only; no effect on damage) |
| `hideShieldBubbleInside` | `false` | `true` hides the bubble for crew standing inside the shield; outside view is unaffected |
| `syncIntervalTicks` | `10` | How often (ticks) the server pushes shield HP/state to clients |

---

## Energy Sources

### Forge Energy (FE)
Connect any FE-producing mod to a generator or battery input: Thermal Expansion, Mekanism, Flux Networks, or any mod implementing `IEnergyStorage`.

### Create (SU → FE)
Place a Create **rotation shaft** adjacent to a Shield Generator, Battery Input, or Gravity Field Generator. Rotation is converted at **1 FE/tick per 1 RPM** from all 6 sides.

---

## Crafting Components

All recipes use custom intermediate components. Components are organized in three tiers:

### Base Components

| Component | Recipe | Yields |
|-----------|--------|--------|
| **Charged Redstone Crystal** | Shapeless: Redstone Block + Amethyst Shard + Glowstone Dust | 1 |
| **Copper Coil** | 8× Copper Ingot around Iron Ingot | 2 |
| **Insulated Wire** | 3× Copper Ingot over 3× Leather | 6 |
| **Tempered Glass Pane** | Smelt Glass Pane in furnace | 1 |
| **Reinforced Plate** | Iron Ingot + Iron Block + Obsidian (3×3) | 2 |

### Mid Components

| Component | Key Ingredients | Yields |
|-----------|----------------|--------|
| **Signal Board** | Insulated Wire, Charged Redstone Crystal, Gold, Redstone Torch, Quartz | 1 |
| **Resonance Lens** | 4× Amethyst Shard around Tempered Glass Pane | 1 |
| **Energy Cell** | Copper Coil, Charged Redstone Crystal, Redstone Block, Iron Block | 1 |
| **Frequency Oscillator** | Signal Board, Copper Coil, Amethyst Shard, Quartz | 1 |

### Advanced Components

| Component | Key Ingredients | Yields | Notes |
|-----------|----------------|--------|-------|
| **Hardened Casing** | Reinforced Plate, Obsidian, **Netherite Ingot** | 1 (stack 4) | Netherite gate for endgame |
| **Stabilized Core** | Charged Redstone Crystal, Ender Pearl, Echo Shard, **Nether Star** | 1 (stack 1) | Boss drop required |
| **Void Shard** | *Drop only* — Enderman (5%), Ender Dragon (4–8) | — (stack 16) | Configurable rates |
| **Void Capacitor** | Void Shard, Energy Cell, Stabilized Core, Echo Shard | 1 (stack 1) | Ultimate component |

### Special Item

**Energy Cell** — right-click on a Shield Generator to instantly inject **25,000 FE** (configurable). The item is consumed.

---

## Crafting Recipes

### Shield System

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

**Shield Battery Cell** *(yields 4)*
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

### Electronic Warfare Station

**Shield Jammer Frame** *(yields 4)*
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

**Gravitational Mine** *(yields 4)*
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

### Solid Projection Module & Access Cards

**Solid Projection Module**
```
[Obsidian]        [Hardened Casing]  [Obsidian]
[Hardened Casing] [Ender Eye]        [Hardened Casing]
[Copper Coil]     [Redstone Block]   [Copper Coil]
```

**Frequency ID Card** *(yields 8)*
```
[              ] [              ] [              ]
[Insulated Wire] [Signal Board]  [Insulated Wire]
[Paper]          [Paper]         [Paper]
```

---

## Installation

1. Install **Minecraft 1.20.1** with **Forge 47.4+**
2. Install **[Valkyrien Skies 2](https://modrinth.com/mod/valkyrien-skies)** (2.4.0+)
3. Install **[Architectury API](https://modrinth.com/mod/architectury-api)** (9.1.12+)
4. Drop `vs-shields-<version>.jar` into your `mods/` folder

### Optional but supported

| Mod | Benefit |
|-----|---------|
| **[Create](https://modrinth.com/mod/create)** | Power generators and batteries with rotation shafts (SU → FE) |
| **[Create: Big Cannons](https://www.curseforge.com/minecraft/mc-mods/create-big-cannons)** | Per-shell damage values for all CBC projectiles |
| **[Create: Big Cannons — Nuke Shell](https://modrinth.com/mod/cbc-nuclear)** | Nuke Shell intercepted with full nuclear flash |
| **[Create: Gunsmithing](https://www.curseforge.com/minecraft/mc-mods/create-gunsmithing)** | Per-weapon damage for projectile and hitscan weapons |
| **[Alex's Caves](https://modrinth.com/mod/alexs-caves)** | Nuclear bomb and torpedo intercepted |
| **[Curios API](https://modrinth.com/mod/curios)** | Tactical Goggles in head slot, Frequency ID Card in charm slot |

---

## Authors

**LennyPane** — programming, design

**PaleBeta** — 3D models & textures

## License

All Rights Reserved
