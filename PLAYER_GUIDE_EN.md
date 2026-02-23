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

### Shield Capacitor

A simple addon block with a 3D model. Place it on the same ship to automatically add **+50 HP** to the shield's maximum health. The effect stacks: 4 capacitors = +200 HP.

### Shield Emitter

Place on the same ship to add **+0.5 HP/tick** to the shield's recharge rate. The effect stacks.

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

## Visual & Audio Effects

### Shield Appearance
- **Honeycomb Pattern:** The shield renders as a sci-fi hexagonal energy grid that slowly rotates around the ship.
- **Color Scales with HP:**
  - **Blue** — HP > 50% (healthy)
  - **Yellow** — HP 25–50% (damaged)
  - **Red** — HP < 25% (critical)
- **Low Energy Distortions:** If the generator's FE drops below 20%, the shield will begin to violently flicker and glitch, shifting to a red-orange hue.

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

### Generator GUI
Right-click the generator to see:
- HP bar (color-coded)
- FE bar (orange)
- Status: Active / Inactive
- Activate / Deactivate button

---

## Redstone Integration

The **Shield Generator** outputs a **redstone signal** when it takes damage — the signal strength is proportional to the damage received. Place a **comparator** next to the generator to wire it into automation circuits (e.g., alert systems, auto-activating backup batteries when under attack).
