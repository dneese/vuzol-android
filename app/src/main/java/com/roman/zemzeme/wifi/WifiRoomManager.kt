package com.roman.zemzeme.wifi

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * The offline room of the phone.
 *
 * The room is remembered, so the code and the password have to be typed only
 * once. The room stays alive while the user looks at other chats, and it
 * shows up in the chat list like any other conversation. Leaving the room
 * forgets it and removes it from the list.
 */
object WifiRoomManager {

    enum class Mode { DIRECT, LAN }

    data class SavedRoom(
        val code: String,
        val password: String,
        val mode: Mode
    )

    private const val PREFS = "wifi_room_store"
    private const val KEY_CODE = "code"
    private const val KEY_PASSWORD = "password"
    private const val KEY_MODE = "mode"
    private const val MAX_MESSAGES = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _savedRoom = MutableStateFlow<SavedRoom?>(null)
    val savedRoom: StateFlow<SavedRoom?> = _savedRoom.asStateFlow()

    private val _activeMode = MutableStateFlow<Mode?>(null)
    val activeMode: StateFlow<Mode?> = _activeMode.asStateFlow()

    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _peers = MutableStateFlow(0)
    val peers: StateFlow<Int> = _peers.asStateFlow()

    private val _messages = MutableStateFlow<List<RoomMessage>>(emptyList())
    val messages: StateFlow<List<RoomMessage>> = _messages.asStateFlow()

    private val _callSignals = MutableSharedFlow<RoomProtocol.CallSignal>(extraBufferCapacity = 8)
    internal val callSignals: SharedFlow<RoomProtocol.CallSignal> = _callSignals.asSharedFlow()

    private var appContext: Context? = null
    private var direct: WifiDirectTransport? = null
    private var lan: LanTransport? = null

    /** Reads the remembered room, safe to call many times. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CODE, null)
        if (!code.isNullOrBlank()) {
            val mode = runCatching {
                Mode.valueOf(prefs.getString(KEY_MODE, Mode.LAN.name) ?: Mode.LAN.name)
            }.getOrDefault(Mode.LAN)
            _savedRoom.value = SavedRoom(code, prefs.getString(KEY_PASSWORD, "") ?: "", mode)
        }
    }

    /** The code of the room, empty when no room is remembered. */
    fun savedCode(): String = _savedRoom.value?.code ?: ""

    /** The id of this phone inside the room, empty when the room is off. */
    val localPeerId: String
        get() = when (_activeMode.value) {
            Mode.DIRECT -> direct?.localPeerId ?: ""
            Mode.LAN -> lan?.localPeerId ?: ""
            null -> ""
        }

    /** Starts the room and remembers it. */
    fun join(context: Context, code: String, password: String?, mode: Mode, nickname: String) {
        init(context)
        val cleanCode = code.trim().lowercase()
        if (cleanCode.isEmpty()) return
        stopTransports()

        val saved = SavedRoom(cleanCode, password.orEmpty(), mode)
        _savedRoom.value = saved
        save(saved)

        when (mode) {
            Mode.DIRECT -> {
                val transport = WifiDirectTransport(context.applicationContext, scope)
                transport.onMessage = { message -> addMessage(message) }
                transport.onStatusChanged = { value -> _status.value = value }
                transport.onPeersChanged = { value -> _peers.value = value }
                direct = transport
                _activeMode.value = Mode.DIRECT
                transport.startRoom(cleanCode, password?.ifBlank { null }, asHost = true)
            }
            Mode.LAN -> {
                val transport = LanTransport(context.applicationContext, scope)
                transport.onMessage = { message -> addMessage(message) }
                transport.onStatusChanged = { value -> _status.value = value }
                transport.onPeersChanged = { value -> _peers.value = value }
                transport.onCallSignal = { signal -> _callSignals.tryEmit(signal) }
                lan = transport
                _activeMode.value = Mode.LAN
                transport.start(cleanCode, password?.ifBlank { null }, nickname)
            }
        }
    }

    /** Joins the remembered room again, for example when the sheet opens. */
    fun rejoin(context: Context, nickname: String) {
        val room = _savedRoom.value ?: return
        if (_activeMode.value != null) return
        join(context, room.code, room.password.ifBlank { null }, room.mode, nickname)
    }

    /** Leaves the room and forgets it, the room leaves the chat list. */
    fun leave(context: Context) {
        init(context)
        stopTransports()
        _savedRoom.value = null
        _activeMode.value = null
        _messages.value = emptyList()
        _status.value = "stopped"
        _peers.value = 0
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_CODE)
            .remove(KEY_PASSWORD)
            .remove(KEY_MODE)
            .apply()
    }

    /** Sends a text message to the room. */
    fun sendText(text: String, nickname: String): Boolean = when (_activeMode.value) {
        Mode.DIRECT -> direct?.sendText(text, direct?.localPeerId ?: "", nickname) ?: false
        Mode.LAN -> lan?.sendText(text, nickname) ?: false
        null -> false
    }

    /** Sends a file to the room. */
    fun sendFile(uri: Uri, nickname: String): Boolean =
        lan?.sendFile(uri, nickname) ?: false

    /** The phones of the room that can be called. */
    internal fun peersForCall(): List<LanTransport.PeerInfo> = lan?.peers() ?: emptyList()

    /** Starts a call to one phone of the room. */
    internal fun startCall(peer: LanTransport.PeerInfo): Boolean = lan?.startCall(peer) ?: false

    /** Answers a call of the room. */
    internal fun acceptCall(signal: RoomProtocol.CallSignal): Boolean = lan?.acceptCall(signal) ?: false

    /** Rejects an incoming call. */
    fun declineCall(callId: String) {
        lan?.declineCall(callId)
    }

    /** Ends the running call. */
    fun endCall() {
        lan?.endCall()
    }

    private fun addMessage(message: RoomMessage) {
        _messages.value = (_messages.value + message).takeLast(MAX_MESSAGES)
    }

    private fun stopTransports() {
        direct?.stop()
        direct = null
        lan?.stop()
        lan = null
        _activeMode.value = null
        _peers.value = 0
    }

    private fun save(room: SavedRoom) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_CODE, room.code)
            ?.putString(KEY_PASSWORD, room.password)
            ?.putString(KEY_MODE, room.mode.name)
            ?.apply()
    }
}
