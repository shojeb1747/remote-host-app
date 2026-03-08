package com.remotecontrol.network

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

/**
 * Handles Socket.IO signaling communication with the server.
 * Manages room registration and WebRTC signal exchange.
 */
class SignalingClient(
    private val serverUrl: String,
    private val listener: SignalingListener
) {
    private lateinit var socket: Socket

    interface SignalingListener {
        fun onConnected()
        fun onRegistered(roomId: String)
        fun onViewerRequest(viewerId: String)
        fun onAnswerReceived(fromId: String, sdp: String)
        fun onIceCandidateReceived(fromId: String, candidate: JSONObject)
        fun onViewerDisconnected(viewerId: String)
        fun onError(message: String)
    }

    fun connect(roomId: String) {
        val options = IO.Options.builder()
            .setReconnection(true)
            .build()

        socket = IO.socket(URI.create(serverUrl), options)

        socket.on(Socket.EVENT_CONNECT) {
            listener.onConnected()
            // Register as host
            val data = JSONObject().put("roomId", roomId)
            socket.emit("host:register", data)
        }

        socket.on("host:registered") { args ->
            val data = args[0] as JSONObject
            listener.onRegistered(data.getString("roomId"))
        }

        socket.on("host:error") { args ->
            val data = args[0] as JSONObject
            listener.onError(data.getString("message"))
        }

        // Viewer wants to connect → we need to send SDP Offer
        socket.on("viewer:request") { args ->
            val data = args[0] as JSONObject
            listener.onViewerRequest(data.getString("viewerId"))
        }

        // Receive SDP Answer from viewer
        socket.on("signal:answer") { args ->
            val data = args[0] as JSONObject
            listener.onAnswerReceived(
                data.getString("fromId"),
                data.getString("sdp")
            )
        }

        // Receive ICE candidate from viewer
        socket.on("signal:ice") { args ->
            val data = args[0] as JSONObject
            listener.onIceCandidateReceived(
                data.getString("fromId"),
                data.getJSONObject("candidate")
            )
        }

        socket.on("viewer:disconnected") { args ->
            val data = args[0] as JSONObject
            listener.onViewerDisconnected(data.getString("viewerId"))
        }

        socket.connect()
    }

    fun sendOffer(targetId: String, sdp: String) {
        val data = JSONObject()
            .put("targetId", targetId)
            .put("sdp", sdp)
        socket.emit("signal:offer", data)
    }

    fun sendIceCandidate(targetId: String, candidate: JSONObject) {
        val data = JSONObject()
            .put("targetId", targetId)
            .put("candidate", candidate)
        socket.emit("signal:ice", data)
    }

    fun disconnect() {
        if (::socket.isInitialized) socket.disconnect()
    }
}
