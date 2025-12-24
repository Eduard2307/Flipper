package com.example.systemtweaker

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

class NetworkScannerActivity : AppCompatActivity() {

    private lateinit var scanResults: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network)

        val btnScan = findViewById<Button>(R.id.btnScan)
        scanResults = findViewById(R.id.scanResults)

        btnScan.setOnClickListener {
            startScan()
        }
    }

    private fun startScan() {
        scanResults.text = "> Initializing Scan...\n"
        
        CoroutineScope(Dispatchers.IO).launch {
            val myIp = getLocalIpAddress()
            withContext(Dispatchers.Main) {
                scanResults.append("> My IP: $myIp\n")
            }

            if (myIp == null) return@launch

            val prefix = myIp.substring(0, myIp.lastIndexOf('.') + 1)
            
            withContext(Dispatchers.Main) {
                scanResults.append("> Scanning subnet $prefix.0/24 ...\n")
            }

            for (i in 1..254) {
                val testIp = prefix + i
                try {
                    val address = InetAddress.getByName(testIp)
                    if (address.isReachable(50)) { // Fast check
                        val hostName = address.hostName
                        withContext(Dispatchers.Main) {
                            scanResults.append("[+] HOST FOUND: $testIp ($hostName)\n")
                            scanResults.append("    -> Scanning ports...\n")
                        }
                        
                        // Port Scan logic
                        val openPorts = scanPorts(testIp)
                        withContext(Dispatchers.Main) {
                            if (openPorts.isNotEmpty()) {
                                scanResults.append("    [!] OPEN PORTS: ${openPorts.joinToString(", ")}\n")
                            } else {
                                scanResults.append("    [-] No common ports open.\n")
                            }
                            
                            // Auto scroll
                            val scrollAmount = scanResults.layout.getLineTop(scanResults.lineCount) - scanResults.height
                            if (scrollAmount > 0) scanResults.scrollTo(0, scrollAmount)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            withContext(Dispatchers.Main) {
                scanResults.append("> Scan Complete.\n")
            }
        }
    }

    private fun scanPorts(ip: String): List<Int> {
        val commonPorts = mapOf(
            21 to "FTP",
            22 to "SSH",
            23 to "Telnet",
            80 to "HTTP",
            443 to "HTTPS",
            445 to "SMB",
            3389 to "RDP",
            8080 to "HTTP-Proxy",
            53 to "DNS",
            554 to "RTSP" // Cameras
        )
        val openPorts = mutableListOf<Int>()
        
        for (port in commonPorts.keys) {
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(ip, port), 200) // 200ms timeout per port
                socket.close()
                openPorts.add(port)
            } catch (e: Exception) {
                // Closed
            }
        }
        return openPorts
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') < 0) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
