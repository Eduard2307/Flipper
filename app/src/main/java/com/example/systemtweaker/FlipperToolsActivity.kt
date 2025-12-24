package com.example.systemtweaker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class FlipperToolsActivity : AppCompatActivity() {

    private lateinit var toolLog: TextView
    private var nfcAdapter: NfcAdapter? = null
    private var irManager: ConsumerIrManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Handle permission results
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flipper)

        toolLog = findViewById(R.id.toolLog)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        
        // Setup NFC Intent
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        nfcPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        findViewById<Button>(R.id.btnNfcRead).setOnClickListener {
            checkNfc()
        }

        findViewById<Button>(R.id.btnNfcClone).setOnClickListener {
             // Link to Payment Emulator or other future write tools
             Toast.makeText(this, "Payment Emulation Service is Active (Background)", Toast.LENGTH_LONG).show()
             log("[*] PAYMENT EMULATOR ACTIVE")
             log("    The phone will now respond to POS terminals.")
             log("    (Service: PaymentEmulatorService)")
        }

        findViewById<Button>(R.id.btnIrTest).setOnClickListener {
            // testIr() - Replaced with full Activity
            startActivity(android.content.Intent(this, IrRemoteActivity::class.java))
        }

        findViewById<Button>(R.id.btnBleScan).setOnClickListener {
            scanBle()
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkNfc() {
        if (nfcAdapter == null) {
            log("[-] NFC is not available on this device.")
        } else if (!nfcAdapter!!.isEnabled) {
            log("[-] NFC is disabled. Please enable it in settings.")
        } else {
            log("[+] NFC is ready. Hold a tag to READ.")
            // Foreground dispatch is enabled in onResume
        }
    }

    private fun enableNfcReader() {
        log("> Listening for NFC tags (Reader Mode)...")
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {
            nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter?.isEnabled == true) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val uid = tag.id.joinToString(":") { "%02X".format(it) }
                log("[+] TAG DETECTED!")
                log("    UID: $uid")
                log("    Tech: ${tag.techList.joinToString(", ") { it.substringAfterLast(".") }}")
            }
        }
    }

    // Placeholder for NFC Write logic (requires specific Tag technology handling)
    private fun writeNfcTag() {
        log("> Ready to WRITE to next detected tag...")
        // 1. Wait for Tag detection
        // 2. connect()
        // 3. writeNdefMessage()
    }

    private fun testIr() {
        if (irManager?.hasIrEmitter() == true) {
            log("[+] IR Emitter found.")
            // Simple frequency pattern (approx 38kHz)
            val pattern = intArrayOf(100, 100, 100, 100)
            try {
                irManager?.transmit(38000, pattern)
                log("[+] IR Signal Transmitted (Test Pattern).")
            } catch (e: Exception) {
                log("[-] IR Transmit failed: ${e.message}")
            }
        } else {
            log("[-] No IR Emitter found on this device.")
        }
    }

    private fun scanBle() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            log("[-] Bluetooth disabled or missing.")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            log("[-] Missing Bluetooth Permissions.")
            return
        }

        log("> Starting BLE Scan...")
        val scanner = bluetoothAdapter!!.bluetoothLeScanner
        
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val name = if (ActivityCompat.checkSelfPermission(this@FlipperToolsActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                         device.name ?: "Unknown"
                    } else "Unknown"
                    log("[+] Found BLE: $name (${device.address})")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log("[-] Scan failed: $errorCode")
            }
        }

        scanner.startScan(scanCallback)
        
        // Stop scan after 5 seconds to save battery
        toolLog.postDelayed({
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                scanner.stopScan(scanCallback)
                log("> BLE Scan stopped.")
            }
        }, 5000)
    }

    private fun log(message: String) {
        toolLog.append("$message\n")
        // Auto scroll
        val scrollAmount = toolLog.layout.getLineTop(toolLog.lineCount) - toolLog.height
        if (scrollAmount > 0)
            toolLog.scrollTo(0, scrollAmount)
    }
}
