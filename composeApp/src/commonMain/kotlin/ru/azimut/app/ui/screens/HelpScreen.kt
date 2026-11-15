package ru.azimut.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.azimut.app.ui.components.BackHeader
import ru.azimut.app.ui.tr

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("Справка", "Help"), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            HelpSection(
                tr("Установка и запуск", "Install and launch"),
                tr(
                    "Windows: запустите установщик (setup.exe или .msi) либо распакуйте portable-архив. " +
                        "Нужен Windows 10 (1903) и выше. Данные — в папке «Документы\\Azimut\\NavData».\n" +
                        "Android: установите APK, нужен Android 8.0 и выше. При первом запуске выдайте " +
                        "разрешения на геолокацию, фоновую геолокацию и уведомления. Данные — в папке " +
                        "Android/data/ru.azimut.app/files/NavData.\n" +
                        "Без разрешений карта откроется, но текущая позиция и запись работать не будут.",
                    "Windows: run the setup.exe or .msi installer, or unpack the portable zip. " +
                        "Windows 10 (1903) or newer is required. Data is stored in Documents\\Azimut\\NavData.\n" +
                        "Android: install the APK; Android 8.0 or newer is required. On first launch grant " +
                        "location, background location and notification permissions. Data is stored in " +
                        "Android/data/ru.azimut.app/files/NavData.\n" +
                        "Without permissions the map opens but the current position and recording will not work.",
                ),
            )
            HelpSection(
                tr("Карта и управление", "Map and controls"),
                tr(
                    "Правая колонка: запись, к текущей позиции, точка, маршрут, слои, база, " +
                        "режим Сусанина, меню, ориентация, «Ещё/Свернуть».\n" +
                        "Windows: карту двигают левой кнопкой, масштаб — колесом или двойным кликом, " +
                        "правая кнопка открывает меню, слева есть кнопки «+» и «−».\n" +
                        "Android: карту двигают пальцем, масштаб — щипком, поворот — двумя пальцами. " +
                        "Одиночный тап добавляет точку, долгое нажатие открывает меню. " +
                        "Кнопка ориентации переключает «север / по курсу».\n" +
                        "Нижняя панель показывает координаты, скорость и точность; нажмите на неё, " +
                        "чтобы увидеть пробег, число точек и расстояния до базы и точки сбора.",
                    "Right column: record, back to location, waypoint, route, layers, base, " +
                        "Explorer Mode, menu, orientation, More/Collapse.\n" +
                        "Windows: drag with the left button, zoom with the wheel or double click, " +
                        "right-click opens the menu, and +/− buttons are on the left.\n" +
                        "Android: drag with a finger, pinch to zoom, rotate with two fingers. " +
                        "A single tap adds a waypoint, a long press opens the menu. " +
                        "The orientation button switches north up / course up.\n" +
                        "The bottom panel shows coordinates, speed and accuracy; tap it to see distance, " +
                        "point count and the distance to base and rally point.",
                ),
            )
            HelpSection(
                tr("Запись трека", "Track recording"),
                tr(
                    "Зелёная кнопка начинает запись, красная останавливает и сохраняет трек в NavData/tracks. " +
                        "Точки с точностью хуже 30 м в трек не пишутся. Автосохранение — каждые 5 точек; " +
                        "после аварийного закрытия приложение предложит продолжить или сохранить запись.\n" +
                        "В «Настройки → Запись и треки» задайте интервал 1/3/10 с и экономию заряда " +
                        "(не чаще одной точки в 5 с). «Фоновая запись» включена: Android продолжает запись " +
                        "при заблокированном экране, Windows прячет окно в трей. Выключена: блокировка экрана " +
                        "или закрытие окна останавливает и сохраняет запись.\n" +
                        "Windows: старт и стоп — Ctrl+R.",
                    "The green button starts recording; the red one stops and saves the track to NavData/tracks. " +
                        "Fixes worse than 30 m are not written to the track. Autosave runs every 5 points; " +
                        "after a crash the app offers to resume or save the recording.\n" +
                        "In Settings → Recording & tracks set the interval (1/3/10 s) and battery saver " +
                        "(at most one point every 5 s). Background recording on: Android keeps recording with " +
                        "the screen locked, Windows hides to the tray. Off: locking the screen or closing the " +
                        "window stops and saves the recording.\n" +
                        "Android widget: Record track starts recording with saved settings; Where am I opens " +
                        "the map and centers it on the current position.\n" +
                        "Windows: start and stop with Ctrl+R.",
                ),
            )
            HelpSection(
                tr("Точки, POI, фото и голос", "Waypoints, POI, photos and voice"),
                tr(
                    "Кнопка «Точка» сохраняет точку в текущей позиции. Android: тап по карте открывает " +
                        "диалог новой точки. Windows: правая кнопка по карте → «Добавить точку/POI». " +
                        "В «Меню → Точки» можно добавить точку вручную по координатам, выбрать тип " +
                        "(вода, приют, кемпинг, еда, топливо, медицина, достопримечательность, транспорт, " +
                        "мои точки, прочее), описание и фото.\n" +
                        "Android: фото снимается камерой или выбирается из галереи. " +
                        "Windows: камеры нет, фото выбираются из файлов. Фото хранятся в NavData/photos.\n" +
                        "Голосовые заметки: кнопка «Голосовая заметка» в диалоге, микрофон в списке точек; " +
                        "запись WAV хранится в NavData/voice и воспроизводится кнопкой с номером. " +
                        "На Android нужно разрешение на микрофон. Блок можно отключить в настройках.\n" +
                        "«Слои → Типы POI» фильтрует отображение точек на карте.",
                    "The Waypoint button saves a point at the current position. Android: a map tap opens " +
                        "the new waypoint dialog. Windows: right-click the map → Add waypoint/POI. " +
                        "In Menu → Waypoints you can add a point manually by coordinates, choose a type " +
                        "(water, shelter, camp, food, fuel, medical, sight, transport, my points, other), " +
                        "description and photos.\n" +
                        "Android: photos come from the camera or gallery. " +
                        "Windows: no camera; photos are selected from files. Photos are stored in NavData/photos.\n" +
                        "Voice notes: the Voice note button in the dialog, or the microphone in the waypoint " +
                        "list; WAV files are stored in NavData/voice and played by the numbered button. " +
                        "Android asks for microphone permission. The block can be disabled in settings.\n" +
                        "Layers → POI types filters waypoints on the map.",
                ),
            )
            HelpSection(
                tr("Маршруты и ведение", "Routes and navigation"),
                tr(
                    "Маршрут строится офлайн по данным BRouter. В диалоге задайте «Откуда» и «Куда» " +
                        "(«Моя позиция» или «На карте»), профиль транспорта и при необходимости " +
                        "«Избегать троп». Промежуточные точки: правый клик (Windows) или долгое нажатие " +
                        "(Android) по карте → «Через эту точку».\n" +
                        "Если для района нет данных, нажмите «Скачать данные для роутинга» — файлы " +
                        "загрузятся с brouter.de, и маршрут построится сразу.\n" +
                        "Тропы показаны оранжевым пунктиром и помечены «ТРОПА (не дорога)»: возможны " +
                        "сложные участки и запрет для мото. В предупреждении также указываются броды и " +
                        "запреты для мото по OSM.\n" +
                        "Результат: расстояние, время, набор и сброс высоты, максимальный уклон. " +
                        "Кнопки «Сохранить / GPX» и «Вести по маршруту». При ведении уход с линии более " +
                        "чем на 50 м даёт сигнал; возврат ближе 30 м снимает тревогу.\n" +
                        "Windows: «Карты вдоль маршрута» скачивают коридор шириной 1 км вдоль линии.",
                    "Routes are built offline from BRouter data. In the dialog set From and To " +
                        "(My location or On map), the transport profile and optionally Avoid trails. " +
                        "Via points: right-click (Windows) or long press (Android) on the map → Via this point.\n" +
                        "If the area has no routing data, press Download routing data; files come from " +
                        "brouter.de and the route is built automatically afterwards.\n" +
                        "Trails are drawn as an orange dashed line and labelled TRAIL (not a road): " +
                        "expect technical sections and possible bans on motor vehicles. Warnings also list " +
                        "fords and OSM motor-vehicle restrictions.\n" +
                        "The result shows distance, time, ascent, descent and maximum grade. " +
                        "Use Save / GPX and Navigate. While following, leaving the line by more than 50 m " +
                        "raises an alarm; returning within 30 m clears it.\n" +
                        "Windows: Maps along route downloads a 1 km corridor along the line.",
                ),
            )
            HelpSection(
                tr("Карты и офлайн", "Maps and offline"),
                tr(
                    "Кнопка «Слои» переключает спутник, базовую карту (Esri Street, Esri Topo, OSM, " +
                        "OSM Humanitarian, CyclOSM, OpenTopoMap) и прозрачные наложения. " +
                        "Прозрачность настраивается в «Настройки → Карты и слои». Просмотренные тайлы " +
                        "кэшируются автоматически.\n" +
                        "Android: кнопка «Скачать видимую область» сохраняет текущий слой в диапазоне " +
                        "текущего масштаба и на 2 уровня ближе. Прогресс виден в сообщении.\n" +
                        "Windows: кнопка загрузки в левом верхнем углу — прямоугольник, произвольная " +
                        "область или видимый район (карта + спутник). После загрузки район можно сохранить " +
                        "в .mbtiles в NavData/export и перенести на телефон.\n" +
                        "Импорт карты: «Настройки → Карты и слои → Импорт карты (.mbtiles)». " +
                        "Векторные .mbtiles пока не поддерживаются; для OSM массовая загрузка отключена.",
                    "The Layers button switches satellite, base map (Esri Street, Esri Topo, OSM, " +
                        "OSM Humanitarian, CyclOSM, OpenTopoMap) and transparent overlays. " +
                        "Opacity is set in Settings → Maps & layers. Viewed tiles are cached automatically.\n" +
                        "Android: Download visible area saves the current layer from the current zoom " +
                        "and two levels closer. Progress is shown as a message.\n" +
                        "Windows: the download button in the top-left corner offers a rectangle, " +
                        "a free shape or the visible area (map + satellite). After downloading, the area can " +
                        "be saved as .mbtiles to NavData/export and copied to a phone.\n" +
                        "Import a map via Settings → Maps & layers → Import map (.mbtiles). " +
                        "Vector .mbtiles are not supported yet; bulk OSM download is disabled.",
                ),
            )
            HelpSection(
                tr("База, точка сбора и журнал", "Base, rally point and journal"),
                tr(
                    "Кнопка «База» сохраняет текущую позицию как базу. Точку сбора (Rally) можно " +
                        "сохранить в «Настройки → Точки и фото». Расстояния до базы и до сбора показаны " +
                        "в развёрнутой нижней панели.\n" +
                        "«Меню → Журнал поездок»: показать трек на карте, вести по нему, экспортировать GPX, " +
                        "показать/скрыть как фоновую линию, переименовать или удалить. " +
                        "Построенные маршруты помечены «План», треки Сусанина — «Сусанин».",
                    "The Base button saves the current position as the base. The rally point is saved in " +
                        "Settings → Waypoints & media. Distances to base and rally point appear in the " +
                        "expanded bottom panel.\n" +
                        "Menu → Trip journal: show a track on the map, navigate it, export GPX, " +
                        "show/hide it as a background line, rename or delete. Built routes are marked Plan, " +
                        "Explorer tracks are marked Explorer.",
                ),
            )
            HelpSection(
                tr("GPS и точность", "GPS and accuracy"),
                tr(
                    "Индикатор спутников показывает число спутников в решении; точность в метрах видна " +
                        "в нижней панели. Точность хуже 30 м подсвечивается красным.\n" +
                        "Android: используется встроенный GPS/GNSS, компас — если он есть в телефоне.\n" +
                        "Windows: встроенного GPS обычно нет. В «Настройки → Запись и треки → GPS-приёмник» " +
                        "выберите симулятор для теста или COM-порт внешнего NMEA-приёмника и скорость порта. " +
                        "Магнитного компаса нет, ориентация по курсу работает только при движении.\n" +
                        "В лесу и ущельях точность падает — ориентируйтесь по треку и компасу, " +
                        "а не только по точке на карте.",
                    "The satellite badge shows satellites used in the fix; accuracy in metres is in the " +
                        "bottom panel. Worse than 30 m is highlighted in red.\n" +
                        "Android: the built-in GPS/GNSS is used, plus the compass when the phone has one.\n" +
                        "Windows: PCs rarely have GPS. In Settings → Recording & tracks → GPS receiver " +
                        "choose the simulator for testing or the COM port of an external NMEA receiver and " +
                        "its baud rate. There is no magnetometer, so course-up works only while moving.\n" +
                        "In forests and canyons accuracy drops — rely on the track and compass, " +
                        "not just the dot on the map.",
                ),
            )
            HelpSection(
                tr("Режим Сусанина", "Explorer Mode"),
                tr(
                    "Режим включается кнопкой с восклицательным знаком. Он выбирает более свободный " +
                        "профиль роутинга, помечает трек и GPX флагом susanin и увеличивает счётчик " +
                        "«был Сусанином». Кнопка «Выйти отсюда» строит безопасный офлайн-маршрут к базе, " +
                        "точке сбора или последней точке трека.\n" +
                        "Предупреждения о тропах, бродах и запретах для мото появляются при построении " +
                        "маршрута. Данные карт и DEM могут содержать ошибки; решение о маршруте " +
                        "принимает пользователь.\n\n" +
                        "В английской версии — Explorer Mode: ироничная отсылка к Ивану Сусанину, " +
                        "который увёл врагов в непроходимую чащу, но сам погиб. Мы уводим вас в чащу, " +
                        "но всегда возвращаем обратно.",
                    "Enable it with the warning button. It picks a more permissive routing profile, " +
                        "marks the track and GPX with susanin=true and increases the Explorer counter. " +
                        "Get me out builds a safe offline route to base, rally point or the last track point.\n" +
                        "Trail, ford and motor-vehicle restriction warnings appear when a route is built. " +
                        "Map and DEM data may contain errors; the route decision is the user's.\n\n" +
                        "The Russian name refers to Ivan Susanin, who led enemies into a swamp " +
                        "and perished. We lead you into the wild, but always bring you back.",
                ),
            )
            HelpSection(
                tr("Погода и световой день", "Weather and daylight"),
                tr(
                    "«Настройки → Погода и световой день» показывает восход, закат и продолжительность " +
                        "дня на текущую дату для последней GPS-позиции (или базы). На полярных широтах " +
                        "вместо времени выводится полярный день или полярная ночь.\n" +
                        "Погода загружается по кнопке «Обновить» с open-meteo.com: температура, ветер и " +
                        "явление. Без сети показывается последний кэш. Переключатель «Учитывать рельеф» " +
                        "пока не меняет расчёт: коррекция по DEM-горизонту не подключена.",
                    "Settings → Weather and daylight shows sunrise, sunset and day length for today at " +
                        "the last GPS position (or base). At polar latitudes it shows polar day or polar " +
                        "night instead of times.\n" +
                        "Weather is downloaded with the Refresh button from open-meteo.com: temperature, " +
                        "wind and conditions. Without network the last cache is shown. The terrain switch " +
                        "does not change the calculation yet: DEM horizon correction is not connected.",
                ),
            )
            HelpSection(
                tr("Данные и приватность", "Data and privacy"),
                tr(
                    "Все данные хранятся локально в NavData/: maps, tracks, dem, photos, voice, routing, export. " +
                        "Нет телеметрии, аналитики и аккаунтов. Сеть нужна для загрузки карт и сегментов " +
                        "BRouter и — только по кнопке «Обновить» — для запроса погоды на open-meteo.com.\n" +
                        "«Настройки → Данные»: «Создать резервную копию» складывает zip в NavData/backups; " +
                        "«Восстановить из копии» сначала сохраняет текущее состояние, затем заменяет данные " +
                        "архивом (запись трека должна быть остановлена).\n" +
                        "Автоимпорт: включите «Карты и слои → Автосканирование файлов» и положите .gpx/.mbtiles " +
                        "в NavData/import — они импортируются при следующем запуске.\n" +
                        "Перенос между устройствами — вручную, копированием NavData или zip-копии по USB, " +
                        "локальному Wi-Fi или через облако.\n" +
                        "Windows: Документы\\Azimut\\NavData. Android: " +
                        "Android/data/ru.azimut.app/files/NavData.",
                    "All data is stored locally in NavData/: maps, tracks, dem, photos, voice, routing, export. " +
                        "No telemetry, analytics or accounts. The network is used to download maps and " +
                        "BRouter segments and — only via the Refresh button — to request weather from " +
                        "open-meteo.com.\n" +
                        "Settings → Data: Create backup writes a zip to NavData/backups; Restore from backup " +
                        "saves the current state first, then replaces data with the archive (stop recording " +
                        "before restoring).\n" +
                        "Auto-import: enable Maps & layers → File auto-scan and put .gpx/.mbtiles into " +
                        "NavData/import; they are imported on the next launch.\n" +
                        "Move data between devices manually by copying NavData or the zip backup via USB, " +
                        "local Wi-Fi or cloud storage.\n" +
                        "Windows: Documents\\Azimut\\NavData. Android: " +
                        "Android/data/ru.azimut.app/files/NavData.",
                ),
            )
            HelpSection(
                tr("Рельеф и DEM", "Terrain and DEM"),
                tr(
                    "Файлы .hgt (SRTM) и GeoTIFF .tif кладутся в NavData/dem или импортируются " +
                        "в Настройках. «Скачать DEM вокруг меня» берёт Copernicus GLO-30/90 " +
                        "(открытый источник, без регистрации) — 3×3 тайла по 1°.\n" +
                        "С DEM считаются высоты, набор/сброс, максимальный уклон и профиль в «Проверке» " +
                        "трека; световой день корректируется по линии горизонта (переключатель " +
                        "«Учитывать рельеф» в Погоде).\n" +
                        "Если DEM для района нет — приложение честно пишет «нет DEM» и считает без рельефа.",
                    "Put .hgt (SRTM) or GeoTIFF .tif files into NavData/dem or import them in Settings. " +
                        "“Download DEM around me” uses Copernicus GLO-30/90 (open, no registration) — " +
                        "3×3 one-degree tiles.\n" +
                        "With a DEM the app computes heights, ascend/descend, max grade and the profile " +
                        "in the track “Precheck”; daylight is corrected by the horizon line (the " +
                        "“Account for terrain” switch in Weather).\n" +
                        "Without a DEM for the area the app honestly says “no DEM” and computes without terrain.",
                ),
            )
            HelpSection(
                tr("ООПТ и зоны", "Protected areas"),
                tr(
                    "Импортируйте GeoJSON с полигонами (FeatureCollection, Polygon/MultiPolygon) — " +
                        "слои появятся на карте красной заливкой. Свойства name/protection/access " +
                        "показываются при въезде; предупреждение — один раз на зону за поездку.\n" +
                        "Слой отражает только загруженные файлы и может быть неполным: это не " +
                        "официальный статус земли. Зона не называется «запретом», если в данных " +
                        "нет access=private/no.",
                    "Import GeoJSON with polygons (FeatureCollection, Polygon/MultiPolygon) — layers " +
                        "appear as red fills on the map. name/protection/access properties are shown " +
                        "on entry; the warning fires once per zone per trip.\n" +
                        "The layer reflects the loaded files only and may be incomplete: it is not the " +
                        "official land status. A zone is not called “restricted” unless the data has " +
                        "access=private/no.",
                ),
            )
            HelpSection(
                tr("Импорт/экспорт", "Import/export"),
                tr(
                    "Треки: GPX, KML, KMZ, TCX, GeoJSON — импорт кнопкой в Настройках и автоимпортом " +
                        "из NavData/import. Точки: GPX, KML, KMZ, CSV, GeoJSON — импорт и экспорт " +
                        "кнопками в «Точках» (CSV: lat,lon,name,description,category).\n" +
                        "Карты: растровые .mbtiles; .sqlitedb и .gpkg конвертируются в .mbtiles при " +
                        "импорте (векторные базы не поддерживаются). XYZ-источники добавляются URL-шаблоном " +
                        "{z}/{x}/{y}; лицензия источника — ваша ответственность.\n" +
                        "Журнал: «Проверка» трека перед выездом, экспорт листа PNG/PDF со схемой, " +
                        "профилем высот и предупреждениями.",
                    "Tracks: GPX, KML, KMZ, TCX, GeoJSON — import in Settings and auto-import from " +
                        "NavData/import. Waypoints: GPX, KML, KMZ, CSV, GeoJSON — import and export " +
                        "buttons in Waypoints (CSV: lat,lon,name,description,category).\n" +
                        "Maps: raster .mbtiles; .sqlitedb and .gpkg are converted to .mbtiles on import " +
                        "(vector databases are not supported). XYZ sources are added via a {z}/{x}/{y} " +
                        "URL template; source licensing is your responsibility.\n" +
                        "Journal: track “Precheck” before the ride, PNG/PDF sheet export with the sketch, " +
                        "elevation profile and warnings.",
                ),
            )
            HelpSection(
                tr("Координаты и синхронизация", "Coordinates and sync"),
                tr(
                    "Координаты точки: в диалоге создания точки показываются оба формата " +
                        "(десятичные и ГГ°ММ'СС), кнопки «Копировать» и «Поделиться» (системный " +
                        "шеринг на телефоне). В контекст-меню точки — «Копировать координаты». " +
                        "Направление и расстояние до базы/сбора — на SOS-панели нет: используйте " +
                        "«Компас: к этой точке».\n" +
                        "Синхронизация (Настройки → Синхронизация NavData): локальная папка/USB или " +
                        "WebDAV. Перед запуском создаётся zip-бэкап; файлы не удаляются молча — " +
                        "конфликты получают копию с суффиксом. Пароль WebDAV хранится в защищённом " +
                        "хранилище платформы (на Windows — зашифрованным локальным ключом). " +
                        "SMB не поддерживается: потребовала бы тяжёлую нативную зависимость.",
                    "SOS — a button in the map menu: large coordinates, altitude, time, bearing and " +
                        "distance to base/rally, copy and SMS. No automatic calls; everything works offline.\n" +
                        "Sync (Settings → NavData sync): a local/USB folder or WebDAV. A zip backup is " +
                        "created first; files are never silently deleted — conflicts get a suffixed copy. " +
                        "The WebDAV password lives in the platform secure store (on Windows encrypted " +
                        "with a local key file). SMB is not supported: it would require a heavy native " +
                        "dependency.",
                ),
            )
            HelpSection(
                tr("Ограничения версии ${ru.azimut.app.AppInfo.VERSION}", "${ru.azimut.app.AppInfo.VERSION} limitations"),
                tr(
                    "Векторные .mbtiles не поддерживаются (растровые — да). Броды считаются только " +
                        "по OSM-данным маршрутизатора; по DEM броды не определяются. Плановое время " +
                        "в трек-сравнении есть только у планов, сохранённых в 0.4.0 и новее. " +
                        "Расчёт топлива ведётся от полного бака по заданному расходу; заправки (АЗС) " +
                        "не ищутся. Предзагрузка DEM нуждается в сети; ручной импорт работает полностью офлайн. " +
                        "Полная инструкция — INSTRUKCIYA.md рядом с приложением.",
                    "Vector .mbtiles are not supported (raster ones are). Fords are known only from " +
                        "the router's OSM data; DEM alone cannot detect them. Planned time in track " +
                        "comparison exists only for plans saved in 0.4.0 or newer. Fuel is computed " +
                        "for a full tank by the given consumption; gas stations are not searched. " +
                        "DEM pre-download needs network; manual import is fully offline. " +
                        "The full manual is in INSTRUKCIYA.md next to the app.",
                ),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(18.dp))
}

