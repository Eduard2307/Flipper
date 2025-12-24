package com.example.systemtweaker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BadKbActivity : AppCompatActivity() {

    private lateinit var etScript: EditText
    private lateinit var kbLog: TextView
    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null

    // HID Report Descriptor for a standard Keyboard
    private val keyboardReportDescriptor = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x06.toByte(), // USAGE (Keyboard)
        0xA1.toByte(), 0x01.toByte(), // COLLECTION (Application)
        0x05.toByte(), 0x07.toByte(), //   USAGE_PAGE (Keyboard)
        0x19.toByte(), 0xE0.toByte(), //   USAGE_MINIMUM (Keyboard LeftControl)
        0x29.toByte(), 0xE7.toByte(), //   USAGE_MAXIMUM (Keyboard Right GUI)
        0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(), //   LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(), //   REPORT_SIZE (1)
        0x95.toByte(), 0x08.toByte(), //   REPORT_COUNT (8)
        0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
        0x95.toByte(), 0x01.toByte(), //   REPORT_COUNT (1)
        0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
        0x81.toByte(), 0x03.toByte(), //   INPUT (Cnst,Var,Abs)
        0x95.toByte(), 0x05.toByte(), //   REPORT_COUNT (5)
        0x75.toByte(), 0x01.toByte(), //   REPORT_SIZE (1)
        0x05.toByte(), 0x08.toByte(), //   USAGE_PAGE (LEDs)
        0x19.toByte(), 0x01.toByte(), //   USAGE_MINIMUM (Num Lock)
        0x29.toByte(), 0x05.toByte(), //   USAGE_MAXIMUM (Kana)
        0x91.toByte(), 0x02.toByte(), //   OUTPUT (Data,Var,Abs)
        0x95.toByte(), 0x01.toByte(), //   REPORT_COUNT (1)
        0x75.toByte(), 0x03.toByte(), //   REPORT_SIZE (3)
        0x91.toByte(), 0x03.toByte(), //   OUTPUT (Cnst,Var,Abs)
        0x95.toByte(), 0x06.toByte(), //   REPORT_COUNT (6)
        0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
        0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x65.toByte(), //   LOGICAL_MAXIMUM (101)
        0x05.toByte(), 0x07.toByte(), //   USAGE_PAGE (Keyboard)
        0x19.toByte(), 0x00.toByte(), //   USAGE_MINIMUM (Reserved (no event indicated))
        0x29.toByte(), 0x65.toByte(), //   USAGE_MAXIMUM (Keyboard Application)
        0x81.toByte(), 0x00.toByte(), //   INPUT (Data,Ary,Abs)
        0xC0.toByte()              // END_COLLECTION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bad_kb)

        etScript = findViewById(R.id.etScript)
        kbLog = findViewById(R.id.kbLog)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            initBluetoothHid()
        }

        findViewById<Button>(R.id.btnInject).setOnClickListener {
            val script = etScript.text.toString()
            runScript(script)
        }

        findViewById<Button>(R.id.btnPresetCmd).setOnClickListener {
            etScript.setText("DELAY 500\nGUI r\nDELAY 500\nSTRING cmd\nENTER")
        }

        findViewById<Button>(R.id.btnPresetRick).setOnClickListener {
            etScript.setText("DELAY 500\nGUI r\nDELAY 500\nSTRING https://www.youtube.com/watch?v=dQw4w9WgXcQ\nENTER")
        }

        findViewById<Button>(R.id.btnPresetFakeUpd).setOnClickListener {
            etScript.setText("DELAY 500\nGUI r\nDELAY 500\nSTRING https://fakeupdate.net/win10/\nENTER\nDELAY 2000\nF11")
        }
    }

    private fun initBluetoothHid() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
             Toast.makeText(this, "Missing BT Permissions", Toast.LENGTH_SHORT).show()
             return
        }

        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerHidDevice()
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                hidDevice = null
                log("HID Service Disconnected")
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    private fun registerHidDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        val sdpSettings = BluetoothHidDevice.BluetoothHidDeviceAppSdpSettings(
            "FlipShark Keyboard",
            "FlipShark HID Provider",
            "FlipShark",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            keyboardReportDescriptor
        )

        val qosSettings = BluetoothHidDevice.BluetoothHidDeviceAppQosSettings(
            BluetoothHidDevice.ServiceType.BEST_EFFORT, 800, 9, 0, 11250, -1
        )

        val callback = object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                log("HID Registered: $registered")
                if (pluggedDevice != null) {
                    connectedDevice = pluggedDevice
                    log("Connected to: ${pluggedDevice.name}")
                }
            }

            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice = device
                    log("Connected to ${device?.name}")
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice = null
                    log("Disconnected")
                }
            }
        }

        hidDevice?.registerApp(sdpSettings, null, qosSettings, Context.getMainExecutor(this), callback)
        log("Registering HID Device... Enable Bluetooth visibility to pair with PC.")
    }

    private fun runScript(script: String) {
        if (connectedDevice == null) {
            log("[-] Error: Not connected to any PC. Pair via Bluetooth first.")
            return
        }
        
        log("> Parsing Payload...")
        CoroutineScope(Dispatchers.IO).launch {
            script.lines().forEach { line ->
                processLine(line.trim())
            }
            log("> Payload Injection Complete.")
        }
    }

    private suspend fun processLine(line: String) {
        if (line.isEmpty() || line.startsWith("REM")) return

        val parts = line.split(" ", limit = 2)
        val command = parts[0].uppercase()
        val args = if (parts.size > 1) parts[1] else ""

        when (command) {
            "DELAY" -> delay(args.toLongOrNull() ?: 100)
            "STRING" -> sendString(args)
            "ENTER" -> sendKey(0x28.toByte()) // 0x28 is Enter
            "GUI" -> sendKey(0x00.toByte(), 0x08.toByte()) // Win/CMD key modifier
            // Add more Ducky Script commands here
        }
    }

    private suspend fun sendString(str: String) {
        str.forEach { char ->
            // Simple mapping for demo. In real app, need full ASCII->HID map
            val (code, mod) = charToHid(char)
            sendKey(code, mod)
            delay(20) // Typing speed
        }
    }

    private suspend fun sendKey(key: Byte, modifier: Byte = 0) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        
        val report = ByteArray(8)
        report[0] = modifier
        report[2] = key
        
        try {
            hidDevice?.sendReport(connectedDevice, 0, report)
            delay(5) // Hold key for 5ms
            
            // Release
            val releaseReport = ByteArray(8)
            hidDevice?.sendReport(connectedDevice, 0, releaseReport)
            
        } catch (e: Exception) {
            log("Send Error: ${e.message}")
        }
    }

    private fun charToHid(char: Char): Pair<Byte, Byte> {
        val code: Int
        var mod: Int = 0
        
        if (char.isUpperCase()) {
            mod = 0x02 // Left Shift
        }
        
        // Simple ASCII to HID mapping (incomplete, covers basics)
        val c = char.lowercaseChar()
        code = when (c) {
            in 'a'..'z' -> (c - 'a') + 0x04
            in '1'..'9' -> (c - '1') + 0x1E
            '0' -> 0x27
            ' ' -> 0x2C
            '-' -> 0x2D
            '=' -> 0x2E
            '[' -> 0x2F
            ']' -> 0x30
            '\\' -> 0x31
            ';' -> 0x33
            '\'' -> 0x34
            ',' -> 0x36
            '.' -> 0x37
            '/' -> 0x38
            '\n' -> 0x28 // Enter
            else -> 0x2C // Default to space if unknown
        }
        
        return Pair(code.toByte(), mod.toByte())
    }

    private fun log(msg: String) {
        runOnUiThread {
            kbLog.text = "> $msg"
        }
    }
}
