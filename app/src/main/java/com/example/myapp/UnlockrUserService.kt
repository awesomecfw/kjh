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
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    private var unlockr: IUnlockrService? = null
    private var connected = false

    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var command: EditText

    private val permissionCode = 4201

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            unlockr = IUnlockrService.Stub.asInterface(service)
            connected = unlockr != null

            main.post {
                if (connected) {
                    val uid = unlockr?.uid ?: -1

                    status.text =
                        "unlockr: connected • uid $uid"

                    output.text =
                        "UNLOCKR READY\n\n" +
                        "backend: Bytezuku / Shizuku\n" +
                        "uid: $uid\n\n" +
                        "lights service ready"
                } else {
                    status.text = "unlockr: connection failed"
                }
            }
        }

        override fun onServiceDisconnected(
            name: ComponentName?
        ) {
            unlockr = null
            connected = false

            main.post {
                status.text = "unlockr: disconnected"
            }
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener {
                requestCode,
                _ ->

            if (requestCode == permissionCode) {
                updateConnection()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        buildUi()

        Shizuku.addRequestPermissionResultListener(
            permissionListener
        )

        updateConnection()
    }

    override fun onDestroy() {
        try {
            Shizuku.unbindUserService(
                serviceArgs(),
                serviceConnection,
                true
            )
        } catch (_: Exception) {
        }

        Shizuku.removeRequestPermissionResultListener(
            permissionListener
        )

        executor.shutdownNow()

        super.onDestroy()
    }

    private fun updateConnection() {
        if (!Shizuku.pingBinder()) {
            status.text =
                "unlockr: Bytezuku not running"

            output.text =
                "start Bytezuku first."

            return
        }

        if (
            Shizuku.checkSelfPermission() !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            status.text =
                "unlockr: permission required"

            Shizuku.requestPermission(
                permissionCode
            )

            return
        }

        connectUserService()
    }

    private fun connectUserService() {
        try {
            Shizuku.bindUserService(
                serviceArgs(),
                serviceConnection
            )
        } catch (e: Exception) {
            status.text =
                "unlockr: bind failed"

            output.text =
                "${e.javaClass.simpleName}\n\n${e.message}"
        }
    }

    private fun serviceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(
                this,
                UnlockrUserService::class.java
            )
        )
            .daemon(false)
            .debuggable(true)
            .version(1)
            .tag("unlockr")
    }

    private fun buildUi() {
        val root = LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            24,
            24,
            24,
            24
        )

        root.setBackgroundColor(
            Color.rgb(10, 10, 12)
        )

        status = TextView(this)

        status.text =
            "unlockr: connecting..."

        status.textSize = 15f

        status.setTextColor(
            Color.WHITE
        )

        root.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val tabs = LinearLayout(this)

        tabs.orientation =
            LinearLayout.HORIZONTAL

        tabs.gravity =
            Gravity.CENTER_VERTICAL

        root.addView(
            tabs,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
                bottomMargin = 16
            }
        )

        val terminalButton =
            makeButton("terminal")

        val lightsButton =
            makeButton("lights")

        tabs.addView(
            terminalButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        tabs.addView(
            lightsButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val content =
            LinearLayout(this)

        content.orientation =
            LinearLayout.VERTICAL

        root.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        command =
            EditText(this)

        command.hint =
            "adb shell command"

        command.setSingleLine(true)

        command.setTextColor(
            Color.WHITE
        )

        command.setHintTextColor(
            Color.GRAY
        )

        command.setBackgroundColor(
            Color.rgb(25, 25, 28)
        )

        command.setPadding(
            18,
            14,
            18,
            14
        )

        val run =
            makeButton("run")

        val terminalControls =
            LinearLayout(this)

        terminalControls.orientation =
            LinearLayout.HORIZONTAL

        terminalControls.addView(
            command,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        terminalControls.addView(
            run,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(
            terminalControls
        )

        output =
            TextView(this)

        output.text =
            "waiting for unlockr..."

        output.textSize =
            13f

        output.typeface =
            android.graphics.Typeface.MONOSPACE

        output.setTextColor(
            Color.rgb(225, 225, 225)
        )

        output.setPadding(
            12,
            16,
            12,
            16
        )

        val scroll =
            ScrollView(this)

        scroll.addView(output)

        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 12
            }
        )

        setContentView(root)

        run.setOnClickListener {
            runCommand(
                command.text.toString()
            )
        }

        terminalButton.setOnClickListener {
            showTerminal(
                terminalControls,
                scroll
            )
        }

        lightsButton.setOnClickListener {
            showLights(
                content,
                terminalControls,
                scroll
            )
        }
    }

    private fun showTerminal(
        controls: LinearLayout,
        scroll: ScrollView
    ) {
        controls.visibility =
            android.view.View.VISIBLE

        scroll.visibility =
            android.view.View.VISIBLE

        output.text =
            "SAFE ADB TERMINAL\n\n" +
            "try:\n" +
            "id\n" +
            "getprop ro.product.model\n" +
            "dumpsys lights\n" +
            "pm list packages\n" +
            "ps\n" +
            "wm size\n" +
            "wm density\n"
    }

    private fun showLights(
        content: LinearLayout,
        controls: LinearLayout,
        scroll: ScrollView
    ) {
        controls.visibility =
            android.view.View.GONE

        scroll.visibility =
            android.view.View.GONE

        val panel =
            LinearLayout(this)

        panel.orientation =
            LinearLayout.VERTICAL

        val back =
            makeButton("back")

        val inspect =
            makeButton("inspect lights")

        val red =
            makeButton("red")

        val green =
            makeButton("green")

        val blue =
            makeButton("blue")

        val white =
            makeButton("white")

        val yellow =
            makeButton("yellow")

        val purple =
            makeButton("purple")

        val cyan =
            makeButton("cyan")

        val rainbow =
            makeButton("rainbow")

        val off =
            makeButton("failsafe off")

        panel.addView(back)
        panel.addView(inspect)
        panel.addView(red)
        panel.addView(green)
        panel.addView(blue)
        panel.addView(white)
        panel.addView(yellow)
        panel.addView(purple)
        panel.addView(cyan)
        panel.addView(rainbow)
        panel.addView(off)

        content.removeView(controls)
        content.removeView(scroll)

        content.addView(
            panel,
            0,
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
            ).apply {
                topMargin = 12
            }
        )

        back.setOnClickListener {
            content.removeView(panel)
            showTerminal(
                controls,
                scroll
            )
        }

        inspect.setOnClickListener {
            remote {
                unlockr?.inspectLights()
                    ?: "not connected"
            }
        }

        red.setOnClickListener {
            led(
                0xFFFF0000.toInt(),
                "red"
            )
        }

        green.setOnClickListener {
            led(
                0xFF00FF00.toInt(),
                "green"
            )
        }

        blue.setOnClickListener {
            led(
                0xFF0000FF.toInt(),
                "blue"
            )
        }

        white.setOnClickListener {
            led(
                0xFFFFFFFF.toInt(),
                "white"
            )
        }

        yellow.setOnClickListener {
            led(
                0xFFFFFF00.toInt(),
                "yellow"
            )
        }

        purple.setOnClickListener {
            led(
                0xFF8000FF.toInt(),
                "purple"
            )
        }

        cyan.setOnClickListener {
            led(
                0xFF00FFFF.toInt(),
                "cyan"
            )
        }

        rainbow.setOnClickListener {
            remote {
                if (unlockr == null) {
                    "not connected"
                } else {
                    unlockr!!.startRainbow()

                    "rainbow started\n\n" +
                        "failsafe: 15 seconds"
                }
            }
        }

        off.setOnClickListener {
            remote {
                if (unlockr == null) {
                    "not connected"
                } else {
                    unlockr!!.clearLed()

                    "LED override cleared"
                }
            }
        }
    }

    private fun led(
        color: Int,
        name: String
    ) {
        remote {
            val service =
                unlockr

            if (service == null) {
                return@remote "not connected"
            }

            val ok =
                service.setLed(color)

            if (ok) {
                "$name LED request sent\n\n" +
                    "light id: 1\n" +
                    "uid: ${service.uid}\n" +
                    "failsafe: 10 seconds\n\n" +
                    "check the physical LED"
            } else {
                "LED REQUEST FAILED\n\n" +
                    "requested: $name\n" +
                    "light id: 1\n\n" +
                    service.inspectLights()
            }
        }
    }

    private fun runCommand(
        value: String
    ) {
        if (value.isBlank()) {
            return
        }

        remote {
            unlockr?.exec(value)
                ?: "not connected"
        }
    }

    private fun remote(
        block: () -> String?
    ) {
        executor.execute {
            val result =
                try {
                    block() ?: ""
                } catch (e: Exception) {
                    "${e.javaClass.simpleName}: ${e.message}"
                }

            main.post {
                output.text = result
            }
        }
    }

    private fun makeButton(
        text: String
    ): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(
                Color.rgb(30, 30, 34)
            )
            setPadding(
                12,
                8,
                12,
                8
            )
        }
    }
}
