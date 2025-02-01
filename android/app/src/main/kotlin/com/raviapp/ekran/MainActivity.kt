package com.raviapp.ekran

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.app.ActivityManager
import android.os.Build

class MainActivity : AppCompatActivity() {
    private lateinit var toggleButton: Button
    private lateinit var intervalSpinner: Spinner
    private var selectedInterval: Long = 60000 // Default 1 dakika

    private val intervals = arrayOf(
        "1 Dakika" to 60000L,
        "5 Dakika" to 300000L,
        "10 Dakika" to 600000L,
        "30 Dakika" to 1800000L,
        "1 Saat" to 3600000L,
        "3 Saat" to 10800000L,
        "5 Saat" to 18000000L,
        "10 Saat" to 36000000L,
        "16 Saat" to 57600000L,
        "24 Saat" to 86400000L
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupSpinner()
        setupButton()
        checkServiceStatus()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            intervals.map { it.first }.toTypedArray()
        ).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }

        intervalSpinner = findViewById(R.id.intervalSpinner)
        intervalSpinner.adapter = adapter
        intervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedInterval = intervals[position].second
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupButton() {
        toggleButton = findViewById(R.id.toggleButton)
        toggleButton.setOnClickListener {
            if (WallpaperService.isServiceRunning) {
                stopService()
            } else {
                startService()
            }
            updateButtonState()
        }
    }

    private fun checkServiceStatus() {
        val isRunning = isServiceRunning()
        WallpaperService.updateServiceStatus(this, isRunning)
        updateButtonState()
    }

    private fun updateButtonState() {
        toggleButton.text = if (WallpaperService.isServiceRunning) "Durdur" else "Başlat"
        toggleButton.setBackgroundResource(
            if (WallpaperService.isServiceRunning) R.drawable.rounded_button_outline
            else R.drawable.rounded_button
        )
    }

    override fun onResume() {
        super.onResume()
        checkServiceStatus()
        updateButtonState()
    }

    private fun startService() {
        val selectedInterval = intervals[intervalSpinner.selectedItemPosition].second
        val intent = Intent(this, WallpaperService::class.java).apply {
            putExtra("interval", selectedInterval)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        WallpaperService.updateServiceStatus(this, true)
        updateButtonState()
    }

    private fun stopService() {
        stopService(Intent(this, WallpaperService::class.java))
        WallpaperService.updateServiceStatus(this, false)
        updateButtonState()
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == WallpaperService::class.java.name }
    }
}
