package com.example.myapp

import android.app.Service
import android.content.Intent
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class UnlockrUserService : Service() {

    private val executor = Executors.newSingleThreadScheduledExecutor()

    private var rainbowTask: ScheduledFuture<*>? = null
    private var failsafeTask: ScheduledFuture<*>? = null
    private var session: LightsManager.LightsSession? = null

    private val binder = object : IUnlockrService.Stub() {

        override fun getUid(): Int {
            return android.os.Process.myUid()
        }

        override fun exec(command: String): String {
            if (!isSafeCommand(command)) {
                return "BLOCKED BY UNLOCKR FAILSAFE\n\nOnly safe read-only commands are allowed."
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

                        if (count <= 0) {
                            break
                        }

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
                    return "COMMAND TIMEOUT\n\nStopped after 5 seconds."
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

        override fun inspectLights(): String {
            return inspectLightsInternal()
        }

        override fun setLed(color: Int): Boolean {
            stopRainbowInternal()
            return setLedInternal(color)
        }

        override fun clearLed() {
            clearLedInternal()
        }

        override fun startRainbow() {
            startRainbowInternal()
        }

        override fun stopRainbow() {
            stopRainbowInternal()
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        stopRainbowInternal()
        clearLedInternal()

        executor.shutdownNow()

        super.onDestroy()
    }

    private fun isSafeCommand(command: String): Boolean {
        val c = command.trim()

        if (c.isEmpty()) return false
        if (c.length > 300) return false

        val blocked = listOf(
            ";",
            "|",
            "&",
            "`",
            "\$(`,
            ">",
            "<",
            "\n",
            "\r"
        )

        if (blocked.any { c.contains(it) }) {
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

        if (first !in allowed) {
            return false
        }

        if (first == "pm" && !c.startsWith("pm list ")) {
            return false
        }

        if (first == "settings" && !c.startsWith("settings get ")) {
            return false
        }

        if (
            first == "wm" &&
            !c.startsWith("wm size") &&
            !c.startsWith("wm density")
        ) {
            return false
        }

        if (
            first == "ls" &&
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

    private fun inspectLightsInternal(): String {
        return try {
            val manager = getSystemService(LightsManager::class.java)
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
                    } catch (_: Exception) {
                        append("color: unavailable\n")
                    }

                    append("\n")
                }
            }
        } catch (e: Exception) {
            "LIGHT INSPECTION FAILED\n\n${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun getQuestLight(): Light? {
        val manager = getSystemService(LightsManager::class.java)
            ?: return null

        return manager.lights.firstOrNull {
            it.id == 1
        }
    }

    private fun setLedInternal(color: Int): Boolean {
        val manager = getSystemService(LightsManager::class.java)
            ?: return false

        val light = getQuestLight()
            ?: return false

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

            scheduleFailsafe(10)

            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun clearLedInternal() {
        failsafeTask?.cancel(false)
        failsafeTask = null

        rainbowTask?.cancel(false)
        rainbowTask = null

        val light = getQuestLight()
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
    }

    private fun scheduleFailsafe(seconds: Long) {
        failsafeTask?.cancel(false)

        failsafeTask = executor.schedule(
            {
                clearLedInternal()
            },
            seconds,
            TimeUnit.SECONDS
        )
    }

    private fun startRainbowInternal() {
        stopRainbowInternal()

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

        rainbowTask = executor.scheduleAtFixedRate(
            {
                val color = colors[index % colors.size]
                index++

                setLedInternalNoFailsafe(color)
            },
            0,
            250,
            TimeUnit.MILLISECONDS
        )

        failsafeTask?.cancel(false)

        failsafeTask = executor.schedule(
            {
                stopRainbowInternal()
                clearLedInternal()
            },
            15,
            TimeUnit.SECONDS
        )
    }

    private fun setLedInternalNoFailsafe(color: Int) {
        val manager = getSystemService(LightsManager::class.java)
            ?: return

        val light = getQuestLight()
            ?: return

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

    private fun stopRainbowInternal() {
        rainbowTask?.cancel(false)
        rainbowTask = null
    }
}
