import SwiftUI

struct SettingsView: View {
    let repository: ChassisRepository
    let simulatorClient: ChassisSimulatorClient?

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
                NavigationLink {
                    DiagnosticsView(
                        repository: repository,
                        simulatorClient: simulatorClient
                    )
                } label: {
                    Label("诊断事件", systemImage: "waveform.path.ecg")
                }
                LabeledContent("协议版本", value: "0x01")
                LabeledContent("槽位数", value: "\(SmartChassisProtocol.slotCount)")
            }
        }
        .navigationTitle("设置")
    }
}

private struct DiagnosticsView: View {
    let repository: ChassisRepository
    let simulatorClient: ChassisSimulatorClient?

    @State private var chassisList: [SmartChassisSummary] = []
    @State private var selectedChassisID: String?
    @State private var operations: [StockOperationRecord] = []
    @State private var errorMessage: String?
    @State private var isLoading = true

    var body: some View {
        Form {
            Section("来源") {
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

                Button {
                    Task { await load() }
                } label: {
                    Label("刷新诊断", systemImage: "arrow.clockwise")
                }
            }

            operationsSection
            simulatorEventsSection

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("诊断事件")
        .task {
            await load()
        }
    }

    private var selectedChassisBinding: Binding<String> {
        Binding(
            get: { selectedChassisID ?? chassisList.first?.id ?? "" },
            set: { selectedChassisID = $0 }
        )
    }

    private var operationsSection: some View {
        Section("Stock Operations") {
            if operations.isEmpty {
                Text("暂无操作流水")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(operations.prefix(12)) { operation in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(operation.type.rawValue)
                                .font(.headline)
                            Spacer()
                            Text("槽位 \(operation.slotNumber)")
                                .foregroundStyle(.secondary)
                        }
                        Text("\(operation.protocolPartId) \(quantityText(operation))")
                            .font(.subheadline)
                        Text("opcode \(byteText(operation.bleOpcode)) · status \(byteText(operation.bleStatus))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var simulatorEventsSection: some View {
        Section("Simulator Commands") {
            if let simulatorClient {
                let events = Array(simulatorClient.diagnosticEvents.suffix(12).reversed())
                if events.isEmpty {
                    Text("当前无模拟器命令日志")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(events) { event in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(eventTitle(event))
                                    .font(.headline)
                                Spacer()
                                if let tableSeq = event.tableSeq {
                                    Text("seq \(tableSeq)")
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Text(eventMetadata(event))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(event.payloadHex)
                                .font(.caption.monospaced())
                                .textSelection(.enabled)
                        }
                    }
                }
            } else {
                Text("暂无模拟器命令")
                    .foregroundStyle(.secondary)
            }
        }
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
            if let selectedChassisID {
                operations = try repository.fetchStockOperations(chassisID: selectedChassisID)
                    .reversed()
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func quantityText(_ operation: StockOperationRecord) -> String {
        "\(operation.quantityBefore.map(String.init) ?? "-") -> \(operation.quantityAfter.map(String.init) ?? "-")"
    }

    private func byteText(_ value: UInt8?) -> String {
        guard let value else {
            return "-"
        }
        return "0x\(String(value, radix: 16).uppercased())"
    }

    private func eventTitle(_ event: SmartChassisDiagnosticEvent) -> String {
        switch event.kind {
        case .binding:
            return "Binding \(byteText(event.opcode))"
        case .light:
            return "Light \(modeText(event.mode))"
        }
    }

    private func eventMetadata(_ event: SmartChassisDiagnosticEvent) -> String {
        switch event.kind {
        case .binding:
            return "opcode \(byteText(event.opcode)) · status \(byteText(event.status))"
        case .light:
            return "mode \(modeText(event.mode))"
        }
    }

    private func modeText(_ mode: LightMode?) -> String {
        guard let mode else {
            return "-"
        }
        return "0x\(String(mode.code, radix: 16).uppercased())"
    }
}

private struct HardwareRestoreView: View {
    let repository: ChassisRepository

    @State private var chassisList: [SmartChassisSummary] = []
    @State private var selectedChassisID: String?
    @State private var restorePreview: HardwareRestorePreview?
    @State private var pendingSnapshot: BindingTableSnapshot?
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
                    Task { await previewSimulatorRestore() }
                } label: {
                    Label("生成恢复预览", systemImage: "doc.text.magnifyingglass")
                }
                .disabled(selectedChassisID == nil)

                Button(role: .destructive) {
                    Task { await applyPreviewedRestore() }
                } label: {
                    Label("应用恢复", systemImage: "externaldrive.badge.checkmark")
                }
                .disabled(selectedChassisID == nil || pendingSnapshot == nil)

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

            if let restorePreview {
                previewSection(restorePreview)
                changeSection("新增", changes: restorePreview.added)
                changeSection("更新", changes: restorePreview.updated)
                changeSection("清空", changes: restorePreview.cleared)
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
    private func previewSimulatorRestore() async {
        guard let selectedChassisID else { return }

        statusMessage = nil
        errorMessage = nil
        do {
            let snapshot = Self.makeSimulatorRestoreSnapshot()
            let slots = try repository.fetchSlots(chassisID: selectedChassisID)
            restorePreview = try HardwareRestorePreviewBuilder().buildPreview(
                localSlots: slots,
                snapshot: snapshot
            )
            pendingSnapshot = snapshot
            statusMessage = "已生成恢复预览：\(restorePreview?.changeCount ?? 0) 个槽位变化"
        } catch {
            restorePreview = nil
            pendingSnapshot = nil
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func applyPreviewedRestore() async {
        guard let selectedChassisID, let pendingSnapshot else { return }

        statusMessage = nil
        errorMessage = nil
        do {
            try repository.restoreFromBindingTableSnapshot(
                chassisID: selectedChassisID,
                snapshot: pendingSnapshot
            )
            statusMessage = "已按 READ_ALL 快照应用恢复，table_seq \(pendingSnapshot.tableInfo.tableSeq)"
            restorePreview = nil
            self.pendingSnapshot = nil
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func previewSection(_ preview: HardwareRestorePreview) -> some View {
        Section("恢复预览") {
            LabeledContent("table_seq", value: "\(preview.tableSeq)")
            LabeledContent("CRC16", value: "0x\(String(preview.tableCRC16, radix: 16).uppercased())")
            LabeledContent("新增", value: "\(preview.added.count)")
            LabeledContent("更新", value: "\(preview.updated.count)")
            LabeledContent("清空", value: "\(preview.cleared.count)")
        }
    }

    private func changeSection(
        _ title: String,
        changes: [HardwareRestorePreviewChange]
    ) -> some View {
        Section(title) {
            if changes.isEmpty {
                Text("无变化")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(changes) { change in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("槽位 \(change.slotNumber)")
                            .font(.headline)
                        Text(changeDetail(change))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private func changeDetail(_ change: HardwareRestorePreviewChange) -> String {
        "\(factDescription(change.before)) -> \(factDescription(change.after))"
    }

    private func factDescription(_ fact: HardwareRestoreSlotFact?) -> String {
        guard let fact else {
            return "空"
        }
        return "\(fact.protocolPartId) x\(fact.quantity)"
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
        let dependencies = DependencyGraph.simulatorPreview()
        SettingsView(
            repository: dependencies.chassisRepository,
            simulatorClient: dependencies.simulatorClient
        )
    }
}
