/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package timber.log

/** The shared YouTube client logs through Timber, an Android library; here it goes to stderr. */
object Timber {
    private val enabled = System.getenv("SONARA_LOG") != null

    private fun log(level: String, message: String?, error: Throwable? = null) {
        if (!enabled) return
        System.err.println("[$level] $message")
        error?.printStackTrace()
    }

    fun tag(@Suppress("UNUSED_PARAMETER") tag: String): Timber = this

    fun v(message: String?, vararg args: Any?) = log("V", message)
    fun d(message: String?, vararg args: Any?) = log("D", message)
    fun i(message: String?, vararg args: Any?) = log("I", message)
    fun w(message: String?, vararg args: Any?) = log("W", message)
    fun e(message: String?, vararg args: Any?) = log("E", message)
    fun d(error: Throwable?, message: String?, vararg args: Any?) = log("D", message, error)
    fun w(error: Throwable?, message: String?, vararg args: Any?) = log("W", message, error)
    fun e(error: Throwable?, message: String?, vararg args: Any?) = log("E", message, error)
    fun w(error: Throwable?) = log("W", error?.message, error)
    fun e(error: Throwable?) = log("E", error?.message, error)
}
