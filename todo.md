# VS Shields — TODO

## Готово (v0.0.8 + hotfixes)

- [x] Десантная капсула — базовая механика (сборка VS2-корабля, фазы AIMING/BOOST/COAST/DRILL/BREACH)
- [x] Рулевое управление мышью (3°/тик, BOOST и COAST)
- [x] RCS вертикальная тяга (Space = вверх, C = вниз)
- [x] HUD (скорость м/с, шкала топлива BOOST)
- [x] Камера (MixinCockpitCamera через VS2 setupWithShipMounted)
- [x] Исправлена резинка камеры при скорости >10 м/с (COCKPIT_SY_POS EntityDataAccessor)
- [x] Исправлено принудительное переключение на вид от 1-го лица
- [x] Исправлено направление полёта (180° разворот blockstate)
- [x] Исправлено управление рулём в сторону прицела (Camera.getLookVector() вместо player.getYRot/XRot)
- [x] Новая составная модель кабины — каркас (solid) + стёкла (translucent), двусторонние грани
- [x] Исправлены фиолетовые частицы при ломке кокпита (particle текстура на корне composite-модели)
- [x] Игрок в кабине крупнее (0.6×0.9) и сидит ниже на сиденье (ridingOffset −0.3, eyeHeight 0.7)
- [x] Валидация сборки: ровно 1 кокпит + 1 двигатель, иначе — красное сообщение
- [x] Whitelist сборки: любой посторонний блок vs_shields рядом с капсулой → ошибка "pod_invalid_block"
- [x] Исправлен щит: снаряды экипажа изнутри (стрелы, CBC-снаряды) больше не блокируются своим щитом
- [x] Ребаланс HP ×2: Iron 200, Diamond 500, Netherite 1000
- [x] Ребаланс реген ×2: Iron 1.0, Diamond 2.0, Netherite 4.0 HP/тик
- [x] Ребаланс Конденсатор +100 max HP (было +50), Эмиттер +1.0 HP/тик (было +0.5)
- [x] Ребаланс Батарея щита: ёмкость 400,000 FE (было 200,000)
- [x] Русские lang-ключи для boarding pod (отсутствовали)
- [x] Redstone-активация щита: сигнал HIGH → щит включается, LOW → выключается (polling `hasNeighborSignal` в serverTick, edge detection)
- [x] Исправлен CGS: снаряды Nail Gun и Blaze Gun теперь дамажат щит (NTGL `ProjectileEntity` наследует `Entity`, не `Projectile` — добавлены `cgs`/`ntgl` в `isCbcEntity()`)

---

## В работе

### Система крафтовых компонентов (`.docs/IMPL_PLAN_Components.md`)

- [x] **Этап 1** — 13 новых предметов в `ModItems.java` + `EnergyCellItem.java` + `VoidShardItem`
- [x] **Этап 2** — Финальные PNG от дизайнера скопированы в `textures/item/`
- [x] **Этап 3** — 13 item model JSON + 13 lang-ключей (en_us + ru_ru)
- [x] **Этап 4** — 12 рецептов компонентов в `data/vs_shields/recipes/components/`
- [x] **Этап 5** — 21 существующий рецепт обновлён (незерит-гейт через hardened_casing)
- [x] **Этап 6** — `VoidShardDropHandler.kt` — LivingDropsEvent для эндерменов и Дракона
- [x] **Этап 7** — Creative Tab обновлена (+13 предметов)
- [x] **Этап 8** — ShieldConfig: `voidShardEndermanChance`, `voidShardDragonMin/Max`, `energyCellFE`

### Текстуры компонентов

- [x] Заменить текущие PNG на финальные текстуры (если дизайнер обновит)

### Tactical Goggles (`.docs/IMPL_PLAN_TacticalGoggles.md`)

- [x] **Этап 1** — `TacticalGogglesItem` (ArmorItem LEATHER/HELMET + Curios head-тег), регистрация, lang-ключи
- [x] **Этап 2** — `HelmAnalyzerHandler` + `CuriosIntegration.hasGogglesInHeadSlot()` (сканер Y)
- [x] **Этап 3** — Zoom (Shift+V, `ViewportEvent.ComputeFov`, GogglesZoomHandler.kt)
- [x] **Этап 4** — 3D рендер на голове (`TacticalGogglesModel` + `TacticalGogglesRenderer` + `TacticalGogglesForgeMixin`)
- [x] **Этап 5** — Рецепт JSON (`resonance_lens` × 4 + `void_capacitor` × 4 + `ship_analyzer` в центре)
- [x] **Этап 6** — Удалён `TacticalNetheriteHelm` полностью, ночное зрение (серверный PlayerTickEvent)
- [x] Двойной слот: обычный шлем (ArmorItem) ИЛИ Curios head — все 3 способности работают в обоих
- [x] Получить финальные текстуры от дизайнера (ТЗ: `.docs/DESIGN_TZ_TacticalGoggles.md`)

---

## Известные баги

- [ ] **Nailgun (CGS) не дамажит щит** — Blaze Gun работает, Nail Gun нет. Первая попытка фикса (`inflate(200)` + coreAABB check в `ShieldBarrierHandler`) не помогла. Нужна отладка в рантайме: проверить фактический `ForgeRegistries.ENTITY_TYPES.getKey()` для снаряда Nail Gun, убедиться что entity попадает в `isCbcEntity()`, проверить `xOld/yOld/zOld`.
