package com.example.blekahootstudent

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ShowAnswersActivity : AppCompatActivity() {

    private val TAG = "ShowAnswersActivity"

    // BLE
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false

    // Asignado en CONF. Lo leeremos de SharedPreferences
    private var assignedCode: String? = null

    // Respuesta elegida por el estudiante
    private var studentAnswer: String? = null

    // Respuesta correcta (la que viene en "SHOWAW:<correctAnswer>")
    private var correctAnswer: String? = null

    // Referencias a los botones (deshabilitados desde XML)
    private lateinit var btnA: Button
    private lateinit var btnB: Button
    private lateinit var btnC: Button
    private lateinit var btnD: Button

    // Flag advertising "ACK_SHOWAW"
    private var isAdvertisingAckShowAw = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_answers)
        startAdvertisingAckShowAwIndefinitely()
        // UI - recoger referencias
        btnA = findViewById(R.id.btnA)
        btnB = findViewById(R.id.btnB)
        btnC = findViewById(R.id.btnC)
        btnD = findViewById(R.id.btnD)

        // Recuperar assignedCode y studentAnswer de SharedPreferences
        val prefs = getSharedPreferences("BLE_Kahoot_Student", MODE_PRIVATE)
        assignedCode = prefs.getString("assigned_code", null)
        studentAnswer = prefs.getString("last_response", null) // "A", "B", "C", "D", o "BLANK"

        // Recuperar correctAnswer (pasado desde WaitResultsActivity via Intent)
        correctAnswer = intent.getStringExtra("EXTRA_CORRECT_ANSWER")

        // BLE
        val manager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = manager.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // Resaltar la respuesta correcta vs la elegida
        highlightAnswers()

        // Escanear para esperar la siguiente señal (ej: "NEWROUND")
        startScanForNextSignal()

        // Enviar ACK_SHOWAW indefinidamente

    }

    /**
     * Destaca las respuestas:
     * - Si studentAnswer == correctAnswer => esa en verde
     * - Si no, esa en rojo y la correcta en verde
     */
    private fun highlightAnswers() {
        // Por defecto, color negro
        btnA.setTextColor(Color.BLACK)
        btnB.setTextColor(Color.BLACK)
        btnC.setTextColor(Color.BLACK)
        btnD.setTextColor(Color.BLACK)

        if (correctAnswer.isNullOrEmpty()) {
            Toast.makeText(this, "No se recibió la respuesta correcta", Toast.LENGTH_SHORT).show()
            return
        }

        val correct = correctAnswer!!
        val chosen = studentAnswer.orEmpty()

        if (chosen == correct) {
            // Respondió correctamente
            colorButtonForAnswer(chosen, Color.GREEN)
        } else {
            // Resaltar la correcta en verde
            colorButtonForAnswer(correct, Color.GREEN)
            // Y si el alumno marcó otra, marcarla en rojo
            if (chosen.isNotEmpty() && chosen != "BLANK") {
                colorButtonForAnswer(chosen, Color.RED)
            }
        }
    }

    private fun colorButtonForAnswer(answer: String, color: Int) {
        // Los botones tienen texto "A", "B", "C", "D"
        // Compara la respuesta en mayúscula
        when (answer.uppercase()) {
            "A" -> btnA.setTextColor(color)
            "B" -> btnB.setTextColor(color)
            "C" -> btnC.setTextColor(color)
            "D" -> btnD.setTextColor(color)
        }
    }

    // ----------------------------------------
    // BLE: Anuncio ACK_SHOWAW:<code>
    // ----------------------------------------
    private fun startAdvertisingAckShowAwIndefinitely() {
        if (assignedCode.isNullOrEmpty()) {
            Log.e(TAG, "No assignedCode => no se puede enviar ACK_SHOWAW")
            return
        }
        if (isAdvertisingAckShowAw) return // ya se está anunciando

        stopAdvertisingIfNeeded()

        val dataString = "ACK_SHOWAW:$assignedCode"
        val dataToSend = dataString.toByteArray()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(0x1234, dataToSend)
            .setIncludeDeviceName(false)
            .build()

        advertiser?.startAdvertising(settings, data, ackShowAwAdvertiseCallback)
        isAdvertisingAckShowAw = true
        Log.d(TAG, "Estudiante: Iniciando ACK_SHOWAW indefinidamente")
    }

    private fun stopAdvertisingAckShowAw() {
        if (!isAdvertisingAckShowAw) return
        stopAdvertisingIfNeeded()
        isAdvertisingAckShowAw = false
        Log.d(TAG, "Estudiante: Publicidad ACK_SHOWAW detenida")
    }

    private val ackShowAwAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Estudiante: Advertising ACK_SHOWAW iniciado correctamente")
        }
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Estudiante: Error al iniciar Advertising ACK_SHOWAW: $errorCode")
        }
    }

    // ----------------------------------------
    // BLE: Escanear por próxima señal (ej: "NEWROUND", etc)
    // ----------------------------------------
    private fun startScanForNextSignal() {
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
        Log.d(TAG, "Estudiante: Escaneo para señales entrantes (NEWROUND) iniciado")
    }

    private fun stopScan() {
        if (!isScanning) return
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "Estudiante: Escaneo detenido")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(cbType: Int, result: ScanResult) {
            super.onScanResult(cbType, result)
            val record = result.scanRecord ?: return
            val data = record.getManufacturerSpecificData(0x1234) ?: return
            val dataString = String(data)
            Log.d(TAG, "Estudiante -> Recibido: $dataString")

            if (dataString.startsWith("NEWROUND")) {
                // Al recibir NEWROUND, paramos el ACK_SHOWAW y vamos a AnswersActivity
                runOnUiThread {
                    stopAdvertisingAckShowAw()
                    stopScan()
                    goToAnswersActivity()
                }
            }
        }
    }

    private fun goToAnswersActivity() {
        startActivity(Intent(this, AnswersActivity::class.java))
        finish()
    }

    // ----------------------------------------
    // Utilidades
    // ----------------------------------------
    private fun stopAdvertisingIfNeeded() {
        advertiser?.stopAdvertising(ackShowAwAdvertiseCallback)
        Log.d(TAG, "Estudiante: Advertising detenido (si había alguno)")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        stopAdvertisingAckShowAw()
    }
}
