package io.openftba.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/** Supported UI languages. EN + RU. */
enum class Language(val code: String, val displayName: String) {
    EN("en", "English"),
    RU("ru", "Русский");

    companion object {
        fun fromCode(code: String?): Language =
            entries.firstOrNull { it.code == code?.lowercase()?.take(2) } ?: EN
    }
}

/**
 * Type-safe string catalog. Adding a key forces every language to define it, so
 * translations can't silently drift. Keep keys grouped by screen.
 */
interface Strings {
    val appName: String

    // Navigation
    val navOverview: String
    val navRides: String
    val navSettings: String

    // Common units / labels
    val distance: String
    val duration: String
    val movingTime: String
    val elapsedTime: String
    val avgSpeed: String
    val maxSpeed: String
    val avgCadence: String
    val maxCadence: String
    val avgHeartRate: String
    val maxHeartRate: String
    val avgPower: String
    val maxPower: String
    val elevationGain: String
    val elevationLoss: String
    val longestNonStop: String
    val biggestClimb: String

    // Overview
    val totalRides: String
    val totalDistance: String
    val totalTime: String
    val totalElevation: String
    val avgRideDistance: String
    val records: String
    val noData: String
    val noRidesTitle: String
    val noRidesHint: String

    // Ride detail
    val rideSummary: String
    val splits: String
    val axisTime: String
    val axisDistance: String
    val chartSpeed: String
    val chartElevation: String
    val chartHeartRate: String
    val chartCadence: String
    val chartPower: String
    val track3d: String
    val track3dHint: String

    // Settings
    val settingsWatchFolder: String
    val settingsWatchFolderHint: String
    val settingsServerManaged: String
    val settingsDemFolder: String
    val settingsDemFolderHint: String
    val settingsIgnoreElevation: String
    val settingsUseDem: String
    val settingsDownloadDem: String
    val downloading: String
    val settingsSensors: String
    val settingsSensorsHint: String
    val settingsUnits: String
    val settingsUnitsMetric: String
    val settingsUnitsImperial: String
    val settingsLanguage: String
    val settingsProfile: String
    val settingsWeight: String
    val settingsMaxHr: String
    val settingsFtp: String
    val rescan: String

    // Intensity & tiers (Wave 1)
    val intensity: String
    val effort: String
    val athleteLevel: String
    val basisPower: String
    val basisSpeed: String
    val tierRecovery: String
    val tierEndurance: String
    val tierTempo: String
    val tierRace: String
    val tierThresholdBurn: String
    val loadCurve: String
    val fitness: String
    val fatigue: String
    val form: String
    val tierDist: String
    val distHist: String
    val seasonality: String
    val share: String
    val load: String
    val unitKmh: String
    val unitM: String
    val unitBpm: String
    val unitRpm: String
    val unitW: String
    val unitKm: String
    val unitMin: String
    val segmentBreak: String
    val pauseStop: String
    val infoSourceLabel: String
    val infoFormulaLabel: String
    val infoScaleLabel: String
    val srcGps: String
    val srcDevice: String
    val srcDem: String
    val srcCalc: String
    val srcApprox: String
    val infoLearnMore: String
    val tsbFresh: String
    val tsbNeutral: String
    val tsbProductive: String
    val tsbHigh: String
    /** One-line explanation per metric key (see MetricCatalog). */
    fun metricDesc(key: String): String

    fun intensityTier(key: String?): String = when (key) {
        "recovery" -> tierRecovery
        "endurance" -> tierEndurance
        "tempo" -> tierTempo
        "race" -> tierRace
        "threshold_burn" -> tierThresholdBurn
        else -> "—"
    }
}

val LocalStrings = staticCompositionLocalOf<Strings> { EnStrings }

fun stringsFor(language: Language): Strings = when (language) {
    Language.EN -> EnStrings
    Language.RU -> RuStrings
}

object EnStrings : Strings {
    override val appName = "OpenFTBA"
    override val navOverview = "Overview"
    override val navRides = "Rides"
    override val navSettings = "Settings"
    override val distance = "Distance"
    override val duration = "Duration"
    override val movingTime = "Moving time"
    override val elapsedTime = "Elapsed time"
    override val avgSpeed = "Avg speed"
    override val maxSpeed = "Max speed"
    override val avgCadence = "Avg cadence"
    override val maxCadence = "Max cadence"
    override val avgHeartRate = "Avg heart rate"
    override val maxHeartRate = "Max heart rate"
    override val avgPower = "Avg power"
    override val maxPower = "Max power"
    override val elevationGain = "Elevation gain"
    override val elevationLoss = "Elevation loss"
    override val longestNonStop = "Longest non-stop"
    override val biggestClimb = "Biggest climb"
    override val totalRides = "Rides"
    override val totalDistance = "Total distance"
    override val totalTime = "Total time"
    override val totalElevation = "Total ascent"
    override val avgRideDistance = "Avg ride"
    override val records = "Records"
    override val noData = "No data"
    override val noRidesTitle = "No rides yet"
    override val noRidesHint = "Point the watch folder at your OpenTracks export folder in Settings."
    override val rideSummary = "Summary"
    override val splits = "Splits"
    override val axisTime = "Time"
    override val axisDistance = "Distance"
    override val chartSpeed = "Speed"
    override val chartElevation = "Elevation"
    override val chartHeartRate = "Heart rate"
    override val chartCadence = "Cadence"
    override val chartPower = "Power"
    override val track3d = "3D track"
    override val track3dHint = "drag to rotate · scroll or pinch to zoom"
    override val settingsWatchFolder = "OpenTracks export folder"
    override val settingsWatchFolderHint = "Folder where OpenTracks auto-exports tracks (.kmz)."
    override val settingsServerManaged = "Configured on the server (read-only here)."
    override val settingsDemFolder = "Elevation (DEM) folder"
    override val settingsDemFolderHint = "Optional SRTM tiles for offline elevation correction."
    override val settingsIgnoreElevation = "Ignore GPS elevation"
    override val settingsUseDem = "Use DEM elevation (offline)"
    override val settingsDownloadDem = "Download DEM tiles for my rides"
    override val downloading = "Downloading…"
    override val settingsSensors = "Trusted sensors"
    override val settingsSensorsHint = "Turn off a sensor you don't trust — it's ignored even if present in the track."
    override val settingsUnits = "Units"
    override val settingsUnitsMetric = "Metric (km, m)"
    override val settingsUnitsImperial = "Imperial (mi, ft)"
    override val settingsLanguage = "Language"
    override val settingsProfile = "Athlete profile"
    override val settingsWeight = "Weight (kg)"
    override val settingsMaxHr = "Max HR (bpm)"
    override val settingsFtp = "FTP (W)"
    override val rescan = "Rescan folder"
    override val intensity = "Intensity"
    override val effort = "Effort"
    override val athleteLevel = "Athlete level"
    override val basisPower = "by W/kg (FTP)"
    override val basisSpeed = "speed estimate"
    override val tierRecovery = "Recovery"
    override val tierEndurance = "Endurance"
    override val tierTempo = "Tempo"
    override val tierRace = "Race"
    override val tierThresholdBurn = "Threshold Burn"
    override val loadCurve = "Form · Fitness / Fatigue / Form"
    override val fitness = "Fitness (CTL)"
    override val fatigue = "Fatigue (ATL)"
    override val form = "Form (TSB)"
    override val tierDist = "Intensity distribution"
    override val distHist = "Distance distribution"
    override val seasonality = "By month"
    override val share = "Share"
    override val load = "Load"
    override val unitKmh = "km/h"
    override val unitM = "m"
    override val unitBpm = "bpm"
    override val unitRpm = "rpm"
    override val unitW = "W"
    override val unitKm = "km"
    override val unitMin = "min"
    override val segmentBreak = "Segment break"
    override val pauseStop = "Pause"
    override val infoSourceLabel = "Source"
    override val infoFormulaLabel = "Formula"
    override val infoScaleLabel = "Scale"
    override val srcGps = "GPS"
    override val srcDevice = "Sensor"
    override val srcDem = "Elevation model"
    override val srcCalc = "Calculated"
    override val srcApprox = "Approximate"
    override val infoLearnMore = "Learn more (opens in browser)"
    override val tsbFresh = "Fresh / race-ready"
    override val tsbNeutral = "Balanced"
    override val tsbProductive = "Productive overload"
    override val tsbHigh = "High fatigue — recover"
    override fun metricDesc(key: String): String = when (key) {
        "distance" -> "Total path length, summed from GPS point to point along the route."
        "movingTime" -> "Time actually riding — intervals where speed stays at or above 3.6 km/h. Stops are excluded."
        "avgSpeed" -> "Distance divided by moving time (stopped time is excluded)."
        "maxSpeed" -> "Highest instantaneous speed. Read from the speed sensor when present, otherwise derived from GPS; implausible spikes above 108 km/h are capped."
        "elevationGain" -> "Total climbing. Ups and downs smaller than 2 m are ignored (hysteresis) so GPS noise doesn't inflate it."
        "longestNonStop" -> "Longest distance ridden between stops — a segment break, or standing still for 10 s or more, starts a new run."
        "biggestClimb" -> "Largest single continuous ascent within one recording segment."
        "avgHr" -> "Average heart rate over the ride."
        "maxHr" -> "Highest heart rate recorded."
        "avgCadence" -> "Average pedalling cadence (crank revolutions per minute)."
        "avgPower" -> "Average mechanical power at the pedals."
        "maxPower" -> "Highest power recorded."
        "effort" -> "Overall training load of the ride, TSS-like: a maximal one-hour effort scores about 100. Grows with both duration and intensity."
        "intensity" -> "How hard the ride was, as an Intensity Factor (IF = effort relative to your threshold). Computed from power (NP/FTP) if available, else heart rate (%HRR), else speed (approximate)."
        "athlete" -> "Your overall level on an S-F scale, anchored to Coggan FTP benchmarks. From power-to-weight (W/kg) when FTP and weight are set, otherwise a rougher speed proxy (best avg speed: S>=32, A>=28, B>=25, C>=22, D>=19, E>=15 km/h). Women are compared on a scale shifted up."
        "fitness" -> "Chronic Training Load (CTL): a 42-day exponentially-weighted average of daily effort. It tracks your fitness — it rises slowly with consistent training and fades during rest."
        "fatigue" -> "Acute Training Load (ATL): a 7-day exponentially-weighted average of daily effort. It tracks tiredness — it climbs fast after hard days and drops quickly with rest."
        "form" -> "Training Stress Balance (TSB) = fitness - fatigue. Positive means fresh and rested, negative means you are carrying fatigue. A rule of thumb is to stay slightly negative up to about +25."
        "loadCurve" -> "Your fitness (CTL), fatigue (ATL) and form (TSB) over time.\n\nHow to use it:\n- Form below -30: heavily fatigued. Ease off — add recovery or easy days and cut intensity.\n- Form -10 to -30: productive overload. Fitness is building; expect to feel tired.\n- Form -10 to +5: balanced — good for steady training.\n- Form +5 to +25: fresh and race-ready. Taper into this for a key event.\n- Keep fitness rising gradually and consistently; avoid sharp fatigue spikes.\nWhen fatigue rises faster than fitness, schedule rest before pushing on."
        "totalRides" -> "Number of rides imported."
        "totalDistance" -> "Sum of every ride's distance."
        "totalTime" -> "Sum of moving time across all rides."
        "totalElevation" -> "Sum of climbing across all rides."
        "avgRideDistance" -> "Mean distance per ride."
        "longestRide" -> "Single longest ride by total distance."
        "maxRideElevation" -> "Most climbing in a single ride."
        "bestAvgSpeed" -> "Highest average speed achieved in a ride."
        "chSpeed" -> "Instantaneous speed along the ride. From the speed sensor when present, otherwise derived from GPS."
        "chElevation" -> "Elevation profile. Corrected from a local elevation model (DEM) when available, otherwise smoothed GPS altitude."
        "chHr" -> "Heart rate over the course of the ride."
        "chCadence" -> "Pedalling cadence over the course of the ride."
        "chPower" -> "Power output over the course of the ride."
        "distHist" -> "How many rides fall into each 5 km distance bucket. Bar height is the number of rides; the dashed line is the average bar, and the tallest bucket is highlighted. Shows whether you tend to ride short or long."
        "recentDist" -> "Distance of each of your most recent rides, oldest to newest. The dashed line is the average; use it to spot trends and consistency."
        "splits" -> "The ride cut into equal segments (1 km by default). Each row shows that segment's time, average speed and — if recorded — heart rate. Compare splits to see pacing: steady numbers mean even effort; a fade near the end means you tired or hit climbs."
        else -> ""
    }
}

object RuStrings : Strings {
    override val appName = "OpenFTBA"
    override val navOverview = "Обзор"
    override val navRides = "Тренировки"
    override val navSettings = "Настройки"
    override val distance = "Дистанция"
    override val duration = "Длительность"
    override val movingTime = "Время в движении"
    override val elapsedTime = "Общее время"
    override val avgSpeed = "Ср. скорость"
    override val maxSpeed = "Макс. скорость"
    override val avgCadence = "Ср. каденс"
    override val maxCadence = "Макс. каденс"
    override val avgHeartRate = "Ср. пульс"
    override val maxHeartRate = "Макс. пульс"
    override val avgPower = "Ср. мощность"
    override val maxPower = "Макс. мощность"
    override val elevationGain = "Набор высоты"
    override val elevationLoss = "Сброс высоты"
    override val longestNonStop = "Макс. без пауз"
    override val biggestClimb = "Макс. подъём"
    override val totalRides = "Тренировок"
    override val totalDistance = "Всего дистанция"
    override val totalTime = "Всего времени"
    override val totalElevation = "Всего набор"
    override val avgRideDistance = "Ср. заезд"
    override val records = "Рекорды"
    override val noData = "Нет данных"
    override val noRidesTitle = "Пока нет тренировок"
    override val noRidesHint = "Укажите в настройках папку экспорта OpenTracks."
    override val rideSummary = "Сводка"
    override val splits = "Сплиты"
    override val axisTime = "Время"
    override val axisDistance = "Дистанция"
    override val chartSpeed = "Скорость"
    override val chartElevation = "Высота"
    override val chartHeartRate = "Пульс"
    override val chartCadence = "Каденс"
    override val chartPower = "Мощность"
    override val track3d = "3D-трек"
    override val track3dHint = "перетащите — вращение · колесо или щипок — зум"
    override val settingsWatchFolder = "Папка экспорта OpenTracks"
    override val settingsWatchFolderHint = "Папка, куда OpenTracks авто-экспортирует треки (.kmz)."
    override val settingsServerManaged = "Задаётся на сервере (здесь только для просмотра)."
    override val settingsDemFolder = "Папка высот (DEM)"
    override val settingsDemFolderHint = "Опц. SRTM-тайлы для оффлайн-коррекции высоты."
    override val settingsIgnoreElevation = "Игнорировать высоту по GPS"
    override val settingsUseDem = "Высота из DEM (оффлайн)"
    override val settingsDownloadDem = "Скачать DEM-тайлы для моих заездов"
    override val downloading = "Скачивание…"
    override val settingsSensors = "Доверенные датчики"
    override val settingsSensorsHint = "Отключите датчик, которому не доверяете — он игнорируется, даже если есть в треке."
    override val settingsUnits = "Единицы"
    override val settingsUnitsMetric = "Метрические (км, м)"
    override val settingsUnitsImperial = "Имперские (мили, футы)"
    override val settingsLanguage = "Язык"
    override val settingsProfile = "Профиль спортсмена"
    override val settingsWeight = "Вес (кг)"
    override val settingsMaxHr = "Макс. пульс (уд/мин)"
    override val settingsFtp = "FTP (Вт)"
    override val rescan = "Пересканировать"
    override val intensity = "Интенсивность"
    override val effort = "Усилие"
    override val athleteLevel = "Уровень атлета"
    override val basisPower = "по W/kg (FTP)"
    override val basisSpeed = "оценка по скорости"
    override val tierRecovery = "Прогулка"
    override val tierEndurance = "Выносливость"
    override val tierTempo = "Темп"
    override val tierRace = "Гонка"
    override val tierThresholdBurn = "Взрыв"
    override val loadCurve = "Форма · Fitness / Fatigue / Form"
    override val fitness = "Форма (CTL)"
    override val fatigue = "Усталость (ATL)"
    override val form = "Готовность (TSB)"
    override val tierDist = "Распределение интенсивности"
    override val distHist = "Распределение дистанций"
    override val seasonality = "По месяцам"
    override val share = "Поделиться"
    override val load = "Нагрузка"
    override val unitKmh = "км/ч"
    override val unitM = "м"
    override val unitBpm = "уд/м"
    override val unitRpm = "об/м"
    override val unitW = "Вт"
    override val unitKm = "км"
    override val unitMin = "мин"
    override val segmentBreak = "Разрыв сегмента"
    override val pauseStop = "Пауза"
    override val infoSourceLabel = "Источник"
    override val infoFormulaLabel = "Формула"
    override val infoScaleLabel = "Шкала"
    override val srcGps = "GPS"
    override val srcDevice = "Датчик"
    override val srcDem = "Модель высот"
    override val srcCalc = "Расчёт"
    override val srcApprox = "Оценка"
    override val infoLearnMore = "Подробнее (откроется в браузере)"
    override val tsbFresh = "Свежесть / готов к гонке"
    override val tsbNeutral = "Баланс"
    override val tsbProductive = "Продуктивная нагрузка"
    override val tsbHigh = "Сильная усталость — отдых"
    override fun metricDesc(key: String): String = when (key) {
        "distance" -> "Общая длина маршрута, просуммированная между GPS-точками вдоль трека."
        "movingTime" -> "Время в движении — интервалы, где скорость не ниже 3.6 км/ч. Остановки исключаются."
        "avgSpeed" -> "Дистанция, делённая на время в движении (без учёта остановок)."
        "maxSpeed" -> "Максимальная мгновенная скорость. Берётся с датчика скорости, иначе вычисляется по GPS; нереальные всплески выше 108 км/ч отсекаются."
        "elevationGain" -> "Суммарный набор высоты. Колебания меньше 2 м игнорируются (гистерезис), чтобы шум GPS не завышал цифру."
        "longestNonStop" -> "Самый длинный отрезок без остановок — разрыв сегмента или стояние от 10 с начинают новый отрезок."
        "biggestClimb" -> "Наибольший непрерывный подъём в пределах одного сегмента записи."
        "avgHr" -> "Средний пульс за тренировку."
        "maxHr" -> "Максимальный зафиксированный пульс."
        "avgCadence" -> "Средний каденс (оборотов шатунов в минуту)."
        "avgPower" -> "Средняя механическая мощность на педалях."
        "maxPower" -> "Максимальная зафиксированная мощность."
        "effort" -> "Общая нагрузка тренировки, по типу TSS: предельное часовое усилие около 100. Растёт и от длительности, и от интенсивности."
        "intensity" -> "Насколько тяжёлой была тренировка — фактор интенсивности (IF = усилие относительно вашего порога). Считается по мощности (NP/FTP), иначе по пульсу (%HRR), иначе по скорости (приблизительно)."
        "athlete" -> "Ваш общий уровень по шкале S-F, привязанной к эталонам Coggan FTP. По отношению мощности к весу (Вт/кг), если заданы FTP и вес; иначе по грубой оценке через скорость (лучшая ср. скорость: S>=32, A>=28, B>=25, C>=22, D>=19, E>=15 км/ч). Женщины сравниваются по смещённой вверх шкале."
        "fitness" -> "Хроническая нагрузка (CTL): экспоненциальное среднее усилия за 42 дня. Отражает форму — растёт медленно при регулярных тренировках и спадает на отдыхе."
        "fatigue" -> "Острая нагрузка (ATL): экспоненциальное среднее усилия за 7 дней. Отражает усталость — быстро растёт после тяжёлых дней и быстро падает на отдыхе."
        "form" -> "Баланс нагрузки (TSB) = форма - усталость (CTL - ATL). Плюс — свежесть и отдых, минус — накопленная усталость. Практическое правило: держаться от слегка отрицательного до примерно +25."
        "loadCurve" -> "Ваши форма (CTL), усталость (ATL) и готовность (TSB) во времени.\n\nКак применять:\n- Готовность ниже -30: сильная усталость. Сбавьте: добавьте отдых или лёгкие дни и снизьте интенсивность.\n- Готовность от -30 до -10: продуктивная нагрузка. Форма растёт; усталость — это нормально.\n- Готовность от -10 до +5: баланс, хорошо для равномерных тренировок.\n- Готовность от +5 до +25: свежесть, готовность к гонке. Подводитесь к этому перед ключевым стартом.\n- Наращивайте форму плавно и регулярно; избегайте резких скачков усталости.\nЕсли усталость растёт быстрее формы — запланируйте отдых, прежде чем продолжать."
        "totalRides" -> "Количество импортированных тренировок."
        "totalDistance" -> "Сумма дистанций всех тренировок."
        "totalTime" -> "Сумма времени в движении по всем тренировкам."
        "totalElevation" -> "Суммарный набор высоты по всем тренировкам."
        "avgRideDistance" -> "Средняя дистанция за тренировку."
        "longestRide" -> "Самая длинная тренировка по дистанции."
        "maxRideElevation" -> "Наибольший набор высоты за одну тренировку."
        "bestAvgSpeed" -> "Наивысшая средняя скорость за тренировку."
        "chSpeed" -> "Мгновенная скорость по ходу заезда. С датчика скорости, иначе вычисляется по GPS."
        "chElevation" -> "Профиль высоты. Корректируется по локальной модели высот (DEM), если она есть, иначе по сглаженной высоте GPS."
        "chHr" -> "Пульс на протяжении заезда."
        "chCadence" -> "Каденс на протяжении заезда."
        "chPower" -> "Мощность на протяжении заезда."
        "distHist" -> "Сколько тренировок попадает в каждый интервал по 5 км. Высота столбца — число заездов; пунктир — средний столбец, самый высокий выделен. Видно, тяготеете вы к коротким или длинным заездам."
        "recentDist" -> "Дистанция последних заездов, от старых к новым. Пунктир — среднее; помогает увидеть тренд и регулярность."
        "splits" -> "Заезд, разбитый на равные отрезки (по умолчанию 1 км). В строке — время отрезка, средняя скорость и, если есть, пульс. Сравнивая отрезки, видно раскладку: ровные цифры — равномерное усилие; провал к концу — усталость или подъёмы."
        else -> ""
    }
}
