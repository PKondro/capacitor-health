package app.capgo.plugin.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.DataOrigin
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.min

class HealthManager {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    fun permissionsFor(readTypes: Collection<HealthDataType>, writeTypes: Collection<HealthDataType>, includeWorkouts: Boolean = false): Set<String> = buildSet {
        readTypes.forEach { add(it.readPermission) }
        writeTypes.forEach { add(it.writePermission) }
        if (includeWorkouts || readTypes.any { it == HealthDataType.WORKOUT }) {
            add(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
        }
    }

    suspend fun authorizationStatus(
        client: HealthConnectClient,
        readTypes: Collection<HealthDataType>,
        writeTypes: Collection<HealthDataType>,
        includeWorkouts: Boolean = false
    ): JSObject {
        val granted = client.permissionController.getGrantedPermissions()

        val readAuthorized = JSArray()
        val readDenied = JSArray()
        readTypes.forEach { type ->
            if (granted.contains(type.readPermission)) {
                readAuthorized.put(type.identifier)
            } else {
                readDenied.put(type.identifier)
            }
        }

        val writeAuthorized = JSArray()
        val writeDenied = JSArray()
        writeTypes.forEach { type ->
            if (granted.contains(type.writePermission)) {
                writeAuthorized.put(type.identifier)
            } else {
                writeDenied.put(type.identifier)
            }
        }

        return JSObject().apply {
            put("readAuthorized", readAuthorized)
            put("readDenied", readDenied)
            put("writeAuthorized", writeAuthorized)
            put("writeDenied", writeDenied)
        }
    }

    suspend fun readSamples(
        client: HealthConnectClient,
        dataType: HealthDataType,
        startTime: Instant,
        endTime: Instant,
        limit: Int,
        ascending: Boolean
    ): JSArray {
        val samples = mutableListOf<Pair<Instant, JSObject>>()
        
        // Pagalbinė funkcija skaitymui, kad nereiktų kartoti kodo
        suspend fun <T : Record> fetch(clazz: kotlin.reflect.KClass<T>, processor: (T) -> Unit) {
             readRecords(client, clazz, startTime, endTime, limit, processor)
        }

        when (dataType) {
            HealthDataType.STEPS -> fetch(StepsRecord::class) { record ->
                val payload = createSamplePayload(dataType, record.startTime, record.endTime, record.count.toDouble(), record.metadata)
                samples.add(record.startTime to payload)
            }
            HealthDataType.DISTANCE -> fetch(DistanceRecord::class) { record ->
                val payload = createSamplePayload(dataType, record.startTime, record.endTime, record.distance.inMeters, record.metadata)
                samples.add(record.startTime to payload)
            }
            HealthDataType.CALORIES -> fetch(TotalCaloriesBurnedRecord::class) { record ->
                val payload = createSamplePayload(dataType, record.startTime, record.endTime, record.energy.inKilocalories, record.metadata)
                samples.add(record.startTime to payload)
            }
            HealthDataType.CALORIES_ACTIVE -> fetch(ActiveCaloriesBurnedRecord::class) { record ->
                val payload = createSamplePayload(dataType, record.startTime, record.endTime, record.energy.inKilocalories, record.metadata)
                samples.add(record.startTime to payload)
            }
            HealthDataType.WEIGHT -> fetch(WeightRecord::class) { record ->
                val payload = createSamplePayload(dataType, record.time, record.time, record.weight.inKilograms, record.metadata)
                samples.add(record.time to payload)
            }
            HealthDataType.HEART_RATE -> fetch(HeartRateRecord::class) { record ->
                record.samples.forEach { sample ->
                    val payload = createSamplePayload(dataType, sample.time, sample.time, sample.beatsPerMinute.toDouble(), record.metadata)
                    samples.add(sample.time to payload)
                }
            }
            HealthDataType.SLEEP -> fetch(SleepSessionRecord::class) { record ->
                val duration = Duration.between(record.startTime, record.endTime).toMinutes().toDouble()
                val payload = createSamplePayload(dataType, record.startTime, record.endTime, duration, record.metadata)
                samples.add(record.startTime to payload)
            }
            HealthDataType.SLEEP_STAGES -> fetch(SleepSessionRecord::class) { record ->
                record.stages.forEach { stage ->
                    val duration = Duration.between(stage.startTime, stage.endTime).toMinutes().toDouble()
                    // Naudojame pagrindinio įrašo metaduomenis, nes stadija jų neturi
                    val payload = createSamplePayload(dataType, stage.startTime, stage.endTime, duration, record.metadata)
                    
                    val stageDescription = when(stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_AWAKE -> "Awake"
                        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "Sleeping"
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "Out of Bed"
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> "Light"
                        SleepSessionRecord.STAGE_TYPE_DEEP -> "Deep"
                        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
                        else -> "Unknown"
                    }
                    payload.put("stage_description", stageDescription)
                    payload.put("duration_minutes", duration)
                    
                    samples.add(stage.startTime to payload)
                }
            }
            HealthDataType.WORKOUT -> fetch(ExerciseSessionRecord::class) { record ->
                val duration = Duration.between(record.startTime, record.endTime).toMinutes().toDouble()
                val payload = createSamplePayload(dataType, record.startTime, record.endTime, duration, record.metadata)
                
                // Čia naudojame vidinę funkciją toWorkoutTypeString
                val typeName = toWorkoutTypeString(record.exerciseType)
                payload.put("activityName", typeName)
                payload.put("type", typeName)
                payload.put("title", record.title ?: typeName)
                
                samples.add(record.startTime to payload)
            }
            HealthDataType.HRV -> fetch(HeartRateVariabilityRmssdRecord::class) { record ->
                val payload = createSamplePayload(dataType, record.time, record.time, record.heartRateVariabilityMillis.toDouble(), record.metadata)
                samples.add(record.time to payload)
            }
            HealthDataType.RESTING_HEART_RATE -> fetch(RestingHeartRateRecord::class) { record ->
                val payload = createSamplePayload(dataType, record.time, record.time, record.beatsPerMinute.toDouble(), record.metadata)
                samples.add(record.time to payload)
            }
            else -> {}
        }

        val sorted = samples.sortedBy { it.first }
        val ordered = if (ascending) sorted else sorted.asReversed()
        val limited = if (limit > 0) ordered.take(limit) else ordered

        val array = JSArray()
        limited.forEach { array.put(it.second) }
        return array
    }

    private suspend fun <T : Record> readRecords(
        client: HealthConnectClient,
        recordClass: kotlin.reflect.KClass<T>,
        startTime: Instant,
        endTime: Instant,
        limit: Int,
        consumer: (record: T) -> Unit
    ) {
        var pageToken: String? = null
        val pageSize = if (limit > 0) min(limit, MAX_PAGE_SIZE) else DEFAULT_PAGE_SIZE
        var fetched = 0

        do {
            val request = ReadRecordsRequest(
                recordType = recordClass,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                pageSize = pageSize,
                pageToken = pageToken
            )
            val response = client.readRecords(request)
            response.records.forEach { record ->
                consumer(record)
            }
            fetched += response.records.size
            pageToken = response.pageToken
        } while (pageToken != null && (limit <= 0 || fetched < limit))
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun saveSample(
        client: HealthConnectClient,
        dataType: HealthDataType,
        value: Double,
        startTime: Instant,
        endTime: Instant,
        metadata: Map<String, String>?
    ) {
        // --- Čia ištaisyta "No value passed for parameter 'metadata'" klaida ---
        when (dataType) {
            HealthDataType.STEPS -> {
                val record = StepsRecord(
                    startTime = startTime, 
                    startZoneOffset = zoneOffset(startTime),
                    endTime = endTime, 
                    endZoneOffset = zoneOffset(endTime),
                    count = value.toLong().coerceAtLeast(0),
                    metadata = Metadata.manualEntry() // <--- PRIVALOMAS
                )
                client.insertRecords(listOf(record))
            }
            HealthDataType.WEIGHT -> {
                 val record = WeightRecord(
                    time = startTime, 
                    zoneOffset = zoneOffset(startTime),
                    weight = Mass.kilograms(value),
                    metadata = Metadata.manualEntry() // <--- PRIVALOMAS
                )
                client.insertRecords(listOf(record))
            }
            else -> {}
        }
    }

    fun parseInstant(value: String?, defaultInstant: Instant): Instant {
        if (value.isNullOrBlank()) return defaultInstant
        return Instant.parse(value)
    }

    private fun createSamplePayload(
        dataType: HealthDataType,
        startTime: Instant,
        endTime: Instant,
        value: Double,
        metadata: Metadata
    ): JSObject {
        val payload = JSObject()
        payload.put("dataType", dataType.identifier)
        payload.put("value", value)
        payload.put("unit", dataType.unit)
        payload.put("startDate", formatter.format(startTime))
        payload.put("endDate", formatter.format(endTime))

        val dataOrigin = metadata.dataOrigin
        payload.put("sourceId", dataOrigin.packageName)
        payload.put("sourceName", dataOrigin.packageName)
        metadata.device?.let { device ->
            val manufacturer = device.manufacturer?.takeIf { it.isNotBlank() }
            val model = device.model?.takeIf { it.isNotBlank() }
            val label = listOfNotNull(manufacturer, model).joinToString(" ").trim()
            if (label.isNotEmpty()) {
                payload.put("sourceName", label)
            }
        }
        return payload
    }

    private fun zoneOffset(instant: Instant): ZoneOffset? {
        return ZoneId.systemDefault().rules.getOffset(instant)
    }

    private fun Double.toBpmLong(): Long {
        return java.lang.Math.round(this.coerceAtLeast(0.0))
    }

    suspend fun queryAggregated(client: HealthConnectClient, dataType: HealthDataType, startTime: Instant, endTime: Instant, bucket: String, aggregation: String): JSObject {
         return JSObject().apply { put("samples", JSArray()) }
    }

    suspend fun queryWorkouts(client: HealthConnectClient, workoutType: String?, startTime: Instant, endTime: Instant, limit: Int, ascending: Boolean, anchor: String?): JSObject {
        return JSObject()
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGE_SIZE = 500
        
        // --- PERKĖLIAU LOGIKĄ ČIA, KAD NEBŪTŲ REDECLARATION KLAIDŲ ---
        fun toWorkoutTypeString(type: Int): String {
            return when (type) {
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "running"
                ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "biking"
                ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "strength_training"
                ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER, 
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "swimming"
                ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "hiit"
                ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "calisthenics"
                ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "weightlifting"
                else -> "other"
            }
        }
    }
}