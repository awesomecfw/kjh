package com.example.myapp

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CODE = 100
    }

    private lateinit var output: TextView

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener

            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                testAccess()
            } else {
                output.text = "unlockr test\n\npermission denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)
        layout.setBackgroundColor(0xFF000000.toInt())

        output = TextView(this)
        output.textSize = 15f
        output.setTextColor(0xFFFFFFFF.toInt())
        output.text = "unlockr test\n\nchecking bytezuku..."

        val button = Button(this)
        button.text = "request unlockr access"

        button.setOnClickListener {
            requestAccess()
        }

        layout.addView(output)
        layout.addView(button)

        setContentView(layout)

        checkConnection()
    }

    private fun checkConnection() {
        if (!Shizuku.pingBinder()) {
            output.text =
                "unlockr test\n\n" +
                "bytezuku server not connected"
            return
        }

        output.text =
            "unlockr test\n\n" +
            "bytezuku server detected\n\n" +
            "uid: ${Shizuku.getUid()}\n\n" +
            "press the button to request access"
    }

    private fun requestAccess() {
        if (!Shizuku.pingBinder()) {
            output.text = "unlockr test\n\nbytezuku server not connected"
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            testAccess()
            return
        }

        if (Shizuku.shouldShowRequestPermissionRationale()) {
            output.text =
                "unlockr test\n\n" +
                "permission was denied previously"
            return
        }

        output.text =
            "unlockr test\n\n" +
            "requesting bytezuku access..."

        Shizuku.requestPermission(REQUEST_CODE)
    }

    private fun testAccess() {
        output.text =
            "unlockr test\n\n" +
            "ACCESS GRANTED\n\n" +
            "server uid: ${Shizuku.getUid()}\n" +
            "permission: granted\n\n" +
            "privileged binder is ready"
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
