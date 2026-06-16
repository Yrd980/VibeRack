import SwiftUI

struct SettingsView: View {
    var body: some View {
        Form {
            Section("硬件") {
                NavigationLink {
                    HardwareRestoreView()
                } label: {
                    Label("从底盘恢复", systemImage: "arrow.clockwise")
                }
                LabeledContent("Device Health", value: "BLE 连接后读取")
            }

            Section("诊断") {
                LabeledContent("协议版本", value: "0x01")
                LabeledContent("槽位数", value: "\(SmartChassisProtocol.slotCount)")
            }
        }
        .navigationTitle("设置")
    }
}

private struct HardwareRestoreView: View {
    var body: some View {
        ContentUnavailableView("等待硬件恢复实现", systemImage: "externaldrive.badge.icloud")
            .navigationTitle("从底盘恢复")
    }
}

#Preview {
    NavigationStack {
        SettingsView()
    }
}
