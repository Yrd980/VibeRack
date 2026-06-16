package com.viberack.app.core.ble.smart

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

@SuppressLint("MissingPermission")
class SmartChassisGattClient(
    private val appContext: Context,
    private val hasBluetoothPermission: () -> Boolean
) : SmartChassisClient {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    }
    private val operationMutex = Mutex()

    private var gatt: BluetoothGatt? = null
    private var bindingControlPoint: BluetoothGattCharacteristic? = null
    private var tableInfoCharacteristic: BluetoothGattCharacteristic? = null
    private var lightCommand: BluetoothGattCharacteristic? = null
    private var lightStatus: BluetoothGattCharacteristic? = null
    private var connectedDevice: SmartChassisDevice? = null
    private var pendingIdentity: SmartChassisDevice? = null

    private var pendingConnect: CompletableDeferred<SmartChassisClientResult<SmartChassisDevice>>? = null
    private var pendingBindingOp: PendingBindingOp? = null
    private var pendingReadAll: PendingReadAll? = null
    private var pendingTableInfoRead: CompletableDeferred<SmartChassisClientResult<SmartChassisTableInfo>>? = null
    private var pendingLightStatusRead: CompletableDeferred<SmartChassisClientResult<SmartChassisLightStatus>>? = null
    private val pendingDescriptors = ArrayDeque<BluetoothGattDescriptor>()
    private var pendingDescriptorSetup: CompletableDeferred<Boolean>? = null
    private var bondReceiver: BroadcastReceiver? = null
    private var bondingDeviceAddress: String? = null

    private val _discoveredChassis = MutableStateFlow<List<SmartChassisDevice>>(emptyList())
    override val discoveredChassis: StateFlow<List<SmartChassisDevice>> = _discoveredChassis.asStateFlow()

    private val _connectionState = MutableStateFlow(SmartChassisConnectionState())
    override val connectionState: StateFlow<SmartChassisConnectionState> = _connectionState.asStateFlow()

    private val _tableInfoUpdates = MutableStateFlow<SmartChassisTableInfo?>(null)
    override val tableInfoUpdates: StateFlow<SmartChassisTableInfo?> = _tableInfoUpdates.asStateFlow()
    private val bindingNotificationHandler = SmartChassisGattBindingNotificationHandler(
        getPendingReadAll = { pendingReadAll },
        setPendingReadAll = { pendingReadAll = it },
        getPendingBindingOp = { pendingBindingOp },
        setPendingBindingOp = { pendingBindingOp = it }
    )

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnect("GATT connection failed: $status")
                closeGatt()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val mtuRequested = runCatching { gatt.requestMtu(64) }.getOrDefault(false)
                    if (!mtuRequested && !gatt.discoverServices()) {
                        failConnect("Service discovery failed to start")
                        closeGatt()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = SmartChassisConnectionState()
                    failPendingOperations("smart chassis disconnected")
                    closeGatt()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!gatt.discoverServices()) {
                failConnect("Service discovery failed to start")
                closeGatt()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnect("Service discovery failed: $status")
                closeGatt()
                return
            }
            if (!resolveCharacteristics(gatt.services)) {
                failConnect("Smart chassis GATT services are missing")
                closeGatt()
                return
            }
            val descriptorReady = CompletableDeferred<Boolean>()
            pendingDescriptorSetup = descriptorReady
            queueNotificationDescriptor(bindingControlPoint)
            queueNotificationDescriptor(tableInfoCharacteristic)
            queueNotificationDescriptor(lightStatus)
            if (pendingDescriptors.isEmpty()) {
                completeConnectWhenBonded()
            } else {
                writeNextDescriptor(gatt)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                pendingDescriptorSetup?.complete(false)
                pendingDescriptorSetup = null
                failConnect("Notification setup failed: $status")
                closeGatt()
                return
            }
            if (pendingDescriptors.isEmpty()) {
                pendingDescriptorSetup?.complete(true)
                pendingDescriptorSetup = null
                completeConnectWhenBonded()
            } else {
                writeNextDescriptor(gatt)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharacteristicRead(characteristic.uuid, value, status)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            handleCharacteristicRead(characteristic.uuid, characteristic.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicChanged(characteristic.uuid, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == SmartChassisUuids.lightCommand) {
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                pendingBindingOp?.deferred?.complete(
                    SmartChassisClientResult.Failure(
                        message = "Binding command write failed: $status",
                        op = pendingBindingOp?.op
                    )
                )
                pendingBindingOp = null
            }
        }
    }

    override suspend fun startScan(): SmartChassisClientResult<List<SmartChassisDevice>> {
        return SmartChassisClientResult.Failure("Use SmartChassisScanner for Android BLE scans")
    }

    override suspend fun stopScan(): SmartChassisClientResult<Unit> {
        return SmartChassisClientResult.Success(Unit)
    }

    override suspend fun connect(address: String): SmartChassisClientResult<SmartChassisDevice> {
        if (!hasBluetoothPermission()) {
            return SmartChassisClientResult.Failure("Bluetooth connect permission is required")
        }
        val adapter = bluetoothAdapter ?: return SmartChassisClientResult.Failure("Bluetooth is not supported")
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            return SmartChassisClientResult.Failure("Bluetooth is disabled")
        }
        val normalizedAddress = address.trim().uppercase()
        val remoteDevice = runCatching { adapter.getRemoteDevice(normalizedAddress) }
            .getOrElse { return SmartChassisClientResult.Failure("Invalid Bluetooth address: $address") }

        disconnect()
        val deferred = CompletableDeferred<SmartChassisClientResult<SmartChassisDevice>>()
        pendingConnect = deferred
        pendingIdentity = discoveredChassis.value.firstOrNull {
            it.address.equals(normalizedAddress, ignoreCase = true)
        }
        _connectionState.value = SmartChassisConnectionState(
            phase = SmartChassisConnectionPhase.CONNECTING,
            message = "Connecting to $normalizedAddress"
        )
        val gattClient = connectGatt(remoteDevice)
        gatt = gattClient
        return withBleTimeout("Connect timed out", CONNECT_TIMEOUT_MS) {
            deferred.await()
        }
    }

    override suspend fun disconnect(): SmartChassisClientResult<Unit> {
        _connectionState.value = _connectionState.value.copy(phase = SmartChassisConnectionPhase.DISCONNECTING)
        failPendingOperations("smart chassis disconnected")
        closeGatt()
        _connectionState.value = SmartChassisConnectionState()
        return SmartChassisClientResult.Success(Unit)
    }

    override suspend fun readTableInfo(): SmartChassisClientResult<SmartChassisTableInfo> {
        return operationMutex.withLock {
            val characteristic = tableInfoCharacteristic
                ?: return@withLock SmartChassisClientResult.Failure("Table Info characteristic is unavailable")
            val gattClient = gatt ?: return@withLock SmartChassisClientResult.Failure("smart chassis is not connected")
            val deferred = CompletableDeferred<SmartChassisClientResult<SmartChassisTableInfo>>()
            pendingTableInfoRead = deferred
            if (!gattClient.readCharacteristic(characteristic)) {
                pendingTableInfoRead = null
                return@withLock SmartChassisClientResult.Failure("Table Info read failed to start")
            }
            withBleTimeout("Table Info read timed out") { deferred.await() }
        }
    }

    override suspend fun readOne(slot: Int): SmartChassisClientResult<SmartChassisSlotRecord> {
        return when (val result = sendBindingCommand(
            op = SmartChassisBindingOp.READ_ONE,
            payload = SmartChassisCodec.encodeReadOne(slot)
        )) {
            is SmartChassisClientResult.Success -> {
                val record = SmartChassisCodec.parseSlotRecord(result.value)
                    ?: return SmartChassisClientResult.Failure("Invalid READ_ONE record", SmartChassisBindingOp.READ_ONE)
                SmartChassisClientResult.Success(record, SmartChassisBindingOp.READ_ONE)
            }
            is SmartChassisClientResult.Failure -> result
        }
    }

    override suspend fun readAll(): SmartChassisClientResult<SmartChassisTableSnapshot> {
        val recordsResult = operationMutex.withLock {
            val characteristic = bindingControlPoint
                ?: return@withLock SmartChassisClientResult.Failure("Binding Control Point is unavailable")
            val gattClient = gatt ?: return@withLock SmartChassisClientResult.Failure("smart chassis is not connected")
            val deferred = CompletableDeferred<SmartChassisClientResult<List<SmartChassisSlotRecord>>>()
            pendingReadAll = PendingReadAll(deferred = deferred)
            if (!writeCharacteristic(gattClient, characteristic, SmartChassisCodec.encodeReadAll())) {
                pendingReadAll = null
                return@withLock SmartChassisClientResult.Failure(
                    message = "READ_ALL write failed to start",
                    op = SmartChassisBindingOp.READ_ALL
                )
            }
            withBleTimeout("READ_ALL timed out") { deferred.await() }
        }
        return when (recordsResult) {
            is SmartChassisClientResult.Success -> {
                when (val infoResult = readTableInfo()) {
                    is SmartChassisClientResult.Success -> {
                        val records = recordsResult.value
                        val validationError = SmartChassisReadAllValidator.validate(records, infoResult.value)
                        if (validationError != null) {
                            SmartChassisClientResult.Failure(
                                message = validationError,
                                op = SmartChassisBindingOp.READ_ALL,
                                status = SmartChassisBindingStatus.ERR_CRC
                            )
                        } else {
                            SmartChassisClientResult.Success(
                                value = SmartChassisTableSnapshot(
                                    records = records,
                                    tableInfo = infoResult.value
                                ),
                                op = SmartChassisBindingOp.READ_ALL
                            )
                        }
                    }
                    is SmartChassisClientResult.Failure -> infoResult
                }
            }
            is SmartChassisClientResult.Failure -> recordsResult
        }
    }

    override suspend fun writeOne(record: SmartChassisSlotRecord): SmartChassisClientResult<SmartChassisTableInfo> {
        val payload = SmartChassisGattBindingPayloads.encode {
            SmartChassisCodec.encodeWriteOne(SmartChassisCodec.encodeSlotRecordForTable(record))
        } ?: return SmartChassisGattBindingPayloads.encodeFailure(SmartChassisBindingOp.WRITE_ONE)
        return writeAndRefreshTableInfo(SmartChassisBindingOp.WRITE_ONE, payload)
    }

    override suspend fun clearOne(slot: Int): SmartChassisClientResult<SmartChassisTableInfo> {
        val payload = SmartChassisGattBindingPayloads.encode {
            SmartChassisCodec.encodeClearOne(slot)
        } ?: return SmartChassisGattBindingPayloads.encodeFailure(SmartChassisBindingOp.CLEAR_ONE)
        return writeAndRefreshTableInfo(SmartChassisBindingOp.CLEAR_ONE, payload)
    }

    override suspend fun insertAt(
        slot: Int,
        record: SmartChassisSlotRecord
    ): SmartChassisClientResult<SmartChassisTableInfo> {
        val payload = SmartChassisGattBindingPayloads.encode {
            SmartChassisCodec.encodeInsertAt(slot, SmartChassisCodec.encodeSlotRecordForTable(record))
        } ?: return SmartChassisGattBindingPayloads.encodeFailure(SmartChassisBindingOp.INSERT_AT)
        return writeAndRefreshTableInfo(SmartChassisBindingOp.INSERT_AT, payload)
    }

    override suspend fun removeAt(slot: Int): SmartChassisClientResult<SmartChassisTableInfo> {
        val payload = SmartChassisGattBindingPayloads.encode {
            SmartChassisCodec.encodeRemoveAt(slot)
        } ?: return SmartChassisGattBindingPayloads.encodeFailure(SmartChassisBindingOp.REMOVE_AT)
        return writeAndRefreshTableInfo(SmartChassisBindingOp.REMOVE_AT, payload)
    }

    override suspend fun moveBlock(
        from: Int,
        to: Int,
        length: Int
    ): SmartChassisClientResult<SmartChassisTableInfo> {
        val payload = SmartChassisGattBindingPayloads.encode {
            SmartChassisCodec.encodeMoveBlock(from, to, length)
        } ?: return SmartChassisGattBindingPayloads.encodeFailure(SmartChassisBindingOp.MOVE_BLOCK)
        return writeAndRefreshTableInfo(SmartChassisBindingOp.MOVE_BLOCK, payload)
    }

    override suspend fun setQuantity(slot: Int, quantity: Int): SmartChassisClientResult<SmartChassisTableInfo> {
        val payload = SmartChassisGattBindingPayloads.encode {
            SmartChassisCodec.encodeSetQuantity(slot, quantity)
        } ?: return SmartChassisGattBindingPayloads.encodeFailure(SmartChassisBindingOp.SET_QTY)
        return writeAndRefreshTableInfo(SmartChassisBindingOp.SET_QTY, payload)
    }

    override suspend fun sendLightCommand(
        command: SmartChassisLightCommand
    ): SmartChassisClientResult<SmartChassisLightStatus> {
        val writeResult = operationMutex.withLock {
            val characteristic = lightCommand
                ?: return@withLock SmartChassisClientResult.Failure("Light Command characteristic is unavailable")
            val gattClient = gatt ?: return@withLock SmartChassisClientResult.Failure("smart chassis is not connected")
            val payload = runCatching { SmartChassisCodec.encodeLightCommand(command) }
                .getOrElse { throwable ->
                    return@withLock SmartChassisClientResult.Failure(
                        throwable.message ?: "Invalid light command"
                    )
                }
            if (!writeCharacteristic(
                    gattClient,
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            ) {
                return@withLock SmartChassisClientResult.Failure("Light command write failed to start")
            }
            SmartChassisClientResult.Success(Unit)
        }
        return if (writeResult is SmartChassisClientResult.Failure) {
            writeResult
        } else {
            readLightStatus()
        }
    }



    private suspend fun writeAndRefreshTableInfo(
        op: SmartChassisBindingOp,
        payload: ByteArray
    ): SmartChassisClientResult<SmartChassisTableInfo> {
        return when (val writeResult = sendBindingCommand(op, payload)) {
            is SmartChassisClientResult.Success -> readTableInfo()
            is SmartChassisClientResult.Failure -> writeResult
        }
    }

    private suspend fun sendBindingCommand(
        op: SmartChassisBindingOp,
        payload: ByteArray
    ): SmartChassisClientResult<ByteArray> {
        var attempt = 0
        while (true) {
            val result = sendBindingCommandOnce(op, payload)
            if (
                result is SmartChassisClientResult.Failure &&
                result.status == SmartChassisBindingStatus.ERR_FLASH_BUSY &&
                attempt < FLASH_BUSY_MAX_RETRIES
            ) {
                attempt++
                delay(FLASH_BUSY_RETRY_DELAY_MS)
                continue
            }
            return result
        }
    }

    private suspend fun sendBindingCommandOnce(
        op: SmartChassisBindingOp,
        payload: ByteArray
    ): SmartChassisClientResult<ByteArray> {
        return operationMutex.withLock {
            val characteristic = bindingControlPoint
                ?: return@withLock SmartChassisClientResult.Failure("Binding Control Point is unavailable", op)
            val gattClient = gatt ?: return@withLock SmartChassisClientResult.Failure("smart chassis is not connected", op)
            val deferred = CompletableDeferred<SmartChassisClientResult<ByteArray>>()
            pendingBindingOp = PendingBindingOp(op = op, deferred = deferred)
            if (!writeCharacteristic(gattClient, characteristic, payload)) {
                pendingBindingOp = null
                return@withLock SmartChassisClientResult.Failure("Binding command write failed to start", op)
            }
            withBleTimeout("Binding command timed out") { deferred.await() }
        }
    }

    private fun connectGatt(device: BluetoothDevice): BluetoothGatt {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, callback)
        }
    }

    private fun resolveCharacteristics(services: List<BluetoothGattService>): Boolean {
        val bindingService = services.firstOrNull { it.uuid == SmartChassisUuids.bindingService }
        val lightService = services.firstOrNull { it.uuid == SmartChassisUuids.lightService }
        bindingControlPoint = bindingService?.getCharacteristic(SmartChassisUuids.bindingControlPoint)
        tableInfoCharacteristic = bindingService?.getCharacteristic(SmartChassisUuids.tableInfo)
        lightCommand = lightService?.getCharacteristic(SmartChassisUuids.lightCommand)
        lightStatus = lightService?.getCharacteristic(SmartChassisUuids.lightStatus)
        return bindingControlPoint != null &&
            tableInfoCharacteristic != null &&
            lightCommand != null &&
            lightStatus != null
    }

    private fun queueNotificationDescriptor(characteristic: BluetoothGattCharacteristic?) {
        val gattClient = gatt ?: return
        if (characteristic == null) {
            return
        }
        gattClient.setCharacteristicNotification(characteristic, true)
        characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let { descriptor ->
            pendingDescriptors.addLast(descriptor)
        }
    }

    private fun writeNextDescriptor(gatt: BluetoothGatt) {
        val descriptor = pendingDescriptors.firstOrNull() ?: return
        when (val result = SmartChassisGattDescriptorWriter.writeEnableNotification(gatt, descriptor)) {
            DescriptorWriteStart.Started -> Unit
            is DescriptorWriteStart.Failed -> {
                pendingDescriptorSetup?.complete(false)
                failConnect(result.message)
            }
        }
        pendingDescriptors.removeFirst()
    }

    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Boolean {
        characteristic.writeType = writeType
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun handleCharacteristicRead(uuid: UUID, value: ByteArray, status: Int) {
        if (uuid == SmartChassisUuids.tableInfo) {
            val deferred = pendingTableInfoRead ?: return
            pendingTableInfoRead = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                deferred.complete(SmartChassisClientResult.Failure("Table Info read failed: $status"))
                return
            }
            val parsed = SmartChassisCodec.parseTableInfo(value)
            if (parsed != null) {
                _tableInfoUpdates.value = parsed
            }
            deferred.complete(
                if (parsed != null) {
                    SmartChassisClientResult.Success(parsed)
                } else {
                    SmartChassisClientResult.Failure("Invalid Table Info payload")
                }
            )
        } else if (uuid == SmartChassisUuids.lightStatus) {
            val deferred = pendingLightStatusRead ?: return
            pendingLightStatusRead = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                deferred.complete(SmartChassisClientResult.Failure("Light Status read failed: $status"))
                return
            }
            val parsed = SmartChassisCodec.parseLightStatus(value)
            deferred.complete(
                if (parsed != null) {
                    SmartChassisClientResult.Success(parsed)
                } else {
                    SmartChassisClientResult.Failure("Invalid Light Status payload")
                }
            )
        }
    }

    private fun handleCharacteristicChanged(uuid: UUID, value: ByteArray) {
        if (uuid == SmartChassisUuids.bindingControlPoint) {
            handleBindingNotification(value)
        } else if (uuid == SmartChassisUuids.tableInfo) {
            SmartChassisCodec.parseTableInfo(value)?.let { tableInfo ->
                _tableInfoUpdates.value = tableInfo
            }
        } else if (uuid == SmartChassisUuids.lightStatus) {
            SmartChassisCodec.parseLightStatus(value)
        }
    }

    private fun handleBindingNotification(value: ByteArray) {
        bindingNotificationHandler.handle(value)
    }

    private suspend fun readLightStatus(): SmartChassisClientResult<SmartChassisLightStatus> {
        val characteristic = lightStatus ?: return SmartChassisClientResult.Failure("Light Status characteristic is unavailable")
        val gattClient = gatt ?: return SmartChassisClientResult.Failure("smart chassis is not connected")
        val deferred = CompletableDeferred<SmartChassisClientResult<SmartChassisLightStatus>>()
        pendingLightStatusRead = deferred
        if (!gattClient.readCharacteristic(characteristic)) {
            pendingLightStatusRead = null
            return SmartChassisClientResult.Failure("Light Status read failed to start")
        }
        return withBleTimeout("Light Status read timed out") { deferred.await() }
    }

    private fun completeConnectWhenBonded() {
        val device = gatt?.device ?: return failConnect("Connected device is unavailable")
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> completeConnect()
            BluetoothDevice.BOND_BONDING -> waitForBond(device)
            else -> startBond(device)
        }
    }

    private fun startBond(device: BluetoothDevice) {
        waitForBond(device)
        _connectionState.value = _connectionState.value.copy(
            message = "Pairing with ${device.address}"
        )
        if (!device.createBond()) {
            unregisterBondReceiver()
            failConnect("Smart chassis pairing could not be started")
            closeGatt()
        }
    }

    private fun waitForBond(device: BluetoothDevice) {
        if (bondReceiver != null && bondingDeviceAddress == device.address) {
            return
        }
        unregisterBondReceiver()
        bondingDeviceAddress = device.address
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    return
                }
                val changedDevice = intent.bluetoothDeviceExtra() ?: return
                if (!changedDevice.address.equals(bondingDeviceAddress, ignoreCase = true)) {
                    return
                }
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> {
                        unregisterBondReceiver()
                        completeConnect()
                    }

                    BluetoothDevice.BOND_NONE -> {
                        unregisterBondReceiver()
                        failConnect("Smart chassis pairing was cancelled or failed")
                        closeGatt()
                    }
                }
            }
        }
        bondReceiver = receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun completeConnect() {
        val deviceAddress = gatt?.device?.address?.uppercase() ?: return failConnect("Connected device address is unavailable")
        val advertisedIdentity = pendingIdentity?.takeIf {
            it.address.equals(deviceAddress, ignoreCase = true)
        }
        val device = SmartChassisDevice(
            address = deviceAddress,
            name = advertisedIdentity?.name ?: gatt?.device?.name,
            rssi = advertisedIdentity?.rssi,
            advertisement = advertisedIdentity?.advertisement ?: SmartChassisAdvertisement(
                companyId = SmartChassisProtocol.DEV_COMPANY_ID,
                protoVersion = SmartChassisProtocol.PROTOCOL_VERSION,
                batchId = 0,
                batteryPct = 0,
                statusFlags = 0,
                tableSeqLow16 = 0
            ),
            lastSeenAt = advertisedIdentity?.lastSeenAt ?: System.currentTimeMillis()
        )
        pendingIdentity = null
        connectedDevice = device
        _connectionState.value = SmartChassisConnectionState(
            phase = SmartChassisConnectionPhase.CONNECTED,
            device = device
        )
        pendingConnect?.complete(SmartChassisClientResult.Success(device))
        pendingConnect = null
    }

    private fun failConnect(message: String) {
        pendingConnect?.complete(SmartChassisClientResult.Failure(message))
        pendingConnect = null
        pendingIdentity = null
        _connectionState.value = SmartChassisConnectionState(
            phase = SmartChassisConnectionPhase.DISCONNECTED,
            message = message
        )
    }

    private fun failPendingOperations(message: String) {
        pendingBindingOp?.deferred?.complete(SmartChassisClientResult.Failure(message, pendingBindingOp?.op))
        pendingBindingOp = null
        pendingReadAll?.deferred?.complete(SmartChassisClientResult.Failure(message, SmartChassisBindingOp.READ_ALL))
        pendingReadAll = null
        pendingTableInfoRead?.complete(SmartChassisClientResult.Failure(message))
        pendingTableInfoRead = null
        pendingLightStatusRead?.complete(SmartChassisClientResult.Failure(message))
        pendingLightStatusRead = null
    }

    private fun closeGatt() {
        unregisterBondReceiver()
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        bindingControlPoint = null
        tableInfoCharacteristic = null
        lightCommand = null
        lightStatus = null
        connectedDevice = null
        pendingIdentity = null
        _tableInfoUpdates.value = null
        pendingDescriptors.clear()
        pendingDescriptorSetup = null
    }

    private fun unregisterBondReceiver() {
        bondReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        bondReceiver = null
        bondingDeviceAddress = null
    }


    private suspend fun <T> withBleTimeout(
        timeoutMessage: String,
        timeoutMs: Long = OPERATION_TIMEOUT_MS,
        block: suspend () -> SmartChassisClientResult<T>
    ): SmartChassisClientResult<T> {
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            SmartChassisClientResult.Failure(timeoutMessage)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }



    private companion object {
        private const val OPERATION_TIMEOUT_MS = 12_000L
        private const val CONNECT_TIMEOUT_MS = 45_000L
        private const val FLASH_BUSY_MAX_RETRIES = 3
        private const val FLASH_BUSY_RETRY_DELAY_MS = 100L
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
