package com.example.myapp

import android.app.Activity
import android.hardware.lights.LightsManager
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val output = TextView(this)

        output.textSize = 14f
        output.setPadding(32, 32, 32, 32)
        output.setTextColor(0xFFFFFFFF.toInt())
        output.setBackgroundColor(0xFF000000.toInt())

        setContentView(output)

        inspectLights(output)
    }

    private fun inspectLights(output: TextView) {
        try {
            val manager =
                getSystemService(LightsManager::class.java)

            val lights = manager.lights

            val text = StringBuilder()

            text.append("UNLOCKR LIGHT INSPECTOR\n\n")
            text.append("lights: ${lights.size}\n\n")

            for (light in lights) {
                text.append("id: ${light.id}\n")
                text.append("type: ${light.type}\n")
                text.append("ordinal: ${light.ordinal}\n")
                text.append("capabilities: ${light.capabilities}\n")

                try {
                    val state = manager.getLightState(light)
                    text.append("state: $state\n")
                } catch (e: Exception) {
                    text.append("state: ${e.javaClass.simpleName}\n")
                }

                text.append("\n")
            }

            output.text = text.toString()

        } catch (e: Exception) {
            output.text =
                "LIGHT INSPECTION FAILED\n\n" +
                "${e.javaClass.name}\n\n" +
                "${e.message}"
        }
    }
}
