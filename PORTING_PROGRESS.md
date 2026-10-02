# Перенос MouseMover → MMover

Дата: 30 сентября 2026. Цель: `C:\Users\antro\AndroidStudioProjects\MMover`, подтверждена пользователем. Эталон: фактическое рабочее дерево `C:\Users\antro\AndroidStudioProjects\MouseMover`, включая незакоммиченные и новые файлы, только для чтения.

## Итог

Применимые изменения из PORTING_GUIDE_RU.md реализованы. Основная матрица API 25, 28, 32, 36 пройдена. Дополнительные проверки и ограничения перечислены ниже. Изменения оставлены в рабочем дереве, без коммита, публикации и подписи production-сборки.

## Сопоставление перед реализацией и результат

| Область | Исходный MMover | Решение и результат |
| --- | --- | --- |
| Идентичность | com.divinegames.mmover, 104 / 1.04, RuStore | Сохранены пакет, версия, бренд, контакты, иконки, настройки подписи и карточка магазина |
| Стек | min 24, compile/target 35; Kotlin 2.0.21, AGP 8.12.3, Gradle 8.13 | Сохранён; добавлены необходимые AndroidX/coroutines и desugaring для java.time на старых API |
| Движение | Логика внутри Activity, старые фоны | MotionController, scheduler, монотонные deadlines, один callback, остановка при onPause, освобождение при destroy/detach |
| Фоны | Растровые фоны и старые значения preferences | Пять процедурных фонов, 240×240 BitmapShader, отменяемая генерация вне UI, preview; безопасная миграция старых ID |
| Направление | Старое движение | Поворот через 2000 px пути; полосы вверх/вниз, остальные случайно; Choreographer при animator scale 0/1 |
| Defaults | Старые значения 5 с / 300 пикс/с | 10 с / 500 пикс/с только при отсутствующих ключах; сохранённые значения не перезаписываются; pause_duration остаётся шагами по 5 с |
| UI и навигация | Старые Main, Settings, Start, Info | Новые панели, шапка/insets, подсказки, настройки с preview, инструкция/FAQ, правильное восстановление и возврат к существующему Main |
| Waiting | Затемнение, таймер | Сохранены чёрный фон, центральный таймер, режим и остановка по нажатию |
| Доступ за награду | Логика внутри Activity | RewardAccess/Session/Controller, сохранение даты/флага, полночь, защита от повторной выдачи; только callback награды |
| Реклама | Только Yandex Mobile Ads 7.17.0 | Собственный Yandex-only MainAdsController; lifecycle, защита поздних callbacks/дублей, корректное закрытие, ошибки/retry, sticky refresh оставлен SDK |
| Firebase / AdMob / UMP / медиация | Нет действующей интеграции | Не добавлены; региональные политики и privacy-flow эталона неприменимы |
| Оплата | Информационный диалог, действующего биллинга нет | Сохранено; перенос не внедряет покупку или Google Play Billing |
| Локализации | EN/RU/DE/FR | Сохранены исходные тексты, добавлены необходимые новые строки и массивы; проверены четыре языка |
| R8 / backup | Shrinking в release | Сборка release проходит, сохранены SourceFile/LineNumberTable; banner_timing исключён из cloud/device transfer backup |
| Документация | Руководство, нет собственных актуальных правил | Создан AGENTS.md для MMover, настоящий отчёт; исходное руководство сохранено |

Ссылки «Поделиться»/«Оценить» направлены на исходную карточку RuStore. HEAD-запрос подтвердил HTTP 200 с перенаправлением на `https://www.rustore.ru/catalog/app/com.divinegames.mmover`. Обработка отсутствующего обработчика Intent предусмотрена. Google Play ссылки эталона не перенесены.

Release ID сохранены: верхний `demo-banner-yandex` (так было в MMover), нижний `R-M-17944676-2`, rewarded `R-M-17944676-3`. Debug использует только demo ID. Исходная сетевая политика с разрешённым cleartext сохранена; HTTPS-only не заявляется. largeHeap, manifest и собственные permissions не изменены.

## Сборки и статические проверки

- Исходные assembleDebug, testDebugUnitTest и lintDebug прошли до переноса; исходный APK сохранён для проверки обновления.
- После переноса: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, lintDebug, assembleRelease — успешно. Release с R8/resource shrinking, unsigned.
- JVM: **54 теста, 0 failures/errors** (Motion, MovementDirection, ProceduralBackground, RewardAccess, AdRequestPolicy, BannerRefreshController, DiagnosticThrottle, исходный example).
- Lint: **0 ошибок, 109 предупреждений**, исходно 0/94. Предупреждения включают legacy-ресурсы, рекомендации обновления/KTX/layout; общих suppress не добавлено. Исходный дефект нулевой ширины скрытого spinner из эталона не перенесён.
- В releaseRuntimeClasspath нет Firebase, AdMob, UMP и Google Play Billing; Yandex остался 7.17.0. Транзитивная аналитика AppMetrica не является интеграцией оплаты приложения.
- `git diff --check` — успешно. Аудит хэшей: 171 файл исходников/ресурсов эталона без изменений, 39 защищённых файлов MMover без изменений. Пользовательская правка `.idea/deviceManager.xml` сохранена.

Логи: `artifacts/porting/final-build.log`, `release-dependencies.txt`, `preservation-audit.json`, исходные APK/хэши/lint рядом. Детали тестов: `app/build/test-results/testDebugUnitTest`, lint: `app/build/reports/lint-results-debug.html`.

## Основная матрица Android

| API / AVD | Основная регрессия | animator scale 0 | Обновление / чистая установка | Итог |
| --- | --- | --- | --- | --- |
| 25 / Small_Phone | 35/35 | 10/10 | 1/1 + 1/1 | 47/47 |
| 28 / Pixel_3 | 35/35 | 10/10 | 1/1 + 1/1 | 47/47 |
| 32 / Pixel_6_Pro | 35/35 | 10/10 | 1/1 + 1/1 | 47/47, окончательный повтор api32-retry |
| 36 / Medium_Phone_API_36 | 35/35 | 10/10 | 1/1 + 1/1 | 47/47 |

Основной набор: MainFlow, SettingsNavigation, SettingsLayout, BackgroundPreview, ProceduralBackground, MovementDirection, Onboarding, HelpScreens, ButtonHints, SplashLifecycle, RewardedDialog. animator scale 0: MainFlow + MovementDirection. Это повторные запуски части тех же методов, не 47 различных методов на каждом API.

Обновление проверено установкой нового debug APK поверх сохранённого baseline debug APK с тем же пакетом/отладочной подписью. Сохранены заданные старые значения active=7, speed=700, pause=17, brightness=65, vibration=false, язык RU, маркер даты/доступа; старый фон мигрировал в mosaic. Отдельно после очистки данных временного AVD проверены defaults 10/500/noise. Production-обновление с релизной подписью не проверялось.

AVD запускались последовательно с `-read-only -no-snapshot`, изменения настроек оставались в одноразовых сеансах. Пользовательские устройства не очищались. Логи и скриншоты: `artifacts/porting/api25`, `api28`, `api32-retry`, `api36`.

## Дополнительные проверки

- API 31 Pixel 6: геометрия шапки и четыре сценария rewarded-диалога — **5/5**.
- API 36, тёмная тема и шрифт 1.5: FAQ/инструкция, layout настроек, preview — **9/9**.
- API 36, live demo Yandex: загрузка двух баннеров и rewarded, сохранение контроллера/баннеров после посещения инструкции — **1/1**. Второй opt-in метод в этом запуске пропущен; строка JUnit OK(2 tests) не означает два исполненных сетевых теста.
- API 36 с отключёнными Google Play services и Play Store: **20/20** (1 проверка SDK/движения + 19 основного сценария). Отключение обоих точных имён пакетов подтверждено; файлы environment.txt, optional-sdk.txt, core.txt в api36-no-gms-verified.
- API 25 Nexus 9: свежий повтор **9/9**, затем шрифт 1.5 **9/9** (SettingsLayout, BackgroundPreview, HelpScreens). Скриншоты и логи api25-tablet-retry. Команда переключения системной ночной темы на этом API не поддерживается; второй файл назван tablet-dark-large-font.txt, но он подтверждает крупный шрифт, не тёмную тему. Тёмная тема проверена отдельно на API 36.
- Demo rewarded: загрузка, реальный просмотр и выдача награды/даты подтверждены. Полный сетевой тест возврата **не прошёл**: Main остался STARTED после ожидания закрытия экрана SDK. У demo-кнопки нет accessibility-подписи; Back и попытка визуального закрытия в срок не завершили сценарий. Файлы api25-sdk/rewarded-playback.txt, api25-reward-retry, api25-reward-final; последний содержит скриншот. Возврат после реального demo-показа требует ручной проверки на устройстве; успешным этот тест не считается.

## Память API 25

Фактические RAM 1 055 096 832 байт, heap limit 256 МиБ; `wm size 1200×1800`, область приложения 1200×1704. Все пять фонов, прогрев, два цикла движения, 100 переключений, одинаковый явный GC до/после.

| Режим | PSS до → после GC, КиБ | Java до → после, КиБ | Native до → после, КиБ |
| --- | --- | --- | --- |
| Рекламные запросы заблокированы | 37 671 → 36 360 | 6 936 → 5 518 | 16 822 → 16 702 |
| Demo SDK: оба баннера и rewarded загружены | 59 690 → 60 062 | 36 954 → 35 463 | 23 275 → 23 308 |

Оба прогона прошли, OOM нет. SDK diagnostics подтверждает загрузку всех трёх блоков во втором прогоне. 230 400 байт — размер только пикселей одной текстуры, не память приложения. PSS отражает короткие локальные замеры; они не доказывают отсутствия долгосрочных утечек. Raw CSV/device/meminfo: `api25/background-memory` и `api25-sdk/screens-and-metrics/background-memory-sdk`.

## Обнаруженные проблемы проверки

- API 28: первый AVD не подключился к ADB; свежий запуск прошёл.
- API 32: первый основной прогон 33/35 и animator0 9/10. Исправлен тестовый harness: ожидание результата UI-клика и вызов ActivityScenario.onActivity на главном потоке через onFrameActivity, без off-thread ожидания idle при непрерывном Choreographer. Условия проверок сохранены, production-код из-за этого не менялся. Окончательный повтор полностью успешен; ранние логи сохранены.
- Первоначальная попытка отключения GMS на API 36 отключила лишь Play Store: проверка по подстроке ошибочно совпала с gms.supervision. Этот прогон не считается проверкой отсутствующих GMS. Повтор проверяет точное имя основного пакета через pm list packages -d.
- Первый планшетный AVD потерял системный процесс ActivityManager при прогоне локализаций; instrumentation сообщил System has crashed. Этот первый запуск не считается успешной проверкой; повтор в свежем AVD прошёл полностью.
- Первый сетевой rewarded-тест получил награду, но Back не закрыл экран SDK. Повторы с поиском доступной кнопки и визуальным закрытием также не подтвердили возврат в RESUMED. Проверки выдачи награды и даты прошли до этого assertion. Production-код не менялся; незавершённый сетевой сценарий оставлен явным ограничением.

## Ограничения и дальнейшая приёмка

Полный сетевой сценарий закрытия demo rewarded и возврата требует ручной проверки (описанный выше opt-in тест не прошёл). Не проверены физическая мышь, рабочий производственный rewarded-блок, подписанный release и обновление поверх магазинной версии. Сборка unsigned release не заменяет эти проверки. Подпись/публикация не выполнялись согласно руководству. Чистого AOSP-образа без GMS нет; отключение пакетов в Google API AVD не эквивалентно проверке на таком образе. Полноценная оплата остаётся отдельной задачей, поскольку исходный MMover её не реализовывал.

Поведение SDK refresh сверено с [официальным FAQ Yandex](https://ads.yandex.com/helpcenter/en/support/faq/sdk-integration). Причина harness-ожидания сверена с [исходником ActivityScenario](https://github.com/android/android-test/blob/main/core/java/androidx/test/core/app/ActivityScenario.java).
