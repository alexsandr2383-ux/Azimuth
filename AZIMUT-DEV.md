# Азимут / Azimuth — документация разработчика (v0.5.0)

Всё, что нужно для продолжения работы. Пользовательская инструкция — `INSTRUKCIYA.md` (обновляется вместе с кодом).

## Сборка и тесты

```
# JDK: D:\Azimut\.tools\jdk-21.0.13.0.2+1  (JAVA_HOME)
.\gradlew.bat compileKotlinDesktop compileDebugKotlinAndroid   # быстрая проверка
.\gradlew.bat assembleDebug                                     # APK: composeApp\build\outputs\apk\debug\
.\gradlew.bat createDistributable                               # Desktop: composeApp\build\compose\binaries\main\app\Azimuth\Azimuth.exe
.\gradlew.bat desktopTest                                       # unit-тесты (53)
```

**ВАЖНО: тесты запускать с ASCII-хомом Gradle** — с кириллическим `C:\Users\Александр\.gradle` воркер тестов падает
(`GradleWorkerMain ClassNotFoundException`). Рабочий обход:

```
set GRADLE_USER_HOME=D:\Azimut\.gradle-home
.\gradlew.bat desktopTest
```
`.gradle-home` (1.6 ГБ, кэши зависимостей) и `.gradle-tmp` — в `.gitignore`.

**Установка на телефон (USB, adb):**
```
C:\Users\Александр\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r composeApp\build\outputs\apk\debug\composeApp-debug.apk
```

**INSTRUKCIYA.docx/pdf** регенерируются из md:
```
pandoc INSTRUKCIYA.md -o INSTRUKCIYA.docx --from gfm --toc --toc-depth=2
powershell: Word COM → SaveAs(..., 17)   # xelatex/pandoc-pdf на машине нет
```
Ярлык на рабочем столе (`Azimut.lnk`) указывает на exe из `composeApp\build\compose\binaries\main\app\Azimuth\`.

## Версионирование (менять всегда вместе)
1. `composeApp/build.gradle.kts` — `versionCode` / `versionName`
2. `SettingsScreen.kt` — InfoRow «Версия»
3. `AboutScreen.kt` — Info «Версия»
4. `HelpScreen.kt` — заголовок «Ограничения версии …»
5. `INSTRUKCIYA.md` — шапка и заголовок ограничений; затем pandoc + Word COM

## Структура кода (composeApp/src)

**commonMain** — вся логика; платформенное только через expect/actual.

| Пакет | Что там |
|---|---|
| `core/dem/` | DEM-подсистема: `DemTile`(грид+билинейка), `HgtReader`(SRTM .hgt 1201/3601), `GeoTiffReader`(несжатые strip/tile Int16/UInt16/Float32, WGS84; сжатые/проекции — отказ через BadFormat), `DemStore`(кэш 8 тайлов, `hasTileFor`), `DemImport`(импорт+коперник-загрузка с докачкой Range), `DemShade`(хиллшейд 384², кэш 6), `TrackElevation`(профиль/набор/сброс, порог сглаживания 3 м, секции уклона ≥15 м), `SusaninWatch`(закат/тупиковая долина, 1 раз за эпизод), `DataNeeds`+`DataFetcher`(**единый принцип «Загрузить?»**) |
| `core/astro/` | `SunTimes`(восход/закат NOAA-простой), `SolarPosition`(**NOAA γ-алгоритм**, точность ~0.1°/1 мин; переписывать только с тестами!), `TerrainDaylight`(`buildHorizon` полный круг, самокалибровка offset — плоский DEM не должен сдвигать время), `TimeUtil` |
| `core/protected/` | `ProtectedAreas`(GeoJSON FC Polygon/MultiPolygon, holes, bbox-индекс, хранение NavData/protected/*.json), `ProtectedGeometry`(ray-casting, пересечение сегментов), `ProtectedWatch`(1 аларм/зону/эпизод, сброс при выходе из всех зон), `OverpassZones`(зоны из OSM через публичный Overpass API, ways→полигоны, relations не собираются) |
| `core/import/` | `TrackImport`(GPX/KML/KMZ/TCX/GeoJSON), `PointsIO`(точки GPX/KML/KMZ/CSV/GeoJSON + экспорт), `ZipKml`(KMZ→KML), `ZipKmlWriter`(KML→KMZ), `TrackImportDetect` |
| `core/sync/` | `SyncEngine`(манифест путь/размер/mtime/хеш-мелких; «новее побеждает», tie ±500 мс, конфликт-копии, никогда не удаляет, backups//*.tmp исключены), `WebDavStorage`, `LanHttpStorage`+`LanSyncService`(desktop-сервер на JDK HttpServer, токен, путь-guard), `LocalFolderStorage`(desktop java.nio zip-slip-guard / Android SAF), `SecretStore`(Android Keystore AES-GCM / Windows AES+ключ в Документы\Azimut\azimut.key — DPAPI из JVM недоступен) |
| `core/export/` | `TrackPrecheckEngine`, `TrackSheet`+`TrackImageRenderer`(desktop AWT / Android Canvas; кириллица — платформенным рендером), `PdfWriter`(свой, FlateDecode RGB + ASCII-футер), `Deflate` |
| `core/compare/` | `TrackCompare` — сравнение записанного трека с планом: боковое отклонение (avg/max/доля >50 м), дистанция, время (`Track.plannedTimeSeconds`), высоты через `TrackElevation`; считать на Dispatchers.Default |
| `core/fuel/` | `FuelRange` — трамплины-топливо: остаток хода на полном баке, точки «резерв»/«сухой бак» на линии маршрута; настройки `fuelTankLiters/fuelConsumptionL100/fuelReserveLiters` (0 = выкл); считать на Dispatchers.Default |
| `core/map/` | `MapStyles`, `UserMaps`(.mbtiles), `TileDb`(.sqlitedb/.gpkg→.mbtiles, gpkg y-flip по спеке; sqlitedb-зеркальность лечится маркером `<имя>.flip` в import/), `UserXyz`(XYZ-карты по URL-шаблону) |
| `core/model/` | модели; `Geo.parseCoordPair` (DD/DMS/кириллические СЮВЗ/запятые); Waypoint.favorite; Track.fordCount |
| `ui/screens/` | `MapScreen`(2168 строк — сердце; при росте выносить блоки; окно маршрута → `MapRouteDialog.kt`, контекст-меню карты → `MapContextMenu.kt`), `PointsScreen`(фильтры/поиск/импорт/экспорт/избранное), `JournalScreen`(проверка+PNG/PDF), `SettingsScreen`(секции: Карты/DEM/ООПТ/XYZ/Синк/Запись/Погода/Интерфейс), `SosScreen` — **удалён**, координаты в диалоге точки |
| `ui/components/Files.kt` | `rememberTrackFilePicker`(мульти-формат), `rememberBytesSaver`, `rememberFolderPicker`(desktop JFileChooser / Android SAF+persistable) |
| `ui/components/AppBottomNav.kt` | нижняя панель «Карта|Точки|Журнал|Ещё» (ширина < 600 dp; «Ещё» — ModalBottomSheet) |
| `ui/components/Photos.kt` | фото-пайплайн; `rememberPhotoCapture` возвращает (имя, EXIF-geo) — геотеги точек |
| `core/model/TrackSimplify.kt` | Дуглас–Пекер (1,5 м) для рендера обеих платформ; generic `simplifyPoints` + `simplifyTrack`/`simplifyGeoLine` |
| `core/model/ExifGeo.kt` | pure EXIF GPS-парсер JPEG (desktop не имеет платформенного) |
| `core/storage/Backup.kt` | `pruneBackups(keep)` — ретеншн; настройка `backupKeep` (default 20) |
| `ui/components/Share.kt` | системный шэринг (Android ACTION_SEND / desktop clipboard) |

**Правила, которые нельзя ломать:**
- тр() на каждый пользовательский текст (ru/en); строки, используемые в onClick/launch-колбэках, **хоистить** наверх composable (tr — @Composable).
- IO — Dispatchers.IO/Default; запись трека никогда не блокируется; тяжёлое (парсинг зон/DEM/сериализация GPX) — не в композиции и не на main.
- Новые поля моделей — только с default (JSON-миграция бесплатная).
- Новые папки NavData — добавить в `PlatformFiles.ensureDirs()` (оба actual) и в zip-бэкап.
- offline-first: сеть только по явному действию или подтверждению «Загрузить?».
- После любого изменения NavData — `dataVersion++` (App.kt) или локальный reload (MapScreen) — DEM-кэш/слои обновляются мгновенно.
- Данные «нет» = честно «нет данных», не «всё безопасно».
- DEM-имена — SW-угол с паддингом: `N47E041` = [47..48]N×[41..42]E (SRTM/Copernicus); `DemIndex.tileOf/tileBounds/HgtReader` держать согласованными (регресс-тест DemLookupTest).
- SyncEngine: baseline-манифест (`SyncEntry.remoteModifiedAt/remoteSize`) обязателен для решения «эхо или правка»; конфликт = правки обеих сторон с прошлого синка.

## Данные и сервисы
- NavData: `maps/ tracks/ dem/ photos/ voice/ backups/ import/ protected/ export/ xyz/ routing/segments4 profiles2`.
- Автоимпорт из `import/` на старте (gpx kml kmz tcx geojson json mbtiles sqlitedb gpkg hgt tif tiff), исключение `autoscan.json`.
- Источники: Copernicus GLO-30/90 (S3, без токена), Overpass API (overpass-api.de), Open-Meteo (текущая+24 ч). Никаких выдуманных URL.
- Версия 0.5.0: см. «Версионирование» и `AppInfo.kt`. Desktop `packageVersion` синхронизирован с `AppInfo`.

## Известные ограничения
- Векторные .mbtiles — отказ; броды только из OSM-роутинга; SMB нет; PIN-шифрование бэкапов нет.
- Хиллшейд: первый рендер тайла ~100 мс (кэш далее).
- LAN-синк: канал не шифрован (токен 12 симв., path-guard по canonical containment) — для домашней сети.
- Overpass: relations → только внешние контуры; 429/504 → «сервер перегружен».
- desktopTest требует GRADLE_USER_HOME-обход (см. выше).
- Окно маршрута/контекст-меню вынесены из `MapScreen.kt` (2168 строк) в `MapRouteDialog.kt`/`MapContextMenu.kt`; дальнейшие кандидаты — `AddPointDialog`, карточки/панели.

## Дорожная карта (обсуждено с заказчиком)
1. **P5 iOS** — только при наличии Mac; архитектура готова (expect/actual изолированы).
2. **Нижняя навигационная панель** — сделана в 0.3.4 (ширина < 600 dp; выпадающее меню карты сохранено).
3. Идеи на потом: кластеры POI на Android, поиск АЗС для трамплинов-топлива (нужен офлайн-источник данных).
4. Тесты: синк/DEM/ретеншн/ДП/EXIF/TileDb-битые/трек-сравнение/топливо покрыты (96 desktop-тестов, `desktopTest`); осталась Compose UI-инфраструктура (uiTest + правила навигации).
5. **Форматы координат (ввод/импорт точек)** — сейчас только десятичные градусы (DD). Нужны автоопределение и/или выбор формата при импорте и ручном вводе (файлы организаторов/геокэшинг, сотни точек): DDM (N 55° 12.345'), DMS, варианты разделителей/колонок CSV и т.п.; уточнить по реальному файлу.
6. **Подключение карт «как в AlpineQuest»** — уточнить сценарий (URL-шаблоны {s}/TMS {-y}/quadkey, WMS/WMTS, импорт каталогов/описаний провайдеров, пакетная загрузка области для пользовательских XYZ и др.); сейчас: XYZ {z}/{x}/{y} + растровый MBTiles (импорт .sqlitedb/.gpkg).
