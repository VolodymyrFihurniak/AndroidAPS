package app.aaps.plugins.aps.learned

import app.aaps.plugins.aps.learned.model.LearnedModel

/**
 * autoISF build, personal prediction: when the plugin trains by itself. Without a model at once. Then again after a day,
 * while the phone is charging (a training takes many minutes of all cores), or after [RETRAIN_ANYWAY_MS] without a
 * charger.
 */
object TrainingSchedule {

    /** A model is trained again after a day, while the phone is charging */
    const val RETRAIN_MS = 24 * 3_600_000L

    /** ... and after two days even without a charger */
    const val RETRAIN_ANYWAY_MS = 48 * 3_600_000L

    /** After a training that gave no model (or failed), the next try waits this long */
    const val RETRY_MS = 3_600_000L

    /** A training is stopped after this long. v2: 20 minutes, the GRU of the expected curve needs time. */
    const val TIME_LIMIT_MS = 20 * 60_000L

    fun shouldStart(now: Long, model: LearnedModel?, lastAttempt: Long, running: Boolean, charging: Boolean): Boolean = when {
        running                         -> false
        now - lastAttempt < RETRY_MS    -> false
        model == null                   -> true
        charging                        -> now - model.trainedAt >= RETRAIN_MS
        else                            -> now - model.trainedAt >= RETRAIN_ANYWAY_MS
    }
}
