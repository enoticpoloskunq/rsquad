# 🦝 Raccoon Squad

Android VPN клиент на базе Xray-core с поддержкой VLESS, Reality и продвинутыми функциями обхода блокировок.

## Возможности

### Основное
- ✅ **VLESS + Reality** - полная поддержка VLESS URI с Reality
- ✅ **Xray-core** - последняя версия через libv2ray
- ✅ **Jetpack Compose** - современный Material 3 UI

### Обход блокировок
- ✅ **Фрагментация** - TLS Client Hello fragmentation для обхода DPI
- ✅ **Noise** - пакетная обфускация
- ✅ **Косметика** - пресеты и рандомизация параметров
- ✅ **Brute Force** - автоматический подбор косметики для нерабочих нод

### Безопасность
- ✅ **Kill Switch** - блокировка интернета при обрыве VPN
- ✅ **Auto-reconnect** - автоматическое переподключение (до 5 попыток)
- ✅ **Smart Rating** - рейтинг нод по скорости и стабильности

### Удобство
- ✅ **Auto-select** - автоматический выбор лучшей ноды
- ✅ **Country Flags** - определение страны по адресу сервера
- ✅ **Тестирование** - TCP ping и URL тест для всех нод
- ✅ **Мульти-выбор** - массовое удаление нод
- ✅ **Экспорт** - экспорт нод в буфер обмена
- ✅ **Проверка обновлений** - уведомления о новых версиях
- ✅ **Developer Mode** - расширенные логи для отладки

## Скачивание

### Release версия (рекомендуется)
Скачай APK для твоей архитектуры:
- **[arm64-v8a](../../releases/latest)** - большинство современных устройств
- **[armeabi-v7a](../../releases/latest)** - старые 32-битные устройства

### Debug версия
Доступна в [Actions](../../actions) → выбираешь последний билд → Artifacts

## Скриншоты

| Главный экран | Настройки | Редактор ноды |
|---------------|-----------|---------------|
| 🏠 | ⚙️ | ✏️ |

## Сборка

```bash
# Клонировать репозиторий
git clone https://github.com/shrau77/rsquad.git
cd rsquad

# Скачать libv2ray.aar
mkdir -p app/libs
curl -L -o app/libs/libv2ray.aar \
  "https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar"

# Собрать Debug APK
./gradlew assembleDebug

# Собрать Release APK
./gradlew assembleRelease
```

## Требования
- Android 7.0+ (API 24)
- ARM64 или ARMv7 устройство

## Версии

| Версия | Изменения |
|--------|-----------|
| 1.2.0 | Kill Switch, Auto-reconnect, Auto-select, Country Flags |
| 1.1.0 | Brute Force косметика, Smart Rating |
| 1.0.0 | Базовый VLESS + Reality |

## License
MIT
