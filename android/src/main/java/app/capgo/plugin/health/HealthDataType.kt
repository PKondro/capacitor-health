package app.capgo.plugin.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import kotlin.reflect.KClass

enum class HealthDataType(
    val identifier: String,
    val recordClass: KClass<out Record>,
    val unit: String
) {
    STEPS("steps", StepsRecord::class, "count"),
    DISTANCE("distance", DistanceRecord::class, "meter"),
    CALORIES("calories", TotalCaloriesBurnedRecord::class, "kilocalorie"),
    CALORIES_ACTIVE("calories_active", ActiveCaloriesBurnedRecord::class, "kilocalorie"),
    HEART_RATE("heartRate", HeartRateRecord::class, "bpm"),
    WEIGHT("weight", WeightRecord::class, "kilogram"),
    SLEEP("sleep", SleepSessionRecord::class, "minute"),
    // Svarbu: Sleep Stages naudoja SleepSessionRecord
    SLEEP_STAGES("sleep_stages", SleepSessionRecord::class, "stage"),
    WORKOUT("workout", ExerciseSessionRecord::class, "session"),
    RESPIRATORY_RATE("respiratoryRate", RespiratoryRateRecord::class, "bpm"),
    OXYGEN_SATURATION("oxygenSaturation", OxygenSaturationRecord::class, "percent"),
    RESTING_HEART_RATE("restingHeartRate", RestingHeartRateRecord::class, "bpm"),
    HRV("hrv", HeartRateVariabilityRmssdRecord::class, "millisecond"); 

    val readPermission: String
        get() = HealthPermission.getReadPermission(recordClass)

    val writePermission: String
        get() = HealthPermission.getWritePermission(recordClass)

    companion object {
        fun from(identifier: String): HealthDataType? {
            return entries.firstOrNull { it.identifier == identifier }
        }
    }
}