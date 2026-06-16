import SwiftUI

struct SettingsView: View {
    let repository: ChassisRepository

    var body: some View {
        Form {
            Section("硬件") {
                NavigationLink {
                    HardwareRestoreView(repository: repository)
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
    let repository: ChassisRepository

    @State private var chassisList: [SmartChassisSummary] = []
    @State private var selectedChassisID: String?
    @State private var statusMessage: String?
    @State private var errorMessage: String?
    @State private var isLoading = true

    var body: some View {
        Form {
            Section("恢复来源") {
                if isLoading {
                    ProgressView()
                } else if chassisList.isEmpty {
                    ContentUnavailableView("暂无智能底盘", systemImage: "square.grid.3x3.square")
                } else {
                    Picker("底盘", selection: selectedChassisBinding) {
                        ForEach(chassisList) { chassis in
                            Text(chassis.displayName).tag(chassis.id)
                        }
                    }
                }

                LabeledContent("模式", value: "模拟 READ_ALL")
                LabeledContent("说明", value: "真机 BLE/NFC 待验证")
            }

            Section {
                Button {
                    Task { await runSimulatorRestore() }
                } label: {
                    Label("运行模拟恢复", systemImage: "externaldrive.badge.checkmark")
                }
                .disabled(selectedChassisID == nil)

                if let statusMessage {
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("从底盘恢复")
        .task {
            await load()
        }
        .refreshable {
            await load()
        }
    }

    private var selectedChassisBinding: Binding<String> {
        Binding(
            get: { selectedChassisID ?? chassisList.first?.id ?? "" },
            set: { selectedChassisID = $0 }
        )
    }

    @MainActor
    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            chassisList = try repository.fetchChassisList()
            if selectedChassisID == nil {
                selectedChassisID = chassisList.first?.id
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    @MainActor
    private func runSimulatorRestore() async {
        guard let selectedChassisID else { return }

        statusMessage = nil
        errorMessage = nil
        do {
            let snapshot = Self.makeSimulatorRestoreSnapshot()
            try repository.restoreFromBindingTableSnapshot(
                chassisID: selectedChassisID,
                snapshot: snapshot
            )
            statusMessage = "已按 READ_ALL 快照恢复 2 个非空槽位，table_seq \(snapshot.tableInfo.tableSeq)"
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private static func makeSimulatorRestoreSnapshot() -> BindingTableSnapshot {
        let records = (1...SmartChassisProtocol.slotCount).map { slot in
            switch slot {
            case 1:
                SmartChassisCodec.encodeSlotRecord(slot: slot, partId: "C1111111", quantity: 5, flags: 0)
            case 3:
                SmartChassisCodec.encodeSlotRecord(slot: slot, partId: "C3333333", quantity: 9, flags: 1)
            default:
                Data(repeating: 0, count: SmartChassisProtocol.slotRecordSize)
            }
        }
        return BindingTableSnapshot(
            tableInfo: TableInfo(
                tableSeq: 42,
                crc16: SmartChassisCodec.tableCRC16(records),
                slotCount: SmartChassisProtocol.slotCount
            ),
            records: records
        )
    }
}

#Preview {
    NavigationStack {
        SettingsView(repository: DependencyGraph.simulatorPreview().chassisRepository)
    }
}
