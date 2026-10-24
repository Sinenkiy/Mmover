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

## Дополнение 2026-10-24: core-fixes archive

Перенос применимых исправлений завершён. Цель — MMover 2.01/201, ветка 201. До начала изменений рабочее дерево было чистым. Проверены SHA256 всех 13 записей пакета MouseMover-core-fixes-for-second-app.zip; эталон MouseMover использовался только для чтения. Версия и настройки сборки берутся из текущего проекта, а не из устаревших сведений сентябрьского отчёта выше.

### Сопоставление и изменения

- A: Main имел только tint стандартного SwitchCompat. Добавлены собственные XML thumb/track и их назначение в activity_main.xml до создания View. Переключатель больше не зависит от растровых ресурсов AppCompat, вынесенных в density split.
- B: vibration_enabled использовал стандартный widget SwitchPreferenceCompat. Добавлен settings_switch_widget с теми же XML shapes, обязательным switchWidget ID и отключёнными clickable/focusable; обработка клика остаётся у строки preference.
- Существующие цвета совпали с эталоном и сохранены. Ключи/defaults, ограничения доступа за награду, package/namespace, версия, реклама, платежи, подпись, зависимости и manifest не изменялись. Production Kotlin-код не менялся.
- Адаптирован StealthSwitchResourcesTest; добавлен CoreUiRegressionTest для клика строки, сохранения и accessibility-прокрутки. В MainFlowTest добавлен scrollTo перед кликом «Не сейчас»: при шрифте 150% на API 25 кнопка лежит в штатной прокручиваемой панели Material-диалога. Условия проверки награды и production-диалог не изменены.

C1: собственные Toast проходят BaseActivity.showSafeToast с lifecycle guard, который не перехватывает асинхронный Toast$TN. Источник события эталона неизвестен; исправление Toast BadToken не заявляется. C2: accessibility не отключалась, реальное действие прокрутки проверено. C3–C5 (Meizu ANR, broadcast, native thread OOM) не имеют установленного источника в MMover, поэтому изменения framework/manifest/потоков не внесены. Исключённые рекламные исправления не переносились.

### Проверки текущих исправлений

Сборки assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, lintDebug, assembleRelease и bundleRelease успешны. Release собран с R8/resource shrinking. JVM: **54/54**, без failures/errors. Lint до и после: **0 ошибок, 108 предупреждений**, набор предупреждений не изменился. Финальная сборка androidTest и lint также успешны после удаления экспериментального release-only теста.

| API / AVD | Шрифт 100% | Шрифт 150% | Логи |
| --- | --- | --- | --- |
| 25 / Small_Phone | 7/7 | 7/7 | api25-retry |
| 28 / Pixel_3 | 7/7 | 7/7 | api28 |
| 32 / Pixel_6_Pro | 7/7 | 7/7 | api32 |
| 36 / Medium_Phone_API_36 | 7/7 | 7/7 | api36 |

Итого **56 успешных запусков методов**, а не 56 уникальных тестов. В каждом запуске два resource-теста проверяют реальные Main layout и PreferenceGroupAdapter в светлой/тёмной теме при density 160/240/320/560/640. Дополнительно проверены: изменение вибрации ровно один раз на клик строки, сохранение после пересоздания и повторного открытия, правила доступа главного переключателя, ширина настроек и реальное accessibility ACTION_SCROLL_FORWARD в инструкции. TalkBack вручную не проверялся. Первый API 25 дал 6/7 при крупном шрифте из-за прокрутки тестом; окончательный повтор после исправления harness прошёл полностью.

Minified release отдельно проверен на API 32 обычным запуском через launcher с установкой **только base-master.apk**, без density split. В данном AAB нет отдельного ABI split. Main и Settings открылись; строка вибрации переключила true → false, значение сохранилось после остановки процесса и нового запуска. Скриншоты просмотрены, AndroidRuntime не содержит fatal-ошибок. Диагностические APK из unsigned AAB подписаны только стандартным debug-ключом; production-ключи не использовались. Сеть временного release-эмулятора отключена. Готовность к загрузке в магазин с production-подписью этой проверкой не заявляется.

Две промежуточные проблемы диагностики сохранены в логах: первая установка ошибочно ожидала отсутствующий ABI split; затем debug AndroidJUnitRunner не стартовал поверх minified release из-за отсутствующего kotlin.jvm.internal.Intrinsics. Это ошибки тестового инструмента, не успешные тесты приложения. Несовместимый экспериментальный ReleaseSwitchSmokeTest удалён из исходников; release проверен через интерфейс, без ослабления R8/production-конфигурации.

Все AVD запускались последовательно в одноразовых сеансах -read-only -no-snapshot и закрыты. git diff --check прошёл. Аудит 214 исходных tracked-файлов подтвердил только пять ожидаемых изменений; остальные 209, включая конфигурацию и production Kotlin, совпали по SHA256. Добавлены три ресурса и два тестовых файла. Коммит, push и публикация не выполнялись.

Логи, исходный пакет и аудит: artifacts/core-fixes-2026-10-24/ (исключены из Git). Основные файлы: build.log, final-validation.log, preservation-audit.json, baseline-lint.xml; каталоги матрицы указаны в таблице. Финальная release-проверка: api32-release-ui/result.txt, runtime-log.txt, XML и скриншоты. Неуспешные попытки: api25, api32-release. Результаты unit/lint также находятся в app/build/test-results/testDebugUnitTest и app/build/reports/lint-results-debug.html. AAB: app/build/outputs/bundle/release/app-release.aab, без production-подписи.

## Обновление 2026-10-24: target SDK 37 / Yandex 8.6.0

Обновление завершено. В app/build.gradle.kts установлены compileSdk=37 и targetSdk=37. minSdk=24, package/namespace com.divinegames.mmover, versionName=2.01 / versionCode=201 и настройки подписи сохранены. Использована установленная финальная платформа Android 37.0 revision 2 (PreviewSdkInt=0). Версии AGP/Gradle/Kotlin не менялись; AGENTS.md приведён в соответствие фактической конфигурации.

Yandex Mobile Ads обновлён с 7.17.0 до **8.6.0**. На момент проверки это latest/release в Maven Central, версия также подтверждена [официальной историей изменений](https://ads.yandex.com/helpcenter/en/dev/android/changelog-android). Выполнена необходимая [миграция API 8](https://ads.yandex.com/helpcenter/en/dev/android/release/8-0-0-migration):

- MobileAds заменён на YandexAds.
- Баннеры используют AdRequest.Builder(adUnitId) и BannerAdSize.sticky; удалены устаревшие callbacks ухода/возврата.
- Rewarded использует AdRequest и listener, переданный непосредственно в loadAd; при закрытии контроллера вызывается cancelLoading.
- Сохранены lifecycle/generation guards, защита от повторной загрузки и выдачи награды, refresh-политика, пользовательские настройки и все рекламные ID. SDK используется только Yandex; AdMob/Firebase/UMP/Google Play Billing не добавлены. Транзитивная AppMetrica обновилась вместе с SDK до 8.5.1; её analytics-billing-модули не являются интеграцией платежей приложения.

### Сборки и аудит

Успешны assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, lintDebug, assembleRelease и bundleRelease. Release использует R8/resource shrinking. JVM: **54/54**, failures/errors=0. Lint: **0 ошибок / 105 предупреждений**, было 0/108; удалились только предупреждения об устаревших target SDK, compile SDK и Yandex SDK. Новых lint-предупреждений нет.

Готовый release APK проверен aapt2: targetSdk=37, minSdk=24, версия 2.01(201). Список permissions совпадает с предыдущей сборкой; native-библиотек в APK нет. Рекламные ID и условия выбора debug/demo проверены сравнением с сохранённым MainAdsController. git diff --check прошёл. Из 214 исходных tracked-файлов изменены только девять ожидаемых файлов суммарно для обновления SDK и предыдущего переноса; неожиданных изменений нет.

### Runtime-проверки с targetSdk=37

| API / AVD | Регрессия | Дополнительно |
| --- | --- | --- |
| 25 / Small_Phone | 29/29 | 2/2 live demo |
| 28 / Pixel_3 | 29/29 | — |
| 32 / Pixel_6_Pro | 29/29 | — |
| 36 / Medium_Phone_API_36 | 29/29 | 10/10 при шрифте 150%; 2/2 live demo |

Итого **130 успешных запусков методов** на эмуляторах. Регрессия: MainFlowTest, RewardedDialogTest, SettingsNavigationTest, HelpScreensTest, CoreUiRegressionTest, StealthSwitchResourcesTest, SettingsLayoutTest. Проверены движение/остановка/фон, навигация и пересоздание, системные отступы, четыре локализации, сохранение настроек, обе темы и XML-переключатели на пяти плотностях, правила rewarded-доступа.

Live demo на API 25 и 36: реально загрузились оба баннера и rewarded, возврат из инструкции сохраняет контроллер и загруженную рекламу; инициализация SDK не блокирует движение. Оба opt-in параметра включены, каждый из двух методов исполнялся, пропусков не было. Логи подтверждают интеграцию/инициализацию Yandex Mobile Ads 8.6.0. Производственные объявления не нажимались; тесты использовали только debug/demo ID. Полный просмотр/закрытие rewarded в рамках этого обновления не запускался; успешная загрузка не выдаётся за проверку playback.

Все AVD запускались последовательно в одноразовых сеансах -read-only -no-snapshot и закрыты. Пользовательские AVD и их сохранённые данные не изменялись.

### Ограничение проверки API 37 на первом этапе — устранено ниже

На момент завершения первого этапа сборка с API 37 была подтверждена, но runtime API 37 оставался непроверенным: отсутствовал системный образ и требовалось обновление эмулятора. Настройка compile/target сверена с [Android Developers](https://developer.android.com/about/versions/17/setup-sdk). По последующему запросу пользователя среда обновлена и runtime-тесты выполнены; окончательные результаты приведены в разделе «Дополнительная проверка API 37» ниже.

Логи и аудит: artifacts/core-fixes-2026-10-24/sdk37-yandex86/. Основные файлы: final-build.log, release-dependencies.txt, artifact-audit.json, preservation-audit.json, build-artifact-hashes.json; результаты runtime — api25/api28/api32/api36. В sdk37-validation.ps1 сохранены команды матрицы.

AAB: app/build/outputs/bundle/release/app-release.aab. Release APK: app/build/outputs/apk/release/app-release-unsigned.apk. Production-подпись, коммит, push и публикация не выполнялись. Исправления переключателей из предыдущего архива сохранены.

## Дополнительная проверка API 37 — 2026-10-24

По запросу пользователя Android Emulator обновлён с 37.1.11 до **37.2.12** (build 16428233); установлены официальные Android SDK Command-line Tools 23.0 и Google APIs x86_64 system image API 37.0. Создан отдельный AVD **MMover_API_37** на основе Pixel 6 (RAM 2048 МБ). Образ и устройство сохранены для дальнейшего запуска через Device Manager Android Studio. Существующие AVD не менялись.

На реально загруженном устройстве подтверждены Android **17**, SDK **37**, sdk_full=37.0, codename=REL. Установленный MMover имеет versionCode=201, minSdk=24, targetSdk=37. Предыдущее ограничение проверки API 37 снято.

### Совместимость тестовых библиотек

Первый прогон дал 20/29: девять тестов с UI-вводом завершились NoSuchMethodException InputManager.getInstance внутри Espresso 3.6.1. Это несовместимость тестового инструмента с Android 17. Обновлены только зависимости androidTest: **Espresso 3.7.0**, **AndroidX JUnit 1.3.0**. Переход с reflection на getSystemService описан в [официальных AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test). Условия тестов и код приложения для этого не менялись. Ранние логи сохранены в api37-initial.

### Окончательные результаты

| Конфигурация API 37 | Результат | Лог |
| --- | --- | --- |
| Телефон, основной набор | 29/29 | api37/regression.txt |
| Телефон, шрифт 150% | 10/10 | api37/large-font.txt |
| Телефон, live demo Yandex 8.6.0 | 2/2 | api37/demo-loading.txt |
| Широкий экран 2560×1600, density 320 (1280×800 dp) | 12/12 | api37-tablet/regression.txt |

Итого **53 успешных запуска методов**, без пропусков в этих наборах. Проверены движение/остановка, настройки и пересоздание, системные отступы, темы, XML-переключатели, крупный шрифт, справка и EN/RU/DE/FR. На широком экране дополнительно проверены шапка, навигация/смена языка, справка и ширина настроек. Это отдельный одноразовый сеанс того же AVD с временным изменением размера/плотности; постоянный профиль Pixel 6 сохранён. Скриншоты Main, Settings и инструкции просмотрены для телефона и широкого экрана.

В live demo реально загрузились оба баннера и rewarded; возврат из инструкции сохраняет контроллер и баннеры, SDK не блокирует движение. Оба opt-in параметра liveYandex/optionalSdk включены. Полный просмотр/закрытие rewarded здесь не запускался. Использовались только debug/demo ID.

Сборки assembleDebug, assembleDebugAndroidTest, assembleRelease и bundleRelease успешны; JVM **54/54**. Финальный lint с принудительным повтором задач: **0 ошибок / 103 предупреждения**. Первичная попытка lint использовала устаревшие данные quickfix из кэша; после :app:lintDebug --rerun-tasks проверка завершилась успешно. SHA256 debug APK, unsigned release APK и AAB совпадают с предыдущей сборкой SDK 37 / Yandex 8.6.0: изменение тестовых зависимостей не изменило приложение.

В финальных logcat телефона и широкого экрана нет FATAL EXCEPTION, ANR приложения или Fatal signal. Все тестовые сеансы -read-only -no-snapshot закрыты. Установленные SDK/образ и новый AVD сохранены; временные копии установщика удалены. git diff --check прошёл. Идентификаторы, подпись, рекламные и платёжные настройки не менялись; коммит, push и публикация не выполнялись.

Логи и воспроизводимые команды: **artifacts/core-fixes-2026-10-24/api37-validation/**. Итог: summary.json; окружение: runtime-version.json, installed-app.txt, emulator-after.properties, system-image-package.xml; контрольные суммы: artifact-hashes.json. Сборки: test-tools-build.log, fresh-lint.log; сценарии: run-api37.ps1 и run-api37-tablet.ps1; скриншоты: api37/screens и api37-tablet/screens. Логи установки: sdk-install.log и image-install.log. Неуспешный первый прогон оставлен в api37-initial и не включён в итоговые 53 успешных запуска.

## Обновление зависимостей и Gradle DSL — 2026-10-24

Обработан список из 22 предупреждений пользователя. Версии сверены с метаданными официальных Google Maven / Maven Central; использованы стабильные релизы, без alpha/beta. AGP 9.4.1, Gradle 9.6.0, compile/target SDK 37 и minSdk 24 сохранены.

### Ответ по каждому пункту

| № | Предупреждение | Выполнено |
| --- | --- | --- |
| 1 | activity-ktx 1.9.0 → 1.13.0 | Удалена неиспользуемая запись и версия activityKtx. Приложение использует activity 1.13.0. |
| 2 | core-splashscreen 1.0.1 | Обновлён до 1.2.0. |
| 3 | org.jetbrains.kotlin.android 2.2.10 | Kotlin обновлён до 2.4.21. Отдельный Android Kotlin plugin удалён; используется встроенная поддержка AGP с kotlin-gradle-plugin 2.4.21 в корневом buildscript classpath. Фактический compiler-embeddable 2.4.21 подтверждён dependencyInsight. |
| 4 | core-ktx 1.16.0 | Обновлён до 1.19.1. |
| 5 | appcompat 1.7.1 | Обновлён до 1.8.0. |
| 6 | lifecycle-viewmodel-ktx 2.8.1 | Обновлён до 2.11.0. |
| 7 | lifecycle-runtime-ktx 2.8.1 | Обновлён до 2.11.0; общая версия Lifecycle сохранена. |
| 8 | Material 1.12.0 | Обновлён до 1.14.0. |
| 9 | activity 1.10.1 | Обновлён до 1.13.0. |
| 10 | constraintlayout 2.2.1 | Обновлён до 2.2.2. |
| 11 | Google play-services-ads 23.0.0 | Удалена неиспользуемая запись и версия; Google Ads не добавлен в приложение. |
| 12 | Appodeal sdk 3.7.0.0 | Удалена неиспользуемая запись и версия; Appodeal не добавлен в приложение. |
| 13 | recyclerview 1.3.2 | Обновлён до 1.4.0. |
| 14 | coroutines-android 1.9.0 | Обновлён до 1.11.0. |
| 15 | Неиспользуемый alias androidx-activity-ktx | Удалён вместе с пунктом 1. |
| 16 | Неиспользуемый alias play-services-ads | Удалён вместе с пунктом 11. |
| 17 | Неиспользуемый alias sdk | Удалён вместе с пунктом 12. |
| 18 | Устаревший android / BaseAppModuleExtension | Удалён android.newDsl=false; сборка действительно использует публичный ApplicationExtension. Также удалён android.builtInKotlin=false. |
| 19 | Устаревший kotlinOptions | Перенесён в kotlin.compilerOptions вне android. |
| 20 | Устаревший строковый jvmTarget | Использован типизированный JvmTarget.JVM_11; Java/Kotlin bytecode target остался 11. |
| 21 | enableSplit помечен Incubating | Публичный BundleLanguage.enableSplit в AGP 9.4.1 всё ещё имеет эту аннотацию. Настройка сохранена для доступности всех языков; добавлены объяснение и локальный //noinspection UnstableApiUsage только перед присваиванием. Это документированное подавление IDE-инспекции, а не снятие аннотации в AGP. |
| 22 | activity_main.xml: modeSwitch с weight=1 и wrap_content | layout_width заменён на 0dp. XML thumb/track, цвета и правила доступа сохранены. |

Основания миграции: [встроенная поддержка Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), [обновление KGP в AGP](https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin-upgrade), [compilerOptions](https://kotlinlang.org/docs/gradle-compiler-options.html), [BundleLanguage](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/BundleLanguage), [точечное подавление UnstableApiUsage](https://www.jetbrains.com/help/inspectopedia/UnstableApiUsage.html). Аннотация Incubating дополнительно проверена javap в установленном gradle-api-9.4.1.jar.

### Сборки и сохранение настроек

Успешны assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, lintDebug, assembleRelease и bundleRelease; release использует R8/resource shrinking. JVM: **54/54**, без failures/errors. Lint: **0 ошибок / 88 предупреждений**, было 0/103; удалились 14 предупреждений об обновлении зависимостей и InefficientWeight. Новых предупреждений нет. В libs.versions.toml, app/build.gradle.kts и activity_main.xml предупреждений Android Lint нет. Остальные 88 относятся к ранее существовавшим замечаниям вне присланного списка. IDE-инспекция отдельно в Android Studio не запускалась; после изменений требуется Gradle Sync для актуализации её модели.

Проверены фактические ApplicationExtension, JVM_11, minSdk/targetSdk и все прямые версии releaseRuntimeClasspath. В готовом AAB сохранён LANGUAGE negate=true (языковых split нет). Пакет com.divinegames.mmover, версия 2.01/201, production-код Kotlin, рекламные ID/политики, подпись и платёжные настройки не менялись. Yandex Mobile Ads остался 8.6.0; Google Ads и Appodeal отсутствуют в runtime-графе. Удалены только два флага старого Kotlin/DSL; остальные ранее заданные параметры AGP/R8 сохранены.

### Runtime-проверки

| API / конфигурация | Основной набор | Дополнительно |
| --- | --- | --- |
| 25 / Small_Phone | 29/29 | Шрифт 150% 10/10; live demo 2/2 |
| 28 / Pixel_3 | 29/29 | — |
| 32 / Pixel_6_Pro, окончательный повтор | 29/29 | Временный авиарежим в read-only сеансе |
| 36 / Medium_Phone_API_36 | 29/29 | — |
| 37 / MMover_API_37 | 29/29 | Шрифт 150% 10/10; live demo 2/2 |
| 37 / широкий экран 2560×1600, density 320 | 12/12 | SplashLifecycleTest 3/3 |

Итого **184 успешных запуска методов в окончательных наборах**, без пропусков. Скриншоты главной панели просмотрены на маленьком/обычном/широком экране, а также Settings на широком экране. Сохранились обе темы, четыре локализации, переключатели, движение/остановка, настройки и поведение при пересоздании. Live demo проверяет реальную загрузку двух баннеров и rewarded и сохранение баннеров после инструкции; полный просмотр/закрытие rewarded в эту проверку не входил. SplashLifecycleTest проверяет задержку заставки, уход в фон, возврат и уничтожение.

Первый API 32 дал 28/29. Системный лог показывает запуск com.android.settings/.sim.SimDialogActivity в 18:19:45, затем onPause/onResume MainActivity во время ожидания теста. Приложение остановилось при уходе в фон, после чего тест не увидел ожидаемое состояние. Полный повтор в свежем read-only сеансе с временным авиарежимом дал 29/29 без изменений APK, тестов и assertions. В повторе система также инициировала SIM-диалог, но уже на другой фазе; отсутствие этого системного диалога не заявляется. Первый прогон сохранён в api32/ и не включён в 184 успешных запуска; окончательный — api32-retry/. Ключевые строки диагностики: api32-sim-dialog-diagnosis.txt.

В финальных logcat нет FATAL EXCEPTION, ANR приложения или Fatal signal. Эмуляторы запускались последовательно с -read-only -no-snapshot и закрыты. Данные пользовательских устройств не очищались. git diff --check прошёл; аудит исходных tracked-файлов показывает только семь ожидаемых изменений этой задачи (каталог, два build.gradle.kts, gradle.properties, activity_main.xml, AGENTS.md и этот отчёт). Production Kotlin и androidTest-код не менялись.

Артефакты: artifacts/core-fixes-2026-10-24/dependency-cleanup/ (исключены из Git). Сохранены исходные семь файлов before/, source-hashes-before.json, stable-versions.json и XML метаданных; build.log, kotlin-compiler.log, resolved-dependencies.log, dsl-verification.log, bundle-config.json, release-badging.txt, final-lint.xml, lint-delta.json, unit-summary.json. Итоги: test-summary.json, source-change-audit.json и build-artifact-hashes.json. Сценарии: run-matrix.ps1, run-api32-retry.ps1 и run-tablet.ps1. Коммит, push и публикация не выполнялись.
