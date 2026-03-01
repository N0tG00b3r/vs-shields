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

---

## В работе

### Система крафтовых компонентов (`.docs/IMPL_PLAN_Components.md`)

Полный план в `.docs/IMPL_PLAN_Components.md`, ТЗ для дизайнера — в `.docs/DESIGN_TZ_Components.md`.

- [ ] **Этап 1** — Зарегистрировать 13 новых предметов в `ModItems.java`
  - 11 компонентов (обычный Item, stacksTo 64)
  - EnergyCellItem.java (useOn() → заряжает генераторы)
  - VoidShardItem (stacksTo 16)
- [ ] **Этап 2** — Placeholder-текстуры через Python (13 однотонных PNG 16×16)
- [ ] **Этап 3** — Рецепты JSON (12 новых в `data/vs_shields/recipes/components/`)
- [ ] **Этап 4** — Обновить 21 существующий рецепт (заменить ингредиенты на новые компоненты)
- [ ] **Этап 5** — LivingDropsEvent: Void Shard с эндерменов (шанс из конфига)
- [ ] **Этап 6** — Lang-ключи (en_us + ru_ru, 13 строк)
- [ ] **Этап 7** — Creative Tab: добавить новые предметы
- [ ] **Этап 8** — ShieldConfig: добавить `voidShardEndermanChance: 0.05`

### Текстуры компонентов

- [ ] Получить финальные PNG от дизайнера (ТЗ: `.docs/DESIGN_TZ_Components.md`)
- [ ] Заменить placeholder-ы на финальные текстуры

### Визуальная проверка кабины

- [ ] Запустить `./gradlew runClient`, проверить в игре:
  - Каркас рендерится как solid (непрозрачный)
  - Стёкла полупрозрачные (translucent)
  - Видны с обеих сторон (внутри и снаружи)
  - Ориентация при установке (FACING = направление лётчика)

---

## Backlog / под вопросом

- [ ] Keybind для бокового RCS (стрейф) — в коде есть `lateralDir` в пакете, но клиентский keybind не реализован
- [ ] Рецепты для Diamond/Netherite Shield Generator (сейчас только Iron)
- [ ] Версия 0.0.9 — выпустить после реализации компонентов
