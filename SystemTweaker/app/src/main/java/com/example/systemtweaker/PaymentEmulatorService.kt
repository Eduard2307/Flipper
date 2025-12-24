package com.example.systemtweaker

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class PaymentEmulatorService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return ByteArray(0)

        val hexCommand = commandApdu.joinToString("") { "%02X".format(it) }
        Log.d("FlipShark_HCE", "Received APDU: $hexCommand")

        // 1. SELECT Command (00 A4 04 00 ...)
        if (hexCommand.startsWith("00A40400")) {
            // Return "90 00" (Success) + Generic FCI Template to trick terminal into "Processing..."
            // This is a minimal valid response to get the terminal to proceed to the next step
            // It mimics a generic card response.
            return hexStringToByteArray("6F10840E325041592E5359532E4444463031A5009000")
        }

        // 2. GPO (Get Processing Options) - The terminal asks "What can you do?"
        if (hexCommand.startsWith("80A8")) {
            // Return generic AFL (Application File Locator) + Success
            return hexStringToByteArray("80060080080101009000")
        }

        // 3. READ RECORD - The terminal asks for card data
        if (hexCommand.startsWith("00B2")) {
            // This is where we simulate a successful read.
            // We return a DUMMY track 2 equivalent data (fake card number)
            // Status: 90 00 (OK)
            return hexStringToByteArray("700A570840000000000000009000") 
        }

        // Default: Just say "OK" (90 00) to keep the conversation alive as long as possible
        return byteArrayOf(0x90.toByte(), 0x00.toByte())
    }

    override fun onDeactivated(reason: Int) {
        Log.d("FlipShark_HCE", "Deactivated: $reason")
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
