На# VS Energy Shields — Гайд для игроков

## Быстрый старт

1. Скрафти **Shield Generator** (Iron, Diamond или Netherite)
2. Поставь его на VS2 корабль
3. Подключи источник энергии (FE) — любые провода/трубы, или вал Create
4. Кликни ПКМ по генератору → нажми "Activate"
5. Вокруг корабля появится энергетический щит

---

## Блоки

### Shield Generator (Генератор щита)

Основной блок. Создаёт защитное поле вокруг корабля. Есть три тира:

| Тир | HP | Радиус | Регенерация | FE/тик | Ёмкость FE |
|-----|-----|--------|-------------|--------|------------|
| **Iron** | 100 | 8 блоков | 0.5/тик | 20 | 50,000 |
| **Diamond** | 250 | 16 блоков | 1.0/тик | 50 | 200,000 |
| **Netherite** | 500 | 24 блоков | 2.0/тик | 100 | 500,000 |

**Правила:**
- Только **один генератор на корабль**. Второй покажет красное сообщение "Дубликат"
- Генератор потребляет FE каждый тик. Без энергии щит выключается
- HP щита масштабируется от заполненности энергией (50% HP при 0% FE, 100% HP при 100% FE)
- После получения урона регенерация начинается с задержкой (кулдаун: 5с/3с/2с по тирам)

### Shield Capacitor (Конденсатор щита)

Простой блок. Поставь на тот же корабль — автоматически даёт **+50 HP** к максимуму щита.
Стакается: 4 конденсатора = +200 HP.

### Shield Emitter (Эмиттер щита)

Поставь на тот же корабль — даёт **+0.5 HP/тик** к регенерации и **+2 блока** к радиусу.
Стакается: 4 эмиттера = +2.0 recharge + 8 радиуса.

---

## Shield Battery (Батарея щита) — Мультиблок

Мультиблок 3x3x3, который хранит FE и мгновенно восстанавливает HP щита при получении урона.

### Как построить

```
Вид спереди (сторона контроллера):    Средний слой:    Задний слой:
 [C] [C] [C]                          [C] [C] [C]     [C] [C] [C]
 [C] [@] [C]  ← @ = Controller        [C] [C] [C]     [C] [C] [C]
 [C] [C] [C]                          [C] [C] [C]     [C] [C] [C]

[C] = Shield Battery Cell или Shield Battery Input
[@] = Shield Battery Controller (направлен наружу)
```

1. Поставь **Shield Battery Controller** — это центр передней грани куба 3x3x3
2. Заполни остальные **26 позиций** блоками **Shield Battery Cell** и/или **Shield Battery Input**
3. Controller направлен наружу (лицом к тебе при установке). Мультиблок строится ЗА ним

### Блоки мультиблока

| Блок | Описание |
|------|----------|
| **Shield Battery Controller** | Мозг. Открой GUI (ПКМ) чтобы увидеть статус |
| **Shield Battery Cell** | Структурный блок. Без функции, но нужен для формирования |
| **Shield Battery Input** | Принимает FE из труб/кабелей и SU из валов Create. Заменяет любую Cell |

**Совет:** поставь несколько Input блоков для быстрой зарядки. Они буферизуют 50k FE каждый и передают контроллеру.

### Статусы в GUI

| Статус | Значение |
|--------|----------|
| **Incomplete** (красный) | Не все 26 ячеек заполнены |
| **No Shield** (жёлтый) | Мультиблок собран, но на корабле нет активного щита |
| **Ready** (зелёный) | Работает! Батарея заряжена и готова восстанавливать щит |
| **Depleted** (красный) | FE израсходована. Перезаряди батарею |

### Как работает

- **Пассивная регенерация:** каждый удар по щиту → батарея мгновенно восстанавливает **20%** от поглощённого урона, расходуя FE (200 FE = 1 HP)
- **Экстренный сброс:** если HP щита падает ниже **25%** → батарея сбрасывает **ВСЮ** оставшуюся FE в HP (кулдаун 30 секунд)

**Пример:** Netherite щит (500 HP) получает удар на 100 HP → батарея мгновенно восстанавливает 20 HP (20% от 100). Если после удара HP < 125 (25%) → экстренный сброс: 500k FE → 2500 HP.

---

## Cloaking Field Generator (Маскировочное поле)

Альтернатива щиту — делает корабль невидимым.

- Высокий расход энергии (40 FE/тик)
- Ёмкость: 100,000 FE
- **Не защищает от урона** — только скрывает корабль
- Один на корабль

---

## Рецепты крафта

### Генераторы щита

**Iron Shield Generator**
```
[Iron Block] [Redstone]   [Iron Block]
[Iron Block] [Glass]      [Iron Block]
[Iron Block] [Redstone]   [Iron Block]
```

**Diamond Shield Generator**
```
[Diamond Block] [Redstone Block] [Diamond Block]
[Diamond Block] [Glass]          [Diamond Block]
[Diamond Block] [Redstone Block] [Diamond Block]
```

**Netherite Shield Generator**
```
[Netherite Block] [Nether Star]    [Netherite Block]
[Netherite Block] [Diamond Block]  [Netherite Block]
[Netherite Block] [Nether Star]    [Netherite Block]
```

### Вспомогательные блоки

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

### Shield Battery (мультиблок)

**Shield Battery Controller** (x1)
```
[Gold Block]     [Redstone Block] [Gold Block]
[Redstone Block] [Diamond]        [Redstone Block]
[Gold Block]     [Redstone Block] [Gold Block]
```

**Shield Battery Cell** (x4) — нужно 7 крафтов на полный мультиблок
```
[Iron Ingot] [Redstone]     [Iron Ingot]
[Redstone]   [Copper Block]  [Redstone]
[Iron Ingot] [Redstone]     [Iron Ingot]
```

**Shield Battery Input** (x2)
```
[Iron Ingot] [Redstone Block] [Iron Ingot]
[Gold Ingot] [Copper Block]   [Gold Ingot]
[Iron Ingot] [Redstone Block] [Iron Ingot]
```

### Маскировка

**Cloaking Field Generator**
```
[Obsidian]    [Ender Pearl]   [Obsidian]
[Ender Pearl] [Diamond Block] [Ender Pearl]
[Obsidian]    [Ender Pearl]   [Obsidian]
```

---

## Энергия

### Forge Energy (FE)

Подключи любой источник FE:
- Thermal Expansion трубы
- Mekanism кабели
- Flux Networks
- Любой другой мод с IEnergyStorage

### Create SU (Stress Units)

Поставь **вал Create** рядом с генератором или Battery Input. Скорость вращения конвертируется в FE:
- **1 FE/тик за 1 RPM**
- Вал на 256 RPM = 256 FE/тик
- Работает со всеми 6 сторон

---

## Урон по щитам

### Взрывы
| Источник | Урон |
|----------|------|
| Крипер | ~50 |
| TNT | 88 |
| Заряженный крипер / End Crystal | 198 |

### Снаряды
| Источник | Урон |
|----------|------|
| Снежок / Яйцо | 1 |
| Стрела | 5 |
| Огненный шар (блейз) | 8 |
| Трезубец | 15 |
| Фейерверк | 20 |
| Wither skull | 25 |
| Ghast fireball | 30 |
| Дракон fireball | 40 |
| CBC: Solid shot | 50 |
| CBC: HE | 60 |
| Alex's Caves Torpedo | 80 |
| CBC: AP | 80 |
| CBC: Nuke Shell | 200 |
| Alex's Caves Nuclear Bomb | 500 |

---

## Визуальные индикаторы

### Цвет щита
- **Синий** — HP > 50% (здоровый)
- **Жёлтый** — HP 25–50% (повреждён)
- **Красный** — HP < 25% (критический, мерцает)

### HUD
Когда стоишь на корабле с активным щитом — в углу экрана показывается полоска HP.

### GUI генератора
ПКМ по генератору → видишь:
- Полоска HP (с цветом)
- Полоска FE (оранжевая)
- Статус: Active / Inactive
- Радиус щита
- Кнопка Activate / Deactivate

---

## Советы

1. **Начни с Iron** для защиты от мобов, перейди на Diamond/Netherite для PvP
2. **Ставь 2–4 конденсатора** на корабль — дешёвый способ увеличить живучесть
3. **Эмиттеры** полезны для больших кораблей (радиус) и быстрой регенерации
4. **Battery** — must-have для PvP. Пассивная регенерация + экстренный сброс спасают
5. **Несколько Battery Input** ускоряют зарядку — каждый буферизует 50k FE
6. **Create SU** — самый дешёвый способ питать щит. Один водяное колесо уже даёт 256 FE/тик
7. **Клоак + щит** на разных кораблях: один невидимый разведчик, один защищённый танк
