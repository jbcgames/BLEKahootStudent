package com.example.blekahootstudent

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class WaitResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wait_results)

        // Aquí podrías escanear para "SHOWRESULTS" u otra señal,
        // o simplemente esperar a que el profesor muestre los resultados en su pantalla.
    }
}
