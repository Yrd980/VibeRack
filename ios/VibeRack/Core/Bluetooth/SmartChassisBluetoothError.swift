import CoreBluetooth
import Foundation

public enum SmartChassisBluetoothError: Error, Equatable {
    case bluetoothUnavailable
    case bluetoothUnauthorized
    case bluetoothPoweredOff
    case bluetoothUnsupportedState(CBManagerState)
    case scanFailed(String)
    case peripheralUnavailable
    case connectionFailed(String)
    case disconnected(String?)
    case serviceMissing(CBUUID)
    case characteristicMissing(CBUUID)
    case invalidPayload(CBUUID)
    case bindingTable(SmartChassisBindingTableError)
    case bindingCommandFailed(BindingOp, BindingStatus)
    case readFailed(CBUUID, String)
    case writeFailed(CBUUID, String)
    case encryptedWriteRequiresPairing
    case pairingRequired
    case pairingRejected
    case insufficientEncryption
    case insufficientAuthentication
    case timeout
    case cancelled

    public var userMessage: String {
        switch self {
        case .bluetoothUnavailable:
            return "此设备不支持蓝牙。"
        case .bluetoothUnauthorized:
            return "需要允许蓝牙权限后才能扫描和连接智能底盘。"
        case .bluetoothPoweredOff:
            return "蓝牙已关闭，请先在系统设置中打开蓝牙。"
        case .bluetoothUnsupportedState:
            return "蓝牙暂不可用，请稍后重试。"
        case let .scanFailed(reason):
            return "扫描智能底盘失败：\(reason)"
        case .peripheralUnavailable:
            return "未找到目标智能底盘，请靠近设备后重新扫描。"
        case let .connectionFailed(reason):
            return "连接智能底盘失败：\(reason)"
        case .disconnected:
            return "智能底盘连接已断开，请重新连接后再操作。"
        case .serviceMissing:
            return "智能底盘固件缺少必要服务，请检查固件版本。"
        case .characteristicMissing:
            return "智能底盘固件缺少必要特征，请检查固件版本。"
        case .invalidPayload:
            return "智能底盘返回的数据格式不正确。"
        case .bindingTable:
            return "智能底盘绑定表校验失败，已拒绝使用这次读取结果。"
        case let .bindingCommandFailed(op, status):
            return "智能底盘绑定操作失败：op=\(op.code)，status=\(status.code)。"
        case let .readFailed(_, reason):
            return "读取智能底盘状态失败：\(reason)"
        case let .writeFailed(_, reason):
            return "写入智能底盘失败：\(reason)"
        case .encryptedWriteRequiresPairing, .pairingRequired:
            return "此操作需要与智能底盘配对。请确认系统弹窗并完成蓝牙配对后重试。"
        case .pairingRejected:
            return "蓝牙配对未完成，请接受系统配对弹窗后重试。"
        case .insufficientEncryption:
            return "当前连接未加密。请断开后重新连接，并在系统弹窗中完成配对。"
        case .insufficientAuthentication:
            return "智能底盘需要已认证的蓝牙配对，请重新配对后再写入。"
        case .timeout:
            return "智能底盘响应超时，请靠近设备后重试。"
        case .cancelled:
            return "操作已取消。"
        }
    }

    public static func mapCoreBluetoothError(
        _ error: Error?,
        characteristic: CBUUID? = nil
    ) -> SmartChassisBluetoothError? {
        guard let error else {
            return nil
        }

        let nsError = error as NSError
        if nsError.domain == CBATTError.errorDomain,
           let code = CBATTError.Code(rawValue: nsError.code) {
            switch code {
            case .insufficientEncryption:
                return .insufficientEncryption
            case .insufficientAuthentication:
                return .insufficientAuthentication
            case .insufficientEncryptionKeySize:
                return .encryptedWriteRequiresPairing
            default:
                break
            }
        }

        if nsError.domain == CBError.errorDomain,
           let code = CBError.Code(rawValue: nsError.code) {
            switch code {
            case .peerRemovedPairingInformation:
                return .pairingRequired
            case .connectionFailed:
                return .connectionFailed(error.localizedDescription)
            default:
                break
            }
        }

        if let characteristic {
            return .readFailed(characteristic, error.localizedDescription)
        }
        return .connectionFailed(error.localizedDescription)
    }
}
