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
- **Tactical Netherite Helm** — always-on analyzer HUD built into a helmet
- **Gravitational Mine + Launcher** — deployable space-mines that apply massive physical torque to enemy ships on detonation
- **Redstone output** — Shield Generator pulses a redstone signal when struck; wire it into your own alert systems
- **Solid Projection Module** — turns the energy shield into a physical barrier; access controlled by programmable **Frequency ID Cards** (up to 8-char codes, Curios-compatible)
- **Boarding Pod** — 2-block multiblock assault craft; player boards, aims with mouse, fires on a ballistic arc and breaches a 2×2×4 tunnel into the target ship's hull; **RCS Thrusters** allow 5 mid-flight lateral impulses (A/D keys)
- **Forge Energy & Create SU** — power from any FE cable, or directly from Create rotation shafts
- **Mod compatibility** — full damage tables for Create: Big Cannons (all shell types including Nuke Shell) and Create: Gunsmithing (projectile + hitscan weapons)
- **JSON config** — every damage value, energy cost, recharge rate, and visual option is configurable in `config/vs_shields.json`

---

## Blocks & Systems

### Shield Generator

The core block. Place one on your VS2 ship, supply FE, and right-click to activate. The shield wraps the ship's bounding box with a 10-block padding on all sides.

| Tier | Max HP | Regen | FE/tick | FE Capacity |
|------|--------|-------|---------|-------------|
| **Iron** | 100 | 0.5/tick | 20 | 50,000 |
| **Diamond** | 250 | 1.0/tick | 50 | 200,000 |
| **Netherite** | 500 | 2.0/tick | 100 | 500,000 |

- One generator per ship — a second shows a **Duplicate** warning
- HP scales with energy level (50% HP at 0% FE, 100% at full charge)
- Recharge starts after a cooldown (5–10 sec depending on tier); if the shield is fully depleted, a longer cooldown applies
- Outputs a **redstone signal** when struck — wire a comparator to trigger alarm systems or backup logic

### Shield Capacitor

Adds **+50 max HP** to the ship's shield. Stacks: four capacitors = +200 HP.

### Shield Emitter

Adds **+0.5 HP/tick** recharge. Stacks. Each emitter costs **+50 FE/tick** while the shield is recharging.

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

### Tactical Netherite Helm
Wear it for a passive, always-on HUD readout — no right-click required. Built by upgrading a Netherite Helmet with Ship Analyzer circuitry.

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
1. Craft a **Boarding Pod Cockpit** and a **Boarding Pod Engine**
2. Place them side-by-side (engine adjacent to cockpit in any horizontal direction)
3. Right-click the cockpit to board — you mount the pod
4. Aim with the mouse, then press the **Fire** key (unbound by default — set in Controls → VS Shields)
5. Sneak to dismount and cancel

**Flight phases:**

| Phase | Duration | Description | RCS |
|-------|----------|-------------|-----|
| **Aiming** | Until fire | Stationary; player controls aim | — |
| **Boost** | 40 ticks (2 sec) | Rocket burn, near-zero gravity | Active |
| **Coast** | Until impact | Ballistic arc under full gravity | Active |
| **Drilling** | 40 ticks (2 sec) | Attached to hull; drilling through | — |

**Terminal Magnetic Lock:**
In the last 7 blocks before reaching a ship's hull the pod automatically steers its velocity perpendicular to the nearest armour face — ensuring the breach tunnel is always straight regardless of approach angle.

**RCS Thrusters:**
- Press **A** or **D** during Boost/Coast to fire a lateral thruster burst
- Each press consumes **1 of 5 charges** (refilled on each new launch) and rotates the pod to face its new heading
- **0.6 s cooldown** between bursts; HUD shows remaining charges above the hotbar
- Charges are destroyed with the pod — you cannot bank unused thrusts

**On impact with a VS2 ship:**
1. **Drilling phase** — pod locks to the hull for 2 seconds with metal-grinding sound, sparks, and camera shake; follows the ship if it moves or rotates
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
| `syncIntervalTicks` | `10` | How often (ticks) the server pushes shield HP/state to clients |

> The shield uses back-face culling — it is always visible from outside but automatically invisible when the camera is inside the bubble, with no configuration required.

---

## Energy Sources

### Forge Energy (FE)
Connect any FE-producing mod to a generator or battery input: Thermal Expansion, Mekanism, Flux Networks, or any mod implementing `IEnergyStorage`.

### Create (SU → FE)
Place a Create **rotation shaft** adjacent to a Shield Generator, Battery Input, or Gravity Field Generator. Rotation is converted at **1 FE/tick per 1 RPM** from all 6 sides.

---

## Crafting Recipes

### Shield System

**Iron Shield Generator**
```
[Iron Block]  [Redstone]  [Iron Block]
[Iron Block]  [Glass]     [Iron Block]
[Iron Block]  [Redstone]  [Iron Block]
```

**Shield Capacitor**
```
[Iron Ingot]    [Gold Ingot]     [Iron Ingot]
[Redstone Block][Redstone Block] [Redstone Block]
[Iron Ingot]    [Gold Ingot]     [Iron Ingot]
```

**Shield Emitter**
```
[Iron Ingot] [Redstone Torch] [Iron Ingot]
[Gold Ingot] [Glass]          [Gold Ingot]
[Iron Ingot] [Redstone Torch] [Iron Ingot]
```

### Shield Battery

**Shield Battery Cell** *(yields 4)*
```
[Iron Ingot] [Redstone] [Iron Ingot]
[Redstone]   [Glass]    [Redstone]
[Iron Ingot] [Redstone] [Iron Ingot]
```

**Shield Battery Input**
```
[Gold Ingot]  [Diamond]       [Gold Ingot]
[Iron Ingot]  [Battery Cell]  [Iron Ingot]
[Gold Ingot]  [Diamond]       [Gold Ingot]
```

**Shield Battery Controller**
```
[Echo Shard]    [Battery Input]    [Echo Shard]
[Battery Input] [Netherite Ingot]  [Battery Input]
[Echo Shard]    [Battery Input]    [Echo Shard]
```

### Electronic Warfare Station

**Shield Jammer Frame** *(yields 4)*
```
[Iron Block] [Obsidian]   [Iron Block]
[Obsidian]   [Iron Block] [Obsidian]
[Iron Block] [Obsidian]   [Iron Block]
```

**Shield Jammer Input**
```
[    ]     [Iron Block]    [    ]
[Obsidian] [Jammer Frame]  [Obsidian]
[    ]     [Iron Block]    [    ]
```

**Shield Jammer Controller**
```
[Diamond Block]  [End Crystal]      [Diamond Block]
[Redstone Block] [Netherite Block]  [Redstone Block]
[Diamond Block]  [Obsidian]         [Diamond Block]
```

### Gravity Field Generator

```
[Ender Pearl]    [Netherite Block] [Ender Pearl]
[Netherite Block][Nether Star]     [Netherite Block]
[Ender Pearl]    [Netherite Block] [Ender Pearl]
```

### Tools & Weapons

**Ship Analyzer**
```
[Ender Eye]       [Amethyst Shard] [Ender Eye]
[Netherite Ingot] [Spyglass]       [Netherite Ingot]
[Redstone]        [Compass]        [Redstone]
```

**Tactical Netherite Helm**
```
[Echo Shard]      [Spyglass]      [Echo Shard]
[Ender Eye]       [Neth. Helmet]  [Ender Eye]
[Netherite Ingot] [Ship Analyzer] [Netherite Ingot]
```

**Gravitational Mine** *(yields 4)*
```
[Ender Pearl]  [Phantom Membrane] [Ender Pearl]
[Blaze Powder] [Iron Ingot]       [Blaze Powder]
[Gunpowder]    [Iron Ingot]       [Gunpowder]
```

**Gravitational Mine Launcher**
```
[Ender Eye] [Dropper]        [Ender Eye]
[Quartz]    [Netherite Ingot][Quartz]
[Redstone]  [Piston]         [Redstone]
```

**Boarding Pod Cockpit**
```
[Iron Ingot]   [Glass]        [Iron Ingot]
[Smooth Stone] [Compass]      [Smooth Stone]
[Iron Ingot]   [Redstone]     [Iron Ingot]
```

**Boarding Pod Engine**
```
[Iron Ingot]   [Blaze Powder] [Iron Ingot]
[Blaze Powder] [Gunpowder]    [Blaze Powder]
[Iron Ingot]   [Blaze Powder] [Iron Ingot]
```

### Solid Projection Module & Access Cards

**Solid Projection Module**
```
[Obsidian]   [Diamond]        [Obsidian]
[Diamond]    [Ender Eye]      [Diamond]
[Iron Block] [Redstone Block] [Iron Block]
```

**Frequency ID Card**
```
[           ] [          ] [           ]
[Iron Nugget] [Name Tag]   [Iron Nugget]
[Paper]       [Paper]      [Paper]
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

---

## Authors

**LennyPane** — programming, design

**PaleBeta** — 3D models & textures

## License

All Rights Reserved
