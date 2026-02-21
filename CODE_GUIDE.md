# VS Energy Shields — Code Guide

## Архитектура

```
common/                          # Architectury — кроссплатформенный код
├── shield/                      # Серверная логика
│   ├── ShieldManager.java       # Singleton: реестр щитов (shipId → ShieldInstance)
│   ├── ShieldInstance.java       # HP, recharge, damage(), DamageListener, energyPercent
│   ├── ShieldTier.java          # Iron/Diamond/Netherite: HP, recharge, cooldowns
│   └── CloakManager.java        # Реестр замаскированных кораблей
├── network/
│   ├── ModNetwork.java          # Пакеты: SHIELD_SYNC, SHIELD_HIT, SHIELD_BREAK, NUKE_VISUAL, CLOAK_TOGGLE
│   ├── ClientShieldManager.java # Клиентский кэш щитов + world AABB
│   └── VSShieldsNetworking.java # CloakStatusPacket регистрация
├── client/
│   ├── ShieldRenderer.java      # BER: эллипсоид с honeycomb + HP-цвет + flicker
│   ├── ShieldEffectHandler.java # Hit particles + break animation (tick-based)
│   ├── ShieldHudOverlay.java    # HUD: HP-бар ближайшего щита
│   ├── ShieldAmbientSoundHandler.java # Ambient hum loop
│   ├── VSShieldsModClient.java  # Клиентская инициализация, пакеты S2C
│   ├── CloakRenderState.java    # Thread-local: текущий рендерящийся корабль
│   └── ClientCloakManager.java  # Клиентский кэш замаскированных кораблей
├── block/                       # Блоки: генераторы, конденсатор, эмиттер, батарея, маскировка
├── blockentity/                 # BE: тики, FE, Create SU, мультиблок батареи
├── config/ShieldConfig.java     # JSON конфиг (vs_shields.json)
├── registry/                    # Architectury: блоки, BE, меню, звуки, рецепты
└── mixin/                       # CloakChunkLayerMixin (отключена)

forge/                           # Forge-специфичный код
├── ShieldDamageHandler.kt       # ExplosionEvent.Start + ProjectileImpactEvent + EntityJoinLevel (nukes)
├── ShieldBarrierHandler.kt      # LevelTickEvent: перехват снарядов на границе щита
├── ShieldEnergyCapability.kt    # IEnergyStorage для Shield Generator
├── BatteryInputEnergyCapability.kt
├── CloakEnergyCapability.kt
├── CreateCompat.kt              # SU → FE через reflection
└── VSShieldsModForge.kt         # Forge entrypoint, регистрация EVENT_BUS
```

## Ключевые потоки данных

### Урон → Щит → Эффекты
```
Снаряд летит в мир
  ↓
ShieldBarrierHandler (каждый тик)
  ├── curPos внутри shieldAABB, prevPos снаружи → ВНЕШНИЙ БАРЬЕР
  └── curPos внутри coreAABB, prevPos внутри shieldAABB → FRIENDLY FIRE
  ↓
shield.damage(amount, tick)
  ↓
ModNetwork.sendShieldHit(server, shipId, interceptX/Y/Z, damage)
  ↓ (S2C пакет)
ShieldEffectHandler.onShieldHit() → ELECTRIC_SPARK + END_ROD + CRIT + FLASH
  ↓
Если HP ≤ 0: ModNetwork.sendShieldBreak(server, shipId)
  → ShieldEffectHandler.onShieldBreak() → 1-сек анимация разлёта осколков
```

### Энергия → Щит
```
FE/SU источник → BlockEntity.tick()
  → IEnergyStorage (Forge) или CreateCompat (SU через reflection)
  → ShieldGeneratorBlockEntity: FE → energyPercent → hpScale
  → ModNetwork.sendSyncToAll() (каждые N тиков)
  → ClientShieldManager → ShieldRenderer (flicker при < 20%)
```

### Рендеринг щита
```
ShieldRenderer.render()
  → Ship.getShipAABB() → AABB в shipyard-space
  → ellipsoid (32×48 stacks/slices)
  → hexColor(phi, theta) → honeycomb per-vertex alpha modulation
     └── axial hex coordinates → edge distance → smoothstep
  → HP-based color (blue > yellow > red)
  → Energy flicker (FE < 20%): multi-frequency + dropout + color shift
```

## Сетевые пакеты

| ID | Направление | Данные | Назначение |
|---|---|---|---|
| `shield_sync` | S2C | N × (shipId, HP, maxHP, active, energyPct, worldAABB?) | Периодическая синхронизация |
| `shield_hit` | S2C | shipId, x, y, z, damage | Партиклы попадания |
| `shield_break` | S2C | shipId | Анимация разрушения |
| `shield_toggle` | C2S | shipId, active | Вкл/выкл из GUI |
| `nuke_visual` | S2C | x, y, z | Alex's Caves nuke entity |
| `cloak_toggle` | C2S | shipId, active | Вкл/выкл маскировки |
| `cloak_status` | S2C | shipId, isCloaked | CloakStatusPacket |

## Конфигурация (`config/vs_shields.json`)

Основные секции:
- `general` — syncInterval, shieldPadding, hpScaleMin/Max
- `tiers` — HP, recharge, cooldowns для Iron/Diamond/Netherite
- `damage` — explosionPowerFactor, таблица урона по entity type, alexsCavesNukeDamage
- `energy` — FE capacity/consumption per tier, Create SU ratio
- `battery` — cell capacity, passive regen rate, emergency threshold

## Двойной барьер (ShieldBarrierHandler)

Два слоя защиты:
1. **shieldAABB** = worldAABB + padding → внешний барьер, останавливает входящие снаряды
2. **coreAABB** = worldAABB без padding → внутренний барьер, блокирует дружественный огонь

Детекция пересечения: `xOld/yOld/zOld` vs `x/y/z` (не entity ID tracking).
Точка перехвата: ray-AABB intersection для точного hit effect на поверхности.

## Honeycomb рендеринг

Процедурный per-vertex: `(phi, theta)` → equirectangular UV → axial hex coordinates → cube rounding → edge distance → `smoothstep` → alpha/brightness modulation.

Параметры:
- `HEX_SCALE = 8` (количество ячеек)
- `EDGE_WIDTH = 0.12` (толщина линий)
- `EDGE_BRIGHTNESS = 1.8` (яркость рёбер)
- `FILL_ALPHA_MULT = 0.4` (прозрачность заливки)
- Медленное вращение: 1 оборот / 60 сек
