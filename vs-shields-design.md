e# VS Shields — Valkyrien Skies 2 Ship Shield Addon
## Design Document v1.0

---

## 📋 Обзор

**Название мода:** VS Shields  
**Mod ID:** `vsshields`  
**Версия MC:** 1.20.1  
**Лоадер:** Forge  
**Зависимости:** Valkyrien Skies 2 (v2.4+), Kotlin for Forge  
**Шаблон:** [VS Addon Template](https://github.com/ValkyrienSkies/Valkyrien-Skies-Addon-Template)

**Концепция:** Система энергетических щитов для VS2-кораблей. Щит поглощает входящий урон от снарядов и взрывов, защищая блоки корабля. Имеет запас прочности (HP), потребляет энергию, визуально отображается полупрозрачной сферой/куполом.

---

## 🧱 Блоки

### 1. Shield Generator (Генератор щита)

**Рецепт крафта:**
```
[D] [N] [D]
[O] [B] [O]
[I] [R] [I]

D = Diamond, N = Nether Star, O = Obsidian
B = Beacon, R = Redstone Block, I = Iron Block
```

**Свойства:**
- Размещается на VS-корабле
- Только ОДИН генератор на корабль (попытка поставить второй — сообщение в чат)
- Имеет GUI с индикаторами:
  - Полоска HP щита (текущее/максимальное)
  - Статус: ACTIVE / CHARGING / DEPLETED / NO POWER
  - Радиус покрытия
  - Потребление энергии в тик
- Редстоун-управляемый: сигнал редстоуна включает/выключает щит
- При разрушении блока — щит мгновенно падает

**Характеристики базового генератора:**
| Параметр | Значение |
|---|---|
| Базовое HP щита | 500 |
| Радиус покрытия | 16 блоков от генератора |
| Регенерация HP | 2 HP/сек (когда щит НЕ под огнём) |
| Задержка регенерации | 5 секунд после последнего попадания |
| Потребление энергии (idle) | 1 FE/tick (или 1 SU от Create) |
| Потребление энергии (active, под огнём) | 10 FE/tick |

---

### 2. Shield Capacitor (Конденсатор щита)

**Рецепт крафта:**
```
[I] [R] [I]
[R] [D] [R]
[I] [R] [I]

I = Iron Block, R = Redstone Block, D = Diamond Block
```

**Свойства:**
- Увеличивает максимальный HP щита
- Можно ставить несколько штук на корабль
- Каждый конденсатор: **+200 HP** к максимуму щита
- Должен быть в пределах радиуса генератора
- Визуально: светится когда щит активен

**Тиры конденсаторов (опционально, для v2.0):**
| Тир | Материал | Бонус HP |
|---|---|---|
| Iron | Железо | +100 HP |
| Diamond | Алмаз | +200 HP |
| Netherite | Незерит | +400 HP |

---

### 3. Shield Emitter (Эмиттер щита)

**Рецепт крафта:**
```
[ ] [E] [ ]
[G] [D] [G]
[ ] [I] [ ]

E = Ender Pearl, G = Glass, D = Diamond, I = Iron Block
```

**Свойства:**
- Расширяет зону покрытия щита
- Каждый эмиттер: **+8 блоков** к радиусу
- Максимум 4 эмиттера на генератор
- Размещаются на внешней обшивке корабля
- Визуально: излучают частицы когда щит активен

---

## ⚔️ Механика урона

### Что блокирует щит:
| Источник урона | Поглощение | Урон щиту |
|---|---|---|
| TNT взрыв | 100% | 50 HP |
| Create Big Cannons — ядро | 100% | 30 HP |
| Create Big Cannons — HE снаряд | 100% | 80 HP |
| Create Big Cannons — AP снаряд | 50% (пробивает частично) | 40 HP |
| Стрелы/снежки | 100% | 1 HP |
| Огненный шар Гаста | 100% | 20 HP |
| Удар игрока мечом | НЕ блокирует | 0 HP |
| Крипер | 100% | 40 HP |

### Механика пробития:
- Когда HP щита = 0, щит "падает" с визуальным/звуковым эффектом
- Щит не регенерирует 10 секунд после падения (cooldown)
- Бронебойные снаряды (AP) пробивают щит на 50% урона даже при полном HP
- Щит НЕ блокирует melee-урон (чтобы абордаж был возможен)

### Формула урона:
```
actual_damage_to_ship = incoming_damage × (1 - shield_absorption_rate)
shield_hp_loss = incoming_damage × shield_damage_multiplier

if shield_hp <= 0:
    shield_status = DEPLETED
    start_cooldown(10 seconds)
    // Оставшийся урон проходит полностью
```

---

## 🎨 Визуальные эффекты

### Щит активен (idle):
- Полупрозрачная сфера/купол вокруг корабля
- Цвет: голубой (#00AAFF) с альфа 0.15
- Слабая пульсация (синусоида по альфа, период 3 сек)
- Гексагональная текстура (honeycomb pattern) на поверхности

### Щит под ударом:
- В точке попадания — яркая вспышка (альфа 0.8)
- Ripple-эффект (волны расходятся от точки удара)
- Цвет вспышки: белый → голубой
- Частицы: искры в точке попадания

### Щит перегружен (HP < 20%):
- Цвет меняется на красный (#FF4444)
- Мерцание (случайное)
- Звук: электрическое потрескивание

### Щит упал:
- Эффект "разбитого стекла" — сфера разлетается на осколки
- Звук: громкий электрический разряд + звон стекла
- Частицы: голубые осколки рассеиваются
- 10-секундный cooldown без визуала

---

## 🔊 Звуки

| Событие | Звук |
|---|---|
| Щит активирован | Нарастающий гул + "щелчок" |
| Щит работает (ambient) | Тихий низкочастотный гул (loop) |
| Попадание по щиту | Глухой удар + электрический треск |
| Щит перегружен | Потрескивание + тревожный тон |
| Щит упал | Громкий разряд + разбивание |
| Щит восстановлен | Нарастающий тон + "щелчок" |

---

## 🏗️ Архитектура кода

### Структура пакетов:
```
com.mechanicalskies.vsshields/
├── VSShieldsMod.java              // @Mod главный класс
├── config/
│   └── ShieldConfig.java          // ForgeConfigSpec, все числа настраиваемые
├── block/
│   ├── ShieldGeneratorBlock.java  // Block + BlockEntity
│   ├── ShieldCapacitorBlock.java
│   ├── ShieldEmitterBlock.java
│   └── entity/
│       ├── ShieldGeneratorBlockEntity.java  // Основная логика
│       ├── ShieldCapacitorBlockEntity.java
│       └── ShieldEmitterBlockEntity.java
├── shield/
│   ├── ShieldData.java            // Данные щита: HP, status, cooldown
│   ├── ShieldManager.java         // Управление щитами всех кораблей
│   └── ShieldDamageHandler.java   // Перехват и обработка урона
├── network/
│   ├── ShieldStatusPacket.java    // Сервер → Клиент: синхронизация HP
│   ├── ShieldHitPacket.java       // Сервер → Клиент: точка попадания
│   └── NetworkHandler.java
├── client/
│   ├── render/
│   │   ├── ShieldRenderer.java    // Рендеринг сферы через RenderType
│   │   └── ShieldHitEffect.java   // Эффект попадания
│   ├── gui/
│   │   └── ShieldGeneratorScreen.java  // GUI генератора
│   └── ShieldSoundHandler.java    // Ambient звуки
├── registry/
│   ├── ModBlocks.java
│   ├── ModBlockEntities.java
│   ├── ModItems.java
│   ├── ModSounds.java
│   └── ModCreativeTab.java
└── integration/
    └── CreateCompat.java          // Опциональная интеграция с Create SU
```

### Ключевые взаимодействия с VS2 API:

```java
// 1. Определение корабля по позиции блока
import org.valkyrienskies.mod.common.VSGameUtilsKt;

Level level = blockEntity.getLevel();
BlockPos pos = blockEntity.getBlockPos();

// Получить Ship по позиции блока
Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
if (ship != null) {
    long shipId = ship.getId();
    // Привязать щит к этому кораблю
}

// 2. Определить, находится ли точка взрыва внутри радиуса щита
// Трансформировать мировые координаты в ship-space:
Vector3d worldPos = new Vector3d(explosionX, explosionY, explosionZ);
// ship.getShipToWorld() — матрица трансформации
// ship.getWorldToShip() — обратная матрица

// 3. Перехват урона через Forge Events
@SubscribeEvent
public void onExplosion(ExplosionEvent.Detonate event) {
    // Проверить, задевает ли взрыв блоки на VS-корабле
    // Если щит активен — отменить урон блокам, снять HP со щита
}

// Для снарядов CBC — перехват ProjectileImpactEvent или 
// кастомного CBC события попадания
```

### ShieldData (хранится per-ship через VS2 attachment):

```java
public class ShieldData {
    private float currentHP;
    private float maxHP;
    private ShieldStatus status; // ACTIVE, CHARGING, DEPLETED, NO_POWER, OFF
    private int cooldownTicks;
    private int lastHitTick;
    private float radius;
    private BlockPos generatorPos;
    private List<BlockPos> capacitorPositions;
    private List<BlockPos> emitterPositions;
    
    // Рассчитать максимальный HP на основе конденсаторов
    public void recalculateMaxHP() {
        this.maxHP = BASE_HP + (capacitorPositions.size() * HP_PER_CAPACITOR);
    }
    
    // Рассчитать радиус на основе эмиттеров
    public void recalculateRadius() {
        this.radius = BASE_RADIUS + (emitterPositions.size() * RADIUS_PER_EMITTER);
    }
    
    // Tick-логика
    public void tick() {
        if (status == ShieldStatus.DEPLETED) {
            cooldownTicks--;
            if (cooldownTicks <= 0) {
                status = ShieldStatus.CHARGING;
            }
        }
        if (status == ShieldStatus.CHARGING || status == ShieldStatus.ACTIVE) {
            if (currentTick - lastHitTick > REGEN_DELAY_TICKS) {
                currentHP = Math.min(maxHP, currentHP + REGEN_PER_TICK);
            }
            if (currentHP > 0) {
                status = ShieldStatus.ACTIVE;
            }
        }
    }
}
```

---

## ⚙️ Конфигурация (server config)

```toml
[shield]
    # Базовое HP генератора
    baseHP = 500
    # HP за каждый конденсатор
    hpPerCapacitor = 200
    # Базовый радиус (блоки)
    baseRadius = 16
    # Радиус за каждый эмиттер
    radiusPerEmitter = 8
    # Максимум эмиттеров на генератор
    maxEmitters = 4
    # Регенерация HP в секунду
    regenPerSecond = 2.0
    # Задержка регенерации после попадания (секунды)
    regenDelay = 5.0
    # Cooldown после падения щита (секунды)  
    depletedCooldown = 10.0
    
[damage]
    # Множитель урона от TNT
    tntDamage = 50.0
    # Множитель урона от CBC ядра
    cbcSolidShotDamage = 30.0
    # Множитель урона от CBC HE
    cbcHEDamage = 80.0
    # Пробитие AP снарядов (0.0 - 1.0, сколько урона проходит)
    apPenetration = 0.5
    # Блокирует ли melee-урон
    blockMelee = false
    
[energy]
    # Потребление в idle (FE/tick)
    idleConsumption = 1
    # Потребление под огнём (FE/tick)
    activeCombatConsumption = 10
    # Использовать Create SU вместо FE
    useCreateSU = false

[visual]
    # Цвет щита (hex без #)
    shieldColor = "00AAFF"
    # Прозрачность щита (0.0 - 1.0)
    shieldAlpha = 0.15
    # Показывать щит всегда или только при попадании
    alwaysVisible = true
```

---

## 📦 Фазы разработки

### Phase 1 — MVP (1-2 недели)
- [ ] Настроить проект из Addon Template
- [ ] Зарегистрировать блок ShieldGenerator + BlockItem
- [ ] Базовая логика ShieldData (HP, статус)
- [ ] Перехват ExplosionEvent — отмена урона + снятие HP
- [ ] Простой рендеринг щита (полупрозрачная сфера, без текстуры)
- [ ] Синхронизация HP сервер→клиент (network packet)
- [ ] Команда `/vsshields status` для отладки

### Phase 2 — Визуал и звуки (1 неделя)
- [ ] Гексагональная текстура для сферы
- [ ] Ripple-эффект при попадании
- [ ] Мерцание при низком HP
- [ ] Эффект падения щита
- [ ] Все звуковые события
- [ ] Частицы

### Phase 3 — Полная система (1 неделя)
- [ ] Конденсаторы и эмиттеры
- [ ] GUI генератора
- [ ] Рецепты крафта
- [ ] Конфиг-файл
- [ ] Интеграция с Create Big Cannons (определение типа снаряда)
- [ ] Creative Tab

### Phase 4 — Полировка (1 неделя)
- [ ] Create SU интеграция (опционально)
- [ ] FTB Teams интеграция (свои снаряды не снимают HP)
- [ ] Тестирование на Mechanical Skies с 3-4 игроками
- [ ] Балансировка значений
- [ ] README, CurseForge/Modrinth описание

---

## 🎮 Баланс для Mechanical Skies

Для твоего сервера с кораблями 200-500+ блоков на y=150:

**Малый корабль (200 блоков):**
- 1 генератор + 0 конденсаторов = 500 HP
- Выдержит ~6 обычных ядер CBC или 1 HE + несколько обычных
- Щит падает быстро → нужно маневрировать

**Средний корабль (350 блоков):**
- 1 генератор + 2 конденсатора = 900 HP
- Выдержит ~11 обычных ядер или ~2-3 HE
- Есть время на ответный огонь

**Большой корабль (500+ блоков):**
- 1 генератор + 4 конденсатора + 4 эмиттера = 1300 HP, радиус 48
- Выдержит серьёзный обстрел, но дорогой в ресурсах
- Всё ещё уязвим к координированной атаке 2-3 кораблей

**Контрплей:**
- AP снаряды пробивают 50% → есть контр к тяжёлым щитам
- Melee не блокируется → абордаж работает
- Cooldown 10 сек → одна удачная атака открывает окно
- Генератор можно уничтожить прицельно → щит падает навсегда

---

## 💡 Идеи на будущее (v2.0+)

- **Shield Frequencies** — разные частоты щитов, нужно подобрать частоту чтобы пробить
- **Shield Overcharge** — кнопка для кратковременного x2 HP, но потом 30 сек без щита
- **Shield Disruptor** — специальный снаряд/блок, мгновенно снимающий щит
- **Shield Link** — два корабля соединяют щиты в один общий
- **Directional Shields** — щит только с одной стороны, но x3 HP
