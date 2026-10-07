package com.youmak.ps4led

import java.util.concurrent.TimeUnit

object RootShell {
    data class Result(val exitCode: Int, val output: String)

    fun exec(command: String, timeoutMs: Long = 4000): Result {
        return try {
            val p = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = p.inputStream.bufferedReader().use { it.readText() }

            val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)

            if (!finished) {
                p.destroyForcibly()
                return Result(-1, "timeout")
            }

            Result(p.exitValue(), output.trim())
        } catch (e: Exception) {
            Result(-2, e.message ?: e.javaClass.simpleName)
        }
    }

    fun hasRoot(): Boolean =
        exec("id").exitCode == 0

    fun findPs4Led(): String? {
        val script = """
            for red in /sys/class/leds/0005:054C:*:red; do
                [ -e "${'$'}red" ] || continue
                base="${'$'}{red%:red}"
                [ -e "${'$'}base:green/brightness" ] || continue
                [ -e "${'$'}base:blue/brightness" ] || continue
                printf '%s' "${'$'}base"
                exit 0
            done
            exit 1
        """.trimIndent()

        val r = exec(script)

        return r.output.takeIf {
            r.exitCode == 0 &&
            it.startsWith("/sys/class/leds/0005:054C:")
        }
    }

    fun findSonyControllerBattery(): Int? {
        val script = """
            for capacity in /sys/class/power_supply/sony_cont*/capacity; do
                [ -f "${'$'}capacity" ] || continue
                value="${'$'}(cat "${'$'}capacity" 2>/dev/null)"
                case "${'$'}value" in
                    ''|*[!0-9]*) continue ;;
                esac
                printf '%s' "${'$'}value"
                exit 0
            done
            exit 1
        """.trimIndent()

        val result = exec(script)
        return if (result.exitCode == 0) {
            result.output.toIntOrNull()?.coerceIn(0, 100)
        } else {
            null
        }
    }

    fun setRgb(base: String, red: Int, green: Int, blue: Int): Result {
        // The controller has no usable RGB value 0; anything at or below 0
        // is applied as the minimum hardware value 1. Values above 255 remain capped.
        val r = red.coerceIn(1, 255)
        val g = green.coerceIn(1, 255)
        val b = blue.coerceIn(1, 255)

        val cmd =
            "printf '%d' $r > '${base}:red/brightness'; " +
            "printf '%d' $g > '${base}:green/brightness'; " +
            "printf '%d' $b > '${base}:blue/brightness'"

        return exec(cmd)
    }
}
