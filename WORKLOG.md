# Raccoon Squad - Ход работы

## Проект
Android VPN клиент на VLESS + Reality + XTLS-RPRX-VISION

## Репозиторий
https://github.com/shrau77/rsquad

## Токен
(в личке у пользователя)

---

## Выполнено

### Исправлено (25.02.2026)
- [x] Ошибка компиляции: добавлен `import kotlinx.coroutines.isActive` в NodeViewModel.kt
- [x] Коммит запушен: 4c725cb

### Ранее выполнено
- [x] TCP ping результаты теперь сохраняются в JSON (latency field в configToJson/jsonToConfig)
- [x] Счётчик нод отображается в заголовке "(50)"
- [x] Анимации при переключении нод
- [x] Сброс трафика при переключении ноды
- [x] Постоянный keystore для обновлений APK без переустановки
- [x] URL тест без VPN (метод testNodeUrl в NodeTester.kt)
- [x] Прогресс теста (30/50) в TestDialog
- [x] Кнопка отмены теста в TestDialog
- [x] Авто-закрытие диалогов после запуска действия

---

## Фичи в UI (уже реализованы в HomeScreen.kt)

### TestDialog содержит:
- Прогресс: "Тестирование: $tested / $total"
- Кнопка отмены: "❌ Отменить"
- TCP Ping всех нод
- URL тест всех нод (без VPN)
- URL тест текущей ноды (через активный VPN)
- Тест ВСЕХ через активный VPN
- Удалить недоступные (auto-clean)

### VpnStatusBanner содержит:
- Статус VPN (активен/отключен)
- Имя активной ноды
- Exit IP + страна (Check IP кнопка)
- Трафик в реальном времени

---

## В планах

### Ближайшие
- [ ] Splash Screen
- [ ] Фиолетовая тема
- [ ] Smart Rating (просить рейтинг после N успешных подключений)
- [ ] Кнопка "Доктор" (диагностика проблем)

### На будущее
- [ ] Группировка нод по подпискам
- [ ] Автообновление подписок
- [ ] Виджет для быстрого включения/выключения

---

## Ключевые файлы

| Файл | Описание |
|------|----------|
| `app/src/main/java/com/raccoonsquad/ui/viewmodel/NodeViewModel.kt` | ViewModel с тестами, импортом, состоянием |
| `app/src/main/java/com/raccoonsquad/ui/screens/HomeScreen.kt` | Главный экран с UI |
| `app/src/main/java/com/raccoonsquad/core/util/NodeTester.kt` | TCP ping, URL тест, тест через VPN |
| `app/src/main/java/com/raccoonsquad/data/repository/NodeRepository.kt` | Хранение нод в DataStore |
| `app/src/main/java/com/raccoonsquad/core/vpn/RaccoonVpnService.kt` | VPN сервис |
| `app/src/main/java/com/raccoonsquad/core/xray/XrayWrapper.kt` | Обёртка над libv2ray |

---

## Статус билда
Проверить: `curl -s "https://api.github.com/repos/shrau77/rsquad/actions/runs?per_page=1" | jq '.workflow_runs[0] | {status, conclusion}'`

---

## Примечания
- 138 пушей на момент 25.02.2026
- Часто не компилилось, но проект живой
- Основная ветка: main
