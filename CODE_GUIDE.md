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

## Gravity Field Generator

Устройство создаёт зону гравитационного поля для игроков на корабле.

### Архитектура

```
GravityFieldGeneratorBlockEntity.tick()
  → VSGameUtilsKt.getShipManagingPos(level, pos) → ServerShip
  → GravityFieldRegistry.registerOwner(shipId, pos) → duplicate check
  → Вычисление cost = COST_BASE(100) + flight(400) + fall(100) FE/тик
  → Если energy < cost → isActive = false
  → GravityFieldRegistry.update(shipId, GravityFieldState(flightEnabled, fallProt, worldAABB, padding))
```

**`GravityFieldRegistry`** (`shield/` пакет):
- `OWNERS: ConcurrentHashMap<Long, BlockPos>` — `putIfAbsent` для атомарного определения дублей
- `ACTIVE: ConcurrentHashMap<Long, GravityFieldState>` — AABB + флаги эффектов
- `getForPlayer(Player)` — перебор `ACTIVE.values()`, проверка `state.containsPlayer(player)` (AABB + padding)
- `clear()` — вызывается при `onServerStopping` (VSShieldsModForge.kt)

**`GravityFieldHandler.kt`** (Forge, `MinecraftForge.EVENT_BUS`):
- `onPlayerTick` (Phase.END, server-side, ServerPlayer): если `!abilities.instabuild` → выставляет `mayfly` по `getForPlayer()`, вызывает `onUpdateAbilities()`
- `onLivingFall` (LivingFallEvent): если player в поле с fallProtection → `event.isCanceled = true`

**ContainerData** (8 слотов):
- 0 = isActive, 1 = isDuplicate, 2 = flightEnabled, 3 = fallProtectionEnabled
- 4-5 = energyStored (split 16-bit low/high), 6-7 = maxEnergy

### Синхронизация радиуса со щитом

Радиус поля = `ship.getWorldAABB()` + `ShieldConfig.get().getGeneral().shieldPadding` — то же самое расширение, которое использует `ShieldRenderer`. Эффект поля покрывает ровно ту же зону, что и щит.

### Сетевые пакеты (C2S)

| ID | Данные | Действие |
|---|---|---|
| `gravity_toggle` | BlockPos, boolean | вкл/выкл устройства |
| `gravity_flight_toggle` | BlockPos, boolean | переключить полёт |
| `gravity_fall_toggle` | BlockPos, boolean | переключить защиту от падения |

---

## Глушитель Щитов (Shield Jammer) - Electronic Warfare

Механика физического тарана полностью переработана в систему Радиоэлектронной Борьбы. Контроллер `ShieldJammerControllerBlockEntity` сканирует 3x3x3 структуру, которая может содержать рамки (`shield_jammer_frame`) и входы энергии (`shield_jammer_input`).

### Electronic Warfare Scanner (Интеграция с VS2)
1. **Зона действия**: Локальный центр глушителя переводится в координаты мира. 
2. **Поиск целей**: Используется `VSGameUtilsKt.getAllShips(level)`. Все корабли кроме своего сканируются на наличие активного щита через `ShieldManager`.
3. **World Space AABB Перекрытие**: Вычисляется пересечение bounding box корабля противника с зоной действия глушителя на основе преобразований матриц `getShipToWorld()`.
4. **Ультимативное Подавление**: Если найдена цель, глушитель потребляет колоссальные объёмы своей накопленной энергии, нанося прямой урон генератору вражеского щита вызовом `drainEnergyFromJammer(amount)`. В этот момент отрисовываются частицы `SONIC_BOOM`.

### Батареи и Кулдаун (Depletion & Rebooting)
Емкость глушителя динамически вычисляется по количеству рамок в 3x3x3 (до 3.9M FE). Когда энергия падает до нуля, контроллер выключается и система уходит в `REBOOTING` стейт. Входные блоки `ShieldJammerInputBlockEntity` перекрывают подачу энергии из внешних сетей только в момент *Активного* глушения, позволяя системе зарядиться с нуля во время перезагрузки. В GUI отображается точный таймер и статусы ошибок (`DUPLICATE`, `GROUNDED`).

### Disable/Enable Toggle

Поле `isEnabled` (ContainerData слот 9) полностью выключает глушитель без разрушения структуры:
- `disable()` → `isEnabled=false` + вызывает `forceCooldown()` (тот же 60-секундный кулдаун что и `Reload`)
- `enable()` → `isEnabled=true` + `setChanged()` — устройство возобновит работу после перезарядки
- В `serverTick`: если `!isEnabled` → `isActive=false; return;` (раньше любой другой логики)
- Статус в GUI: `OFFLINE (DISABLED)` отображается серым цветом, кнопки Reload/Enable-Disable активны
- NBT backward compat: `!tag.contains("IsEnabled") || tag.getBoolean("IsEnabled")` — дефолт `true`
- C2S пакет: `jammer_enable` (BlockPos, boolean enable)
