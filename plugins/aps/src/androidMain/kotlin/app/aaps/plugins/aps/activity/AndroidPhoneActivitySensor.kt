package app.aaps.plugins.aps.activity

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.aaps.core.interfaces.aps.PhoneActivitySensor
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.ApsStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.roundToLong

/**
 * The steps and the movement of the phone, for the autoISF activity monitor.
 *
 * It listens to the step counter and to the accelerometer of the phone. The step counter of Android
 * counts the steps since the last boot, so only the difference between two events is used, and it is
 * kept in blocks of 5 minutes. The accelerometer only says "the phone was moved", which means that
 * somebody carries it. Without that, no steps can mean "not carried" as well as "not active".
 *
 * The listeners start in [start], not when the class is built, so building the graph does not reach
 * the sensor service.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidPhoneActivitySensor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil
) : PhoneActivitySensor {

    private val lock = Any()
    private var previousStepCount = -1
    private val stepsMap = LinkedHashMap<Long, Int>()
    private var lastMovement = 0L
    private var started = false

    override var startTime: Long = 0L
        private set

    private val stepListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSensorChanged(sensorEvent: SensorEvent?) {
            sensorEvent ?: return
            val now = currentBlock()
            val stepCount = sensorEvent.values[0].toInt()
            synchronized(lock) {
                if (previousStepCount >= 0) {
                    var recentStepCount = stepCount - previousStepCount
                    // The counter starts at 0 again after a reboot
                    if (recentStepCount < 0) recentStepCount = 0
                    stepsMap[now] = (stepsMap[now] ?: 0) + recentStepCount
                }
                previousStepCount = stepCount
                if (stepsMap.size > NUM_OF_5MIN_BLOCKS_TO_KEEP) {
                    val removeBefore = now - NUM_OF_5MIN_BLOCKS_TO_KEEP
                    stepsMap.entries.removeAll { it.key < removeBefore }
                }
            }
        }
    }

    private val movementListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSensorChanged(sensorEvent: SensorEvent?) {
            sensorEvent ?: return
            val x = sensorEvent.values[0]
            val y = sensorEvent.values[1]
            val z = sensorEvent.values[2]
            val acceleration = (x * x + y * y + z * z) / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH)
            if (acceleration > MOVEMENT_THRESHOLD) lastMovement = dateUtil.now()
        }
    }

    override fun start() {
        synchronized(lock) {
            if (started) return
            started = true
            startTime = dateUtil.now()
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            aapsLogger.error(LTag.APS, "No sensor service, the activity monitor has no steps")
            return
        }
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (stepSensor != null) sensorManager.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        else aapsLogger.error(LTag.APS, "The phone has no step counter, the activity monitor has no steps")
        if (accelerometer != null) sensorManager.registerListener(movementListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        else aapsLogger.error(LTag.APS, "The phone has no accelerometer, the activity monitor cannot see movement")
    }

    private fun currentBlock(): Long = (dateUtil.now() / FIVE_MINUTES_IN_MS.toDouble()).roundToLong()

    private fun stepsInBlock(blocksAgo: Int): Int = synchronized(lock) { stepsMap[currentBlock() - blocksAgo] ?: 0 }

    private fun stepsInLastBlocks(numberOf5MinBlocks: Int): Int = synchronized(lock) {
        val since = currentBlock() - numberOf5MinBlocks
        stepsMap.entries.filter { it.key > since }.sumOf { it.value }
    }

    override fun recentSteps5Min(): Int = stepsInBlock(1)
    override fun recentSteps10Min(): Int = stepsInBlock(2)
    override fun recentSteps15Min(): Int = stepsInBlock(3)
    override fun recentSteps30Min(): Int = stepsInLastBlocks(6)
    override fun recentSteps60Min(): Int = stepsInLastBlocks(12)
    override fun recentSteps180Min(): Int = stepsInLastBlocks(36)

    override fun phoneMoved(): Boolean = lastMovement + 15 * 60000 > dateUtil.now()

    override fun permissionGroups(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(Manifest.permission.ACTIVITY_RECOGNITION),
            rationaleTitle = ApsStrings.permission_activity_title,
            rationaleDescription = ApsStrings.need_step_count_permission
        )
    )

    private companion object {

        const val FIVE_MINUTES_IN_MS = 300000
        const val NUM_OF_5MIN_BLOCKS_TO_KEEP = 40
        const val MOVEMENT_THRESHOLD = 1.05
    }
}
