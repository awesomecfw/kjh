package com.example.myapp

import android.content.Context
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class UnlockrUserService(
    private val context: Context
) : IUnlockrService.Stub() {

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var rainbowTask: ScheduledFuture<*>? = null
    private var failsafeTask: ScheduledFuture<*>? = null

    private val lightsManager: LightsManager? =
        context.getSystemService(LightsManager::class.java)

    private val questLight: Light?
        get() = lightsManager?.lights?.firstOrNull { it.id == 1 }

    private var session: LightsManager.LightsSession? = null

    override fun getUid(): Int {
        return android.os.Process.myUid()
    }

    override fun exec(command: String): String {
        if (!isSafeCommand(command)) {
            return "BLOCKED BY UNLOCKR FAILSAFE\n\nOnly read-only ADB commands are allowed."
        }

        return try {
            val process = ProcessBuilder(
                "sh",
                "-c",
                command.trim()
            )
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()

            BufferedReader(
                InputStreamReader(process.inputStream)
            ).use { reader ->
                val buffer = CharArray(4096)
                var total = 0

                while (true) {
                    val count = reader.read(buffer)
                    if (count <= 0) break

                    val allowed = minOf(
                        count,
                        32768 - total
                    )

                    if (allowed > 0) {
                        output.append(buffer, 0, allowed)
                        total += allowed
                    }

                    if (total >= 32768) {
                        output.append("\n\n[output truncated]")
                        break
                    }
                }
            }

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "COMMAND TIMEOUT\n\nThe command was stopped after 5 seconds."
            }

            val result = output.toString()

            if (result.isBlank()) {
                "exit=${process.exitValue()}"
            } else {
                result
            }
        } catch (e: Exception) {
            "COMMAND FAILED\n\n${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun isSafeCommand(command: String): Boolean {
        val c = command.trim()

        if (c.isEmpty()) return false
        if (c.length > 300) return false

        val dangerousCharacters = listOf(
            ";",
            "|",
            "&",
            "`",
            "$(",
            "${'$'}(",
            ">",
            "<",
            "\n",
            "\r"
        )

        if (dangerousCharacters.any { c.contains(it) }) {
            return false
        }

        val first = c
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.lowercase()
            ?: return false

        val allowed = setOf(
            "id",
            "whoami",
            "getprop",
            "dumpsys",
            "ps",
            "ls",
            "pm",
            "settings",
            "wm",
            "uname"
        )

        if (first !in allowed) return false

        if (first == "pm" && !c.startsWith("pm list ")) {
            return false
        }

        if (first == "settings" && !c.startsWith("settings get ")) {
            return false
        }

        if (first == "wm" &&
            !c.startsWith("wm size") &&
            !c.startsWith("wm density")
        ) {
            return false
        }

        if (first == "ls" &&
            !c.startsWith("ls /sdcard") &&
            !c.startsWith("ls /storage") &&
            !c.startsWith("ls /system") &&
            !c.startsWith("ls /vendor") &&
            !c.startsWith("ls /data/local/tmp")
        ) {
            return false
        }

        return true
    }

    override fun inspectLights(): String {
        return try {
            val manager = lightsManager
                ?: return "LightsManager unavailable"

            val lights = manager.lights

            buildString {
                append("UNLOCKR LIGHT INSPECTOR\n\n")
                append("uid: ${android.os.Process.myUid()}\n")
                append("lights: ${lights.size}\n\n")

                for (light in lights) {
                    append("id: ${light.id}\n")
                    append("name: ${light.name}\n")
                    append("type: ${light.type}\n")
                    append("ordinal: ${light.ordinal}\n")
                    append("rgb: ${light.hasRgbControl()}\n")
                    append("brightness: ${light.hasBrightnessControl()}\n")

                    try {
                        val state = manager.getLightState(light)
                        append(
                            "color: #${
                                String.format(
                                    "%08X",
                                    state.color
                                )
                            }\n"
                        )
                    } catch (e: Exception) {
                        append("color: unavailable\n")
                    }

                    append("\n")
                }
            }
        } catch (e: Exception) {
            "LIGHT INSPECTION FAILED\n\n${e.javaClass.simpleName}: ${e.message}"
        }
    }

    override fun setLed(color: Int): Boolean {
        stopRainbow()

        val light = questLight ?: return false
        val manager = lightsManager ?: return false

        return try {
            if (session == null) {
                session = manager.openSession()
            }

            val state = LightState.Builder()
                .setColor(color)
                .build()

            val request = LightsRequest.Builder()
                .addLight(light, state)
                .build()

            session!!.requestLights(request)

            scheduleFailsafe()

            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    override fun clearLed() {
        stopRainbow()

        val light = questLight
        val currentSession = session

        if (light != null && currentSession != null) {
            try {
                val request = LightsRequest.Builder()
                    .clearLight(light)
                    .build()

                currentSession.requestLights(request)
            } catch (_: Exception) {
            }
        }

        try {
            currentSession?.close()
        } catch (_: Exception) {
        }

        session = null
        failsafeTask?.cancel(false)
        failsafeTask = null
    }

    private fun scheduleFailsafe() {
        failsafeTask?.cancel(false)

        failsafeTask = executor.schedule({
            clearLed()
        }, 10, TimeUnit.SECONDS)
    }

    override fun startRainbow() {
        stopRainbow()

        val colors = intArrayOf(
            0xFFFF0000.toInt(),
            0xFFFF7A00.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(),
            0xFF00FFFF.toInt(),
            0xFF0088FF.toInt(),
            0xFF8000FF.toInt(),
            0xFFFF00FF.toInt()
        )

        var index = 0

        rainbowTask = executor.scheduleAtFixedRate({
            val color = colors[index % colors.size]
            index++

            setLedInternal(color)
        }, 0, 250, TimeUnit.MILLISECONDS)

        failsafeTask?.cancel(false)

        failsafeTask = executor.schedule({
            stopRainbow()
            clearLed()
        }, 15, TimeUnit.SECONDS)
    }

    private fun setLedInternal(color: Int) {
        val light = questLight ?: return
        val manager = lightsManager ?: return

        try {
            if (session == null) {
                session = manager.openSession()
            }

            val state = LightState.Builder()
                .setColor(color)
                .build()

            val request = LightsRequest.Builder()
                .addLight(light, state)
                .build()

            session!!.requestLights(request)
        } catch (_: Exception) {
        }
    }

    override fun stopRainbow() {
        rainbowTask?.cancel(false)
        rainbowTask = null
    }
}
