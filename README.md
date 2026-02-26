# 🦝 Raccoon Squad

Android VPN клиент на базе Xray-core с поддержкой VLESS, Reality и продвинутыми функциями обхода блокировок.

## 📥 Скачать

| Версия | Архитектура | Скачать |
|--------|-------------|---------|
| **Release** | arm64-v8a (большинство устройств) | [raccoon-squad-arm64-release.apk](../../releases/download/v1.3.0/raccoon-squad-arm64-release.apk) |
| **Release** | armeabi-v7a (старые устройства) | [raccoon-squad-armeabi-release.apk](../../releases/download/v1.3.0/raccoon-squad-armeabi-release.apk) |
| **Debug** | arm64-v8a | [raccoon-squad-arm64-debug.apk](../../releases/download/v1.3.0/raccoon-squad-arm64-debug.apk) |
| **Debug** | armeabi-v7a | [raccoon-squad-armeabi-debug.apk](../../releases/download/v1.3.0/raccoon-squad-armeabi-debug.apk) |

> **Рекомендуется**: Release версия для arm64-v8a

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
- ✅ **Signature Check** - защита от пересборки

### Удобство
- ✅ **Pre-flight Check** - проверка нод перед подключением
- ✅ **Auto-select** - автоматический выбор лучшей ноды
- ✅ **Country Flags** - определение страны по адресу сервера
- ✅ **Тестирование** - TCP ping и URL тест для всех нод
- ✅ **Мульти-выбор** - массовое удаление нод
- ✅ **Экспорт** - экспорт нод в буфер обмена
- ✅ **Проверка обновлений** - уведомления о новых версиях
- ✅ **Developer Mode** - расширенные логи для отладки

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

# Собрать Release APK (obfuscated, minified)
./gradlew assembleRelease
```

## Требования
- Android 7.0+ (API 24)
- ARM64 или ARMv7 устройство

## Версии

| Версия | Изменения |
|--------|-----------|
| 1.3.0 | UI/UX Redesign: Material Icons, professional look, English UI |
| 1.2.0 | Kill Switch, Auto-reconnect, Pre-flight check, Signature verification |
| 1.1.0 | Brute Force косметика, Smart Rating |
| 1.0.0 | Базовый VLESS + Reality |

## Безопасность
- **Obfuscation**: R8 full mode с ProGuard правилами
- **Minification**: Код минифицирован в release сборке
- **Signature Check**: Приложение закрывается при несовпадении подписи
- **No External Storage**: Не требует разрешений для внешнего хранилища
- **Debug Disabled**: Релизные сборки не отлаживаемые

## License
MIT
