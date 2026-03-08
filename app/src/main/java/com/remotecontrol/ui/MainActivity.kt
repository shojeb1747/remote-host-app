package com.remotecontrol.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.remotecontrol.databinding.ActivityMainBinding
import com.remotecontrol.network.SignalingClient
import com.remotecontrol.network.WebRTCManager
import com.remotecontrol.service.RemoteControlAccessibilityService
import com.remotecontrol.service.ScreenCaptureService
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity(), SignalingClient.SignalingListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var signalingClient: SignalingClient
    private var webRTCManager: WebRTCManager? = null

    // Replace with your server IP
    private val SERVER_URL = "https://remote-server-production-4b0d.up.railway.app"

    // Unique room ID for this host device
    private val roomId = UUID.randomUUID().toString().take(8).uppercase()

    private val SCREEN_CAPTURE_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show Room ID
        binding.tvRoomId.text = "Room ID: $roomId"

        binding.btnStartHosting.setOnClickListener {
            checkAccessibilityAndStart()
        }

        binding.btnStopHosting.setOnClickListener {
            stopHosting()
        }

        signalingClient = SignalingClient(SERVER_URL, this)
    }

    private fun checkAccessibilityAndStart() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this,
                "Please enable Accessibility Service for Remote Control",
                Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        requestScreenCapture()
    }

    private fun isAccessibilityEnabled(): Boolean {
        return RemoteControlAccessibilityService.instance != null
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            SCREEN_CAPTURE_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            // Start screen capture service
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            startForegroundService(serviceIntent)

            // Connect to signaling server
            signalingClient.connect(roomId)
            updateStatus("Connecting to server...")
        }
    }

    private fun stopHosting() {
        webRTCManager?.close()
        signalingClient.disconnect()
        stopService(Intent(this, ScreenCaptureService::class.java))
        updateStatus("Stopped")
        binding.btnStartHosting.isEnabled = true
        binding.btnStopHosting.isEnabled = false
    }

    // ─── SignalingListener Callbacks ──────────────────────────────────────────

    override fun onConnected() {
        runOnUiThread { updateStatus("Connected. Waiting for viewer...") }
    }

    override fun onRegistered(roomId: String) {
        runOnUiThread {
            updateStatus("✅ Hosting active\nShare Room ID: $roomId")
            binding.btnStartHosting.isEnabled = false
            binding.btnStopHosting.isEnabled = true
        }
    }

    override fun onViewerRequest(viewerId: String) {
        runOnUiThread { updateStatus("Viewer connecting: $viewerId") }

        // Wait for screen capture service to be ready
        val videoTrack = ScreenCaptureService.instance?.videoTrack ?: return

        webRTCManager = WebRTCManager(this, signalingClient, viewerId)
        webRTCManager?.createPeerConnection(videoTrack)
        webRTCManager?.createOffer()
    }

    override fun onAnswerReceived(fromId: String, sdp: String) {
        webRTCManager?.setRemoteAnswer(sdp)
        runOnUiThread { updateStatus("🔗 Viewer connected!") }
    }

    override fun onIceCandidateReceived(fromId: String, candidate: JSONObject) {
        webRTCManager?.addIceCandidate(candidate)
    }

    override fun onViewerDisconnected(viewerId: String) {
        webRTCManager?.close()
        webRTCManager = null
        runOnUiThread { updateStatus("Viewer disconnected. Waiting...") }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
            updateStatus("Error: $message")
        }
    }

    private fun updateStatus(text: String) {
        binding.tvStatus.text = text
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHosting()
    }
}
