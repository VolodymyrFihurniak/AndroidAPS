package app.aaps.plugins.aps.learned

import app.aaps.plugins.aps.learned.model.LearnedModel

/** autoISF build, personal prediction: when the plugin trains by itself */
object TrainingSchedule {

    /** A model is trained again after a day */
    const val RETRAIN_MS = 24 * 3_600_000L

    /** After a training that gave no model (or failed), the next try waits this long */
    const val RETRY_MS = 3_600_000L

    /** A training is stopped after this long. v2: 20 minutes, the GRU of the expected curve needs time. */
    const val TIME_LIMIT_MS = 20 * 60_000L

    fun shouldStart(now: Long, model: LearnedModel?, lastAttempt: Long, running: Boolean): Boolean = when {
        running                         -> false
        now - lastAttempt < RETRY_MS    -> false
        model == null                   -> true
        else                            -> now - model.trainedAt >= RETRAIN_MS
    }
}
