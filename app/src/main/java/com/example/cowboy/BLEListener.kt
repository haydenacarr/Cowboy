package com.example.cowboy

interface BLEListener {
    fun dataReceived(data: ByteArray)
}