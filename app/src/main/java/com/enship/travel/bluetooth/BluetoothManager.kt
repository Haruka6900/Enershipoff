package com.enship.travel.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.enship.travel.protocol.AlarmCode
import com.enship.travel.protocol.FrameAssembler
import com.enship.travel.protocol.FrameParser
import com.enship.travel.protocol.ParseResult
import com.enship.travel.protocol.TelemetrySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.UUID

/**
 * Coeur de la couche Bluetooth — gere une liaison RFCOMM (SPP) fiable avec
 * le HC-06.
 *
 * Conçu autour des limites reelles du HC-06 :
 *  - UUID SPP standard 00001101-... (le HC-06 n'expose que ce service) ;
 *  - `cancelDiscovery()` systematique avant `connect()` (sinon echec) ;
 *  - methode de secours par reflexion (`createRfcommSocket(1)`) frequemment
 *    necessaire sur de nombreux telephones ;
 *  - reconnexion automatique avec backoff exponentiel plafonne ;
 *  - watchdog : si plus aucune trame `$DATA` n'arrive pendant
 *    [DATA_TIMEOUT_MS], la liaison est consideree morte et relancee ;
 *  - lecture par paquets reassemblee via [FrameAssembler] ;
 *  - aucune exception propagee : toute erreur => reconnexion ou ERROR.
 *
 * Singleton : partage entre l'UI (ViewModel) et le service de premier plan.
 */
object BluetoothManager {

    private const val TAG = "EnerShipBT"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private const val DATA_TIMEOUT_MS = 4_000L
    private const val RECONNECT_BASE_MS = 1_500L
    private const val RECONNECT_MAX_MS = 15_000L
    private const val READ_BUFFER = 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Etat expose ------------------------------------------------------
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _quality = MutableStateFlow(LinkQuality.NONE)
    val quality: StateFlow<LinkQuality> = _quality.asStateFlow()

    private val _telemetry = MutableStateFlow<TelemetrySnapshot?>(null)
    val telemetry: StateFlow<TelemetrySnapshot?> = _telemetry.asStateFlow()

    private val _stats = MutableStateFlow(CommStats())
    val stats: StateFlow<CommStats> = _stats.asStateFlow()

    private val _connectedDevice = MutableStateFlow<PairedDevice?>(null)
    val connectedDevice: StateFlow<PairedDevice?> = _connectedDevice.asStateFlow()

    private val _statusMessage = MutableStateFlow("Deconnecte")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    /** Evenements alarme (consommes par la couche données pour historiser). */
    private val _alarms = MutableSharedFlow<ParseResult.Alarm>(extraBufferCapacity = 32)
    val alarms: SharedFlow<ParseResult.Alarm> = _alarms.asSharedFlow()

    /** Reponses ACK (consommees par l'ecran de configuration). */
    private val _acks = MutableSharedFlow<ParseResult.Ack>(extraBufferCapacity = 16)
    val acks: SharedFlow<ParseResult.Ack> = _acks.asSharedFlow()

    /** Configuration recue de l'Arduino (`$CONF`). */
    private val _conf = MutableSharedFlow<ParseResult.Conf>(extraBufferCapacity = 8)
    val conf: SharedFlow<ParseResult.Conf> = _conf.asSharedFlow()

    /** Lignes brutes (pour la console de debug / packet inspector). */
    private val _rawLog = MutableSharedFlow<RawLogEntry>(extraBufferCapacity = 256)
    val rawLog: SharedFlow<RawLogEntry> = _rawLog.asSharedFlow()

    // ---- Etat interne -----------------------------------------------------
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var input: InputStream? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var connected = false
    @Volatile private var userRequestedStop = false

    private var connectionJob: Job? = null
    private var watchdogJob: Job? = null
    private var targetAddress: String? = null
    private var reconnectAttempt = 0

    private val assembler = FrameAssembler()
    private var lastDataTimestamp = 0L
    private var intervalAccumulator = 0.0

    private val adapter: BluetoothAdapter?
        get() = BluetoothAdapter.getDefaultAdapter()

    val isBluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    // ---- API publique -----------------------------------------------------

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> = try {
        adapter?.bondedDevices?.map {
            PairedDevice(it.name ?: "Inconnu", it.address)
        }?.sortedBy { it.name } ?: emptyList()
    } catch (e: SecurityException) {
        Log.w(TAG, "Permission BT manquante", e)
        emptyList()
    }

    /** Demarre une connexion (et la reconnexion auto) vers [address]. */
    fun connect(address: String) {
        userRequestedStop = false
        targetAddress = address
        reconnectAttempt = 0
        connectionJob?.cancel()
        connectionJob = scope.launch { connectLoop() }
    }

    /** Arret volontaire — desactive la reconnexion automatique. */
    fun disconnect() {
        userRequestedStop = true
        connectionJob?.cancel()
        watchdogJob?.cancel()
        closeSocket()
        updateState(ConnectionState.DISCONNECTED, "Deconnecte par l'utilisateur")
        _quality.value = LinkQuality.NONE
        _connectedDevice.value = null
    }

    /** Force une reconnexion immediate (bouton manuel). */
    fun reconnectNow() {
        val addr = targetAddress ?: return
        reconnectAttempt = 0
        connectionJob?.cancel()
        closeSocket()
        connect(addr)
    }

    /** Envoie une commande brute (deja terminee par CRLF). */
    fun send(command: String): Boolean {
        val out = output
        if (!connected || out == null) return false
        return try {
            val bytes = command.toByteArray(Charsets.UTF_8)
            out.write(bytes)
            out.flush()
            _stats.value = _stats.value.copy(
                bytesSent = _stats.value.bytesSent + bytes.size,
                commandsSent = _stats.value.commandsSent + 1,
            )
            emitRaw(RawLogEntry.Direction.OUT, command.trim())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Echec envoi commande", e)
            onLinkBroken("Echec envoi : ${e.message}")
            false
        }
    }

    fun resetStats() {
        _stats.value = CommStats(sessionStart = System.currentTimeMillis())
    }

    // ---- Boucle de connexion / reconnexion --------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun connectLoop() {
        val address = targetAddress ?: return
        while (scope.isActive && !userRequestedStop) {
            try {
                if (adapter?.isEnabled != true) {
                    updateState(ConnectionState.ERROR, "Bluetooth desactive")
                    delay(RECONNECT_BASE_MS)
                    continue
                }
                updateState(
                    if (reconnectAttempt == 0) ConnectionState.CONNECTING
                    else ConnectionState.RECONNECTING,
                    if (reconnectAttempt == 0) "Connexion..."
                    else "Reconnexion (tentative ${reconnectAttempt + 1})...",
                )

                val device = adapter?.getRemoteDevice(address)
                    ?: throw IllegalStateException("Equipement introuvable")

                openSocket(device)
                // Connexion reussie : lecture bloquante jusqu'a rupture.
                _connectedDevice.value = PairedDevice(
                    device.name ?: "HC-06", device.address,
                )
                resetStats()
                _stats.value = _stats.value.copy(reconnects = if (reconnectAttempt > 0) _stats.value.reconnects + 1 else 0)
                reconnectAttempt = 0
                updateState(ConnectionState.CONNECTED, "Connecte")
                startWatchdog()
                readLoop()
            } catch (e: Exception) {
                Log.w(TAG, "Connexion echouee", e)
                closeSocket()
            }

            if (userRequestedStop) break

            // Backoff exponentiel plafonne avant nouvelle tentative.
            reconnectAttempt++
            val backoff = (RECONNECT_BASE_MS * (1L shl (reconnectAttempt - 1).coerceAtMost(4)))
                .coerceAtMost(RECONNECT_MAX_MS)
            updateState(ConnectionState.RECONNECTING, "Nouvelle tentative dans ${backoff / 1000}s")
            _quality.value = LinkQuality.NONE
            delay(backoff)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice) {
        adapter?.cancelDiscovery() // TOUJOURS annuler le scan avant connect.
        try {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            bindStreams(s)
        } catch (primary: Exception) {
            // Plan B : socket RFCOMM canal 1 via reflexion (frequent sur HC-06).
            closeSocket()
            val fallback = createReflectionSocket(device)
                ?: throw primary
            fallback.connect()
            bindStreams(fallback)
        }
    }

    private fun createReflectionSocket(device: BluetoothDevice): BluetoothSocket? = try {
        val method: Method = device.javaClass.getMethod(
            "createRfcommSocket", Int::class.javaPrimitiveType,
        )
        method.invoke(device, 1) as BluetoothSocket
    } catch (e: Exception) {
        Log.w(TAG, "Fallback reflexion indisponible", e)
        null
    }

    private fun bindStreams(s: BluetoothSocket) {
        socket = s
        input = s.inputStream
        output = s.outputStream
        connected = true
        assembler.reset()
        lastDataTimestamp = System.currentTimeMillis()
    }

    // ---- Boucle de lecture ------------------------------------------------

    private fun readLoop() {
        val stream = input ?: return
        val buf = ByteArray(READ_BUFFER)
        while (connected && scope.isActive) {
            val n = try {
                stream.read(buf)
            } catch (e: Exception) {
                if (connected) onLinkBroken("Lecture interrompue : ${e.message}")
                break
            }
            if (n <= 0) {
                if (n < 0) { onLinkBroken("Flux ferme"); break }
                continue
            }
            _stats.value = _stats.value.copy(bytesReceived = _stats.value.bytesReceived + n)
            val chunk = String(buf, 0, n, Charsets.UTF_8)
            for (line in assembler.append(chunk)) {
                handleLine(line)
            }
        }
    }

    private fun handleLine(line: String) {
        when (val result = FrameParser.parse(line)) {
            is ParseResult.Data -> {
                registerData(result.snapshot)
                emitRaw(RawLogEntry.Direction.IN, line)
            }
            is ParseResult.Alarm -> {
                _stats.value = _stats.value.copy(
                    framesReceived = _stats.value.framesReceived + 1,
                    alarmFrames = _stats.value.alarmFrames + 1,
                )
                _alarms.tryEmit(result)
                emitRaw(RawLogEntry.Direction.IN, line)
            }
            is ParseResult.Ack -> {
                _stats.value = _stats.value.copy(
                    framesReceived = _stats.value.framesReceived + 1,
                    ackFrames = _stats.value.ackFrames + 1,
                )
                _acks.tryEmit(result)
                emitRaw(RawLogEntry.Direction.IN, line)
            }
            is ParseResult.Conf -> {
                _stats.value = _stats.value.copy(
                    framesReceived = _stats.value.framesReceived + 1,
                    confFrames = _stats.value.confFrames + 1,
                )
                _conf.tryEmit(result)
                emitRaw(RawLogEntry.Direction.IN, line)
            }
            ParseResult.Pong -> {
                _stats.value = _stats.value.copy(
                    framesReceived = _stats.value.framesReceived + 1,
                    pongFrames = _stats.value.pongFrames + 1,
                )
                emitRaw(RawLogEntry.Direction.IN, line)
            }
            is ParseResult.Unknown -> {
                _stats.value = _stats.value.copy(
                    framesReceived = _stats.value.framesReceived + 1,
                    unknownFrames = _stats.value.unknownFrames + 1,
                )
                emitRaw(RawLogEntry.Direction.IN, line)
            }
            is ParseResult.Invalid -> {
                _stats.value = _stats.value.withInvalid(result.reason)
                emitRaw(RawLogEntry.Direction.ERROR, "${result.reason}: ${result.raw}")
            }
        }
    }

    private fun registerData(snapshot: TelemetrySnapshot) {
        val now = snapshot.receivedAt
        val interval = if (lastDataTimestamp > 0) (now - lastDataTimestamp) else 0L
        lastDataTimestamp = now

        // Moyenne lissee (EWMA) de l'intervalle entre trames.
        intervalAccumulator =
            if (intervalAccumulator == 0.0 && interval > 0) interval.toDouble()
            else if (interval > 0) intervalAccumulator * 0.8 + interval * 0.2
            else intervalAccumulator

        _stats.value = _stats.value.copy(
            framesReceived = _stats.value.framesReceived + 1,
            dataFrames = _stats.value.dataFrames + 1,
            lastDataAt = now,
            avgIntervalMs = intervalAccumulator,
        )
        _telemetry.value = snapshot
        updateQuality()
    }

    private fun updateQuality() {
        val s = _stats.value
        _quality.value = when {
            !connected -> LinkQuality.NONE
            s.dataFrames < 2 -> LinkQuality.GOOD
            s.errorRate > 0.25 -> LinkQuality.POOR
            s.avgIntervalMs > 1500 -> LinkQuality.POOR
            s.avgIntervalMs in 700.0..1500.0 -> LinkQuality.GOOD
            else -> LinkQuality.EXCELLENT
        }
    }

    // ---- Watchdog ---------------------------------------------------------

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (connected && scope.isActive) {
                delay(1_000)
                val since = System.currentTimeMillis() - lastDataTimestamp
                if (since > DATA_TIMEOUT_MS) {
                    _quality.value = LinkQuality.STALE
                    onLinkBroken("Aucune donnee depuis ${since / 1000}s")
                    break
                }
            }
        }
    }

    // ---- Gestion des ruptures --------------------------------------------

    private fun onLinkBroken(reason: String) {
        if (!connected) return
        connected = false
        watchdogJob?.cancel()
        closeSocket()
        if (!userRequestedStop) {
            updateState(ConnectionState.RECONNECTING, reason)
            _quality.value = LinkQuality.NONE
        }
    }

    private fun closeSocket() {
        connected = false
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null; output = null; socket = null
    }

    private fun updateState(state: ConnectionState, message: String) {
        _state.value = state
        _statusMessage.value = message
    }

    private fun emitRaw(direction: RawLogEntry.Direction, content: String) {
        _rawLog.tryEmit(RawLogEntry(direction, content))
    }
}
