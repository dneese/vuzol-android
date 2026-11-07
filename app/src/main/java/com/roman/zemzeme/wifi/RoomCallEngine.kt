package com.roman.zemzeme.wifi

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The voice of one call inside an offline room.
 *
 * The sound is captured with AudioRecord, cut into packets of 100 milliseconds
 * and sent over UDP. Every packet is encrypted with the key of the room, so
 * only the phones of the room hear the call. The other phone decrypts the
 * packets and plays them with AudioTrack.
 */
internal class RoomCallEngine(
    private val key: ByteArray,
    private val roomId: ByteArray
) {

    companion object {
        private const val TAG = "RoomCallEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val PACKET_SAMPLES = 1600
        private const val MAX_PACKET = 4096
    }

    private val running = AtomicBoolean(false)

    @Volatile
    private var destinationAddress: String = ""

    @Volatile
    private var destinationPort: Int = 0

    private var sendSocket: DatagramSocket? = null
    private var receiveSocket: DatagramSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    /** UDP port of this phone, the other phone sends the sound here. */
    var localPort: Int = 0
        private set

    fun setDestination(address: String, port: Int) {
        destinationAddress = address
        destinationPort = port
    }

    /** Starts the microphone and the speaker, returns the local UDP port. */
    fun start(): Int {
        if (!running.compareAndSet(false, true)) return localPort

        val receive = try {
            DatagramSocket(0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the audio socket: ${e.message}")
            running.set(false)
            return 0
        }
        receiveSocket = receive
        localPort = receive.localPort

        try {
            sendSocket = DatagramSocket()
        } catch (_: Exception) {
        }

        startPlayer()
        startRecorder()
        Thread({ receiveLoop(receive) }, "vuzol-call-receive").start()
        Thread({ captureLoop() }, "vuzol-call-capture").start()
        return localPort
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { sendSocket?.close() }
        sendSocket = null
        runCatching { receiveSocket?.close() }
        receiveSocket = null
        localPort = 0
    }

    private fun startRecorder() {
        try {
            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            val bufferSize = maxOf(minimum, PACKET_SAMPLES * 2 * 4)
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return
            }
            record.startRecording()
            recorder = record
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the microphone: ${e.message}")
        }
    }

    private fun startPlayer() {
        try {
            val minimum = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            val bufferSize = maxOf(minimum, PACKET_SAMPLES * 2 * 4)
            val track = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return
            }
            track.play()
            player = track
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the speaker: ${e.message}")
        }
    }

    private fun captureLoop() {
        val record = recorder ?: return
        val samples = ShortArray(PACKET_SAMPLES)
        val bytes = ByteArray(PACKET_SAMPLES * 2)
        while (running.get()) {
            val read = try {
                record.read(samples, 0, samples.size)
            } catch (_: Exception) {
                break
            }
            if (read <= 0) continue
            val port = destinationPort
            val address = destinationAddress
            val socket = sendSocket
            if (socket == null || port <= 0 || address.isEmpty()) continue

            val length = read * 2
            var index = 0
            while (index < length) {
                val value = samples[index / 2].toInt()
                bytes[index] = (value and 0xff).toByte()
                bytes[index + 1] = ((value shr 8) and 0xff).toByte()
                index += 2
            }
            val payload = if (length == bytes.size) bytes else bytes.copyOf(length)
            val cipher = RoomCrypto.encrypt(key, roomId, payload) ?: continue
            try {
                socket.send(DatagramPacket(cipher, cipher.size, InetAddress.getByName(address), port))
            } catch (_: Exception) {
            }
        }
    }

    private fun receiveLoop(socket: DatagramSocket) {
        val buffer = ByteArray(MAX_PACKET)
        while (running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: Exception) {
                break
            }
            val cipher = packet.data.copyOf(packet.length)
            val payload = RoomCrypto.decrypt(key, roomId, cipher) ?: continue
            val track = player ?: continue
            try {
                track.write(payload, 0, payload.size)
            } catch (_: Exception) {
            }
        }
    }
}
