package com.example.systemtweaker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnNetwork = findViewById<Button>(R.id.btnNetwork)
        val btnFlipper = findViewById<Button>(R.id.btnFlipper)

        btnNetwork.setOnClickListener {
            startActivity(Intent(this, NetworkScannerActivity::class.java))
        }

        btnFlipper.setOnClickListener {
            startActivity(Intent(this, FlipperToolsActivity::class.java))
        }

        val btnBadKb = findViewById<Button>(R.id.btnBadKb)
        btnBadKb.setOnClickListener {
            startActivity(Intent(this, BadKbActivity::class.java))
        }
    }
}
