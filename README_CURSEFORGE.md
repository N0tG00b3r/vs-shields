# VS Shields

**Energy shield systems for [Valkyrien Skies 2](https://www.curseforge.com/minecraft/mc-mods/valkyrien-skies) ships.**

Minecraft 1.20.1 · Forge · Requires VS2 + Architectury API + Embeddium

---

![VS Shields Banner](https://i.imgur.com/rBj5gGQ.png)

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
- **Forge Energy & Create SU** — power from any FE cable, or directly from Create rotation shafts
- **Mod compatibility** — full damage tables for Create: Big Cannons (all shell types including Nuke Shell) and Create: Gunsmithing (projectile + hitscan weapons)
- **JSON config** — every damage value, energy cost, and recharge rate is configurable in `config/vs_shields.json`

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
[Ender Eye] [Dropper]         [Ender Eye]
[Quartz]    [Netherite Ingot] [Quartz]
[Redstone]  [Piston]          [Redstone]
```

---

## Installation

1. Install **Minecraft 1.20.1** with **Forge 47.4+**
2. Install **[Valkyrien Skies 2](https://www.curseforge.com/minecraft/mc-mods/valkyrien-skies)** (2.4.0+)
3. Install **[Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)** (9.1.12+)
4. Drop `vs-shields-<version>.jar` into your `mods/` folder

### Optional but supported

| Mod | Benefit |
|-----|---------|
| **[Create](https://www.curseforge.com/minecraft/mc-mods/create)** | Power generators and batteries with rotation shafts (SU → FE) |
| **[Create: Big Cannons](https://www.curseforge.com/minecraft/mc-mods/create-big-cannons)** | Per-shell damage values for all CBC projectiles |
| **[Create: Big Cannons — Nuke Shell](https://modrinth.com/mod/cbc-nuclear)** | Nuke Shell intercepted with full nuclear flash |
| **[Create: Gunsmithing](https://www.curseforge.com/minecraft/mc-mods/create-gunsmithing)** | Per-weapon damage for projectile and hitscan weapons |
| **[Alex's Caves](https://www.curseforge.com/minecraft/mc-mods/alexs-caves)** | Nuclear bomb and torpedo intercepted |

---

## Authors

**LennyPane** — programming, design

**PaleBeta** — 3D models & textures

## License

[Custom License](https://github.com/LennyPane/vs-shields/blob/main/LICENSE)
