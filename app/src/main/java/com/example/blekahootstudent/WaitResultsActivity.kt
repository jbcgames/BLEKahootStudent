package com.example.blekahootstudent

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WaitResultsActivity : AppCompatActivity() {

    private val TAG = "WaitResultsActivity"

    // BLE
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // No llamamos a super => se desactiva el botón atrás
        // Puedes mostrar un Toast si deseas notificar
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wait_results)

        // Inicializar BLE
        val manager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = manager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // Comenzar a escanear en busca de "SHOWAW:<correctAnswer>"
        startScanForShowAnswers()
    }

    private fun startScanForShowAnswers() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Activa Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "BLE Scanner no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        if (isScanning) return

        val filter = ScanFilter.Builder()
            .setManufacturerData(0x1234, byteArrayOf())
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        Log.d(TAG, "Escaneando en WaitResultsActivity (SHOWAW, NEWROUND, etc.)")
    }

    private fun stopScan() {
        if (!isScanning) return
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "Escaneo detenido en WaitResultsActivity")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(cbType: Int, result: ScanResult) {
            super.onScanResult(cbType, result)
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Escaneo falló: $errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val data = record.getManufacturerSpecificData(0x1234) ?: return
        val dataString = String(data)
        Log.d(TAG, "WaitResultsActivity -> Recibido: $dataString")

        // 1) Manejar SHOWAW
        if (dataString.startsWith("SHOWAW:")) {
            val parts = dataString.split(":")
            if (parts.size == 2) {
                val correctAnswer = parts[1].trim()
                // Pasamos a ShowAnswersActivity con la respuesta correcta
                runOnUiThread {
                    stopScan()
                    goToShowAnswers(correctAnswer)
                }
            }
        }

        // 2) Si quieres también manejar NEWROUND desde aquí, podrías:
        /*
        if (dataString.startsWith("NEWROUND")) {
            runOnUiThread {
                stopScan()
                goToAnswersActivity()
            }
        }
        */
    }

    private fun goToShowAnswers(correctAnswer: String) {
        val intent = Intent(this, ShowAnswersActivity::class.java)
        intent.putExtra("EXTRA_CORRECT_ANSWER", correctAnswer)
        startActivity(intent)
        finish()
    }

    private fun goToAnswersActivity() {
        startActivity(Intent(this, AnswersActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }
}
