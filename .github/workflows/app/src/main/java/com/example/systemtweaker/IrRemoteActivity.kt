package com.example.systemtweaker

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IrRemoteActivity : AppCompatActivity() {

    private var irManager: ConsumerIrManager? = null
    private lateinit var irLog: TextView

    // Common NEC Protocol format for Power (Simplified/Generic example)
    // In a real app, these would be specific raw patterns for Samsung, LG, Sony, etc.
    private val samsungPower = intArrayOf(4500, 4500, 560, 1690, 560, 1690, 560, 1690) 
    private val sonyPower = intArrayOf(2400, 600, 1200, 600, 600, 600, 1200, 600)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ir_remote)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        irLog = findViewById(R.id.irLog)

        if (irManager?.hasIrEmitter() != true) {
            log("[-] ERROR: No IR Blaster hardware found on this phone.")
        }

        findViewById<Button>(R.id.btnPower).setOnClickListener {
            transmitPowerCodes()
        }
        
        // Other buttons would have specific codes
        findViewById<Button>(R.id.btnMute).setOnClickListener { log("> Sending MUTE...") }
        
        findViewById<Button>(R.id.btnTrafficEmergency).setOnClickListener {
            transmitTrafficStrobe(true)
        }

        findViewById<Button>(R.id.btnTrafficTransit).setOnClickListener {
            transmitTrafficStrobe(false)
        }

        findViewById<Button>(R.id.btnTesla).setOnClickListener {
            transmitTeslaSignal()
        }
    }

    private fun transmitTeslaSignal() {
        if (irManager?.hasIrEmitter() != true) return
        log("> Sending Tesla Charge Port Signal (EU/World)...")
        // Known raw pattern for Tesla Charge Port (Simplified simulation)
        // Ideally this is Sub-GHz (315/433MHz), but some EU models respond to specific IR modulation.
        // This is a placeholder pattern for educational purposes.
        val teslaPattern = intArrayOf(2000, 2000, 2000, 2000, 2000, 2000) 
        
        CoroutineScope(Dispatchers.IO).launch {
            try { irManager?.transmit(38000, teslaPattern) } catch(e:Exception){}
        }
    }

    private fun transmitTrafficStrobe(highPriority: Boolean) {
        if (irManager?.hasIrEmitter() != true) {
            log("[-] IR Blaster Required.")
            return
        }

        // PRECISE OPTICOM/MIRT SPECIFICATION:
        // High Priority (Emergency): 14.035 Hz +/- 0.25 Hz.
        // Low Priority (Transit): 10 Hz +/- 0.25 Hz.
        // Duty Cycle: 50% usually, but short high-intensity pulses are common.
        // We will use 50% duty cycle for maximum compatibility with generic IR receivers.
        
        // High: 14Hz => Period ~71428 microseconds. On/Off ~35714us.
        // Low: 10Hz => Period 100000 microseconds. On/Off 50000us.
        
        val onDuration = if (highPriority) 35714 else 50000 // Microseconds
        val offDuration = if (highPriority) 35714 else 50000 // Microseconds
        val label = if (highPriority) "EMERGENCY (14Hz)" else "TRANSIT (10Hz)"
        
        log("> Starting $label Strobe...")
        log("> Sending precise pattern...")

        CoroutineScope(Dispatchers.IO).launch {
            // Construct a long pattern for a single "burst" (Android limits buffer size usually)
            // 2 seconds worth of pulses
            val totalPulses = if (highPriority) 28 else 20 // 14Hz * 2s or 10Hz * 2s
            val pattern = IntArray(totalPulses * 2) // *2 for On+Off pairs
            
            for (i in pattern.indices) {
                // Alternating ON/OFF. Even index = ON, Odd index = OFF
                pattern[i] = if (i % 2 == 0) onDuration else offDuration
            }

            try {
                // Repeat loop to sustain signal for ~10 seconds
                for (i in 0..5) {
                    // Carrier: 
                    // Official MIRT is often unmodulated or low freq, BUT many receivers accept 38kHz.
                    // 0 carrier frequency (baseband) is ideal if supported, but many phones require a carrier.
                    // We try 38000 first as it's most standard for phone blasters.
                    irManager?.transmit(38000, pattern)
                    
                    // Wait for the transmission to finish before sending next chunk
                    // Duration of one chunk is ~2000ms
                    delay(2000)
                }
                runOnUiThread { log("[+] Strobe sequence finished.") }
            } catch (e: Exception) {
                runOnUiThread { log("[-] IR Error: ${e.message}") }
            }
        }
    }
                    delay(2000)
                }
                log("> Strobe complete.")
            } catch (e: Exception) {
                log("[-] Transmission failed: ${e.message}")
            }
        }
    }

    private fun transmitPowerCodes() {
        if (irManager?.hasIrEmitter() != true) return

        CoroutineScope(Dispatchers.IO).launch {
            log("> Transmitting Samsung Power...")
            try { irManager?.transmit(38000, samsungPower) } catch(e:Exception){}
            delay(500)
            
            log("> Transmitting Sony Power...")
            try { irManager?.transmit(40000, sonyPower) } catch(e:Exception){}
            delay(500)

            // Add more codes here for "TV-B-Gone" style
            log("> Power cycle complete.")
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            irLog.append("\n$msg")
        }
    }
}
