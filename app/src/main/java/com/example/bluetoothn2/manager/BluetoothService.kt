package com.example.bluetoothn2.manager

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class BluetoothService : Service() {
    private val TAG = "BluetoothService"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BluetoothService started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BluetoothService destroyed")
    }
}