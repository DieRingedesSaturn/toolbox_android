package com.example.toolbox.location

data class MockLocationConfig(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val durationMinutes: Int,
) {
    fun isValid(): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        accuracyMeters.isFinite() && accuracyMeters in 1f..1_000_000f &&
        durationMinutes in DURATIONS_MINUTES

    companion object {
        val DURATIONS_MINUTES = listOf(15, 30, 60)
        const val DEFAULT_ACCURACY_TEXT = "50"

        fun parseAccuracyMeters(text: String): Float? =
            text.trim().toFloatOrNull()?.takeIf { it.isFinite() && it in 1f..1_000_000f }

        fun parse(latitude: String, longitude: String, accuracy: String, minutes: Int): MockLocationConfig? {
            val config = MockLocationConfig(
                latitude.trim().toDoubleOrNull() ?: return null,
                longitude.trim().toDoubleOrNull() ?: return null,
                accuracy.trim().toFloatOrNull() ?: return null,
                minutes,
            )
            return config.takeIf { it.isValid() }
        }
    }
}

enum class MockLocationPhase { STOPPED, STARTING, RUNNING, STOPPING, ERROR, CLEANUP_REQUIRED }

enum class MockLocationFailure { ACCESS, SERVICES_DISABLED, NOTIFICATIONS, PROVIDER, START, STOP }

data class MockLocationState(
    val phase: MockLocationPhase = MockLocationPhase.STOPPED,
    val config: MockLocationConfig? = null,
    val endsAtMillis: Long? = null,
    val failure: MockLocationFailure? = null,
    val environment: MockLocationEnvironment = MockLocationEnvironment(),
) {
    val busy: Boolean
        get() = phase in listOf(MockLocationPhase.STARTING, MockLocationPhase.RUNNING, MockLocationPhase.STOPPING)
}

data class MockLocationEnvironment(
    val access: Boolean = false,
    val cleanup: Boolean = false,
    val notifications: Boolean = false,
    val servicesEnabled: Boolean = false,
)
