package com.example.myapp

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private var unlockr: IUnlockrService? = null
    private var connected = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var content: LinearLayout
    private lateinit var status: TextView

    private var terminalControls: LinearLayout? = null
    private var terminalScroll: ScrollView? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            unlockr = IUnlockrService.Stub.asInterface(service)
            connected = unlockr != null

            mainHandler.post {
                updateConnectionStatus()

                if (connected) {
                    status.text = try {
                        "connected • uid ${unlockr!!.uid}"
                    } catch (_: Exception) {
                        "connected"
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            unlockr = null
            connected = false

            mainHandler.post {
                updateConnectionStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()

        updateConnection()

        mainHandler.postDelayed(
            {
                updateConnection()
            },
            1000
        )
    }

    override fun onDestroy() {
        try {
            Shizuku.unbindUserService(
                Shizuku.UserServiceArgs(
                    ComponentName(
                        this,
                        UnlockrUserService::class.java
                    )
                ),
                serviceConnection,
                true
            )
        } catch (_: Exception) {
        }

        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(10, 10, 12))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 10, 12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "unlockr"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val spacer = View(this)

        status = TextView(this).apply {
            text = "not connected"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 18, 0, 18)
        }

        val terminalTab = makeButton("terminal")
        val lightsTab = makeButton("lights")

        tabs.addView(
            terminalTab,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        tabs.addView(
            lightsTab,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(tabs)

        root.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        terminalTab.setOnClickListener {
            showTerminal()
        }

        lightsTab.setOnClickListener {
            showLights()
        }

        setContentView(root)

        showTerminal()
    }

    private fun makeButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(30, 30, 34))
        }
    }

    private fun updateConnectionStatus() {
        status.text =
            if (connected && unlockr != null) {
                try {
                    "connected • uid ${unlockr!!.uid}"
                } catch (_: Exception) {
                    "connected"
                }
            } else {
                "not connected"
            }
    }

    private fun updateConnection() {
        if (!Shizuku.pingBinder()) {
            connected = false
            unlockr = null

            updateConnectionStatus()
            status.text = "shizuku/bytezuku unavailable"

            return
        }

        try {
            if (
                Shizuku.checkSelfPermission() !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(1001)

                status.text = "waiting for permission"

                return
            }
        } catch (e: Exception) {
            status.text =
                "permission check failed: ${e.javaClass.simpleName}"

            return
        }

        try {
            val args =
                Shizuku.UserServiceArgs(
                    ComponentName(
                        this,
                        UnlockrUserService::class.java
                    )
                )
                    .daemon(false)
                    .debuggable(true)
                    .version(1)
                    .tag("unlockr")

            Shizuku.bindUserService(
                args,
                serviceConnection
            )

            status.text = "connecting..."
        } catch (e: Exception) {
            connected = false
            unlockr = null

            status.text =
                "bind failed: ${e.javaClass.simpleName}"
        }
    }

    private fun clearContent() {
        content.removeAllViews()

        terminalControls = null
        terminalScroll = null
    }

    private fun showTerminal() {
        clearContent()

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val input = EditText(this).apply {
            hint = "adb command"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(25, 25, 28))
        }

        val run = makeButton("run")

        controls.addView(
            input,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        controls.addView(
            run,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val output = TextView(this).apply {
            text = "unlockr terminal\n\n"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(12, 12, 12, 12)
        }

        val scroll = ScrollView(this).apply {
            addView(output)
        }

        run.setOnClickListener {
            val service = unlockr

            if (!connected || service == null) {
                output.text =
                    "NOT CONNECTED\n\nUnlockr service is not connected."
                return@setOnClickListener
            }

            val command = input.text.toString().trim()

            if (command.isEmpty()) {
                output.text =
                    "enter a command first"
                return@setOnClickListener
            }

            output.text = "running...\n"

            executor.execute {
                val result =
                    try {
                        service.exec(command)
                    } catch (e: Exception) {
                        "COMMAND FAILED\n\n${e.javaClass.simpleName}: ${e.message}"
                    }

                mainHandler.post {
                    output.text = result
                }
            }
        }

        terminalControls = controls
        terminalScroll = scroll

        content.addView(
            controls,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun showLights() {
        clearContent()

        val back = makeButton("← back")

        back.setOnClickListener {
            showTerminal()
        }

        content.addView(
            back,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val connectionText = TextView(this).apply {
            text =
                if (connected && unlockr != null) {
                    try {
                        "service connected • uid ${unlockr!!.uid}"
                    } catch (_: Exception) {
                        "service connected"
                    }
                } else {
                    "service not connected"
                }

            textSize = 13f
            setTextColor(
                if (connected) {
                    Color.rgb(100, 255, 140)
                } else {
                    Color.rgb(255, 100, 100)
                }
            )

            setPadding(4, 16, 4, 16)
        }

        content.addView(connectionText)

        val output = TextView(this).apply {
            text =
                "press inspect lights to inspect the Quest light framework"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(12, 12, 12, 12)
        }

        val scroll = ScrollView(this).apply {
            addView(output)
        }

        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val inspect = makeButton("inspect lights")

        inspect.setOnClickListener {
            val service = unlockr

            if (!connected || service == null) {
                output.text =
                    "NOT CONNECTED\n\nUnlockr service is not connected.\n\n" +
                    "status: ${status.text}"
                return@setOnClickListener
            }

            output.text = "inspecting...\n"

            executor.execute {
                val result =
                    try {
                        service.inspectLights()
                    } catch (e: Exception) {
                        "INSPECTION FAILED\n\n" +
                            "${e.javaClass.simpleName}: ${e.message}"
                    }

                mainHandler.post {
                    output.text = result
                }
            }
        }

        content.addView(
            inspect,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        addLightButton(
            "red",
            0xFFFF0000.toInt(),
            output
        )

        addLightButton(
            "green",
            0xFF00FF00.toInt(),
            output
        )

        addLightButton(
            "blue",
            0xFF0000FF.toInt(),
            output
        )

        addLightButton(
            "white",
            0xFFFFFFFF.toInt(),
            output
        )

        addLightButton(
            "yellow",
            0xFFFFFF00.toInt(),
            output
        )

        addLightButton(
            "purple",
            0xFF8000FF.toInt(),
            output
        )

        addLightButton(
            "cyan",
            0xFF00FFFF.toInt(),
            output
        )

        val rainbow = makeButton("rainbow")

        rainbow.setOnClickListener {
            val service = unlockr

            if (!connected || service == null) {
                output.text =
                    "NOT CONNECTED\n\nUnlockr service is not connected."
                return@setOnClickListener
            }

            try {
                service.startRainbow()
                output.text =
                    "rainbow started\n\n" +
                    "the Quest LED should cycle continuously."
            } catch (e: Exception) {
                output.text =
                    "RAINBOW FAILED\n\n" +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        content.addView(
            rainbow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val off = makeButton("failsafe off")

        off.setOnClickListener {
            val service = unlockr

            if (!connected || service == null) {
                output.text =
                    "NOT CONNECTED\n\nUnlockr service is not connected."
                return@setOnClickListener
            }

            try {
                service.clearLed()
                output.text =
                    "LED cleared"
            } catch (e: Exception) {
                output.text =
                    "CLEAR FAILED\n\n" +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        content.addView(
            off,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addLightButton(
        name: String,
        color: Int,
        output: TextView
    ) {
        val button = makeButton(name)

        button.setOnClickListener {
            val service = unlockr

            if (!connected || service == null) {
                output.text =
                    "NOT CONNECTED\n\nUnlockr service is not connected."
                return@setOnClickListener
            }

            try {
                val success =
                    service.setLed(color)

                if (success) {
                    output.text =
                        "$name requested successfully\n\n" +
                        "the service accepted the light request."
                } else {
                    output.text =
                        "$name FAILED\n\n" +
                        service.inspectLights()
                }
            } catch (e: Exception) {
                output.text =
                    "$name FAILED\n\n" +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        content.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
}
