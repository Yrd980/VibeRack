import SwiftUI

struct StockInFlowView: View {
    let repository: ChassisRepository
    let workflow: SmartChassisWorkflow

    @State private var chassisList: [SmartChassisSummary] = []
    @State private var slots: [ChassisSlotState] = []
    @State private var selectedChassisID: String?
    @State private var selectedSlotNumber = 1
    @State private var protocolPartId = "C7654321"
    @State private var quantity = 1
    @State private var qrPayloadText = ""
    @State private var parsedQrPayload: InboundQrPayload?
    @State private var statusMessage: String?
    @State private var errorMessage: String?
    @State private var isLoading = true

    var body: some View {
        Form {
            Section("组件") {
                TextField("协议料号", text: $protocolPartId)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .onChange(of: protocolPartId) { _, _ in
                        if parsedQrPayload?.partNumber != normalizedPartId.uppercased() {
                            parsedQrPayload = nil
                        }
                    }
                Stepper(value: $quantity, in: 1...65_535) {
                    LabeledContent("数量", value: "\(quantity)")
                }
                TextField("粘贴 LCSC QR payload", text: $qrPayloadText, axis: .vertical)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button {
                    applyQrPayload()
                } label: {
                    Label("解析 QR payload", systemImage: "qrcode.viewfinder")
                }
            }

            Section("目标") {
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
                    .onChange(of: selectedChassisID) { _, _ in
                        Task { await loadSlotsForSelectedChassis() }
                    }

                    Picker("空槽位", selection: $selectedSlotNumber) {
                        ForEach(availableSlots) { slot in
                            Text("槽位 \(slot.slotNumber)").tag(slot.slotNumber)
                        }
                    }
                    .disabled(availableSlots.isEmpty)

                    if availableSlots.isEmpty {
                        Text("当前底盘没有空槽位")
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                Button {
                    Task { await bindSelectedSlot() }
                } label: {
                    Label("记录入库绑定", systemImage: "tray.and.arrow.down")
                }
                .disabled(!canBind)

                if let statusMessage {
                    Text(statusMessage)
                        .foregroundStyle(.secondary)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("入库")
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

    private var availableSlots: [ChassisSlotState] {
        slots.filter(\.isEmpty)
    }

    private var normalizedPartId: String {
        protocolPartId.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canBind: Bool {
        selectedChassisID != nil && !availableSlots.isEmpty && !normalizedPartId.isEmpty
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
            try loadSlots()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    @MainActor
    private func loadSlotsForSelectedChassis() async {
        do {
            try loadSlots()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func loadSlots() throws {
        guard let selectedChassisID else {
            slots = []
            return
        }
        slots = try repository.fetchSlots(chassisID: selectedChassisID)
        if !availableSlots.contains(where: { $0.slotNumber == selectedSlotNumber }) {
            selectedSlotNumber = availableSlots.first?.slotNumber ?? 1
        }
    }

    private func applyQrPayload() {
        errorMessage = nil
        statusMessage = nil
        do {
            let payload = try LcscQrParser.parse(qrPayloadText)
            protocolPartId = payload.partNumber
            quantity = max(payload.quantity, 1)
            parsedQrPayload = payload
            statusMessage = "已解析 LCSC payload：\(payload.partNumber)"
        } catch {
            parsedQrPayload = nil
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func bindSelectedSlot() async {
        guard let selectedChassisID else { return }

        errorMessage = nil
        statusMessage = nil
        do {
            let componentID = try componentForCurrentEntry()?.id
            try await workflow.stockIn(
                chassisID: selectedChassisID,
                slotNumber: selectedSlotNumber,
                protocolPartId: normalizedPartId,
                quantity: quantity,
                componentID: componentID
            )
            statusMessage = "已执行 STOCK_IN + WRITE_ONE，并绑定 \(normalizedPartId) 到槽位 \(selectedSlotNumber)"
            try loadSlots()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func componentForCurrentEntry() throws -> Component? {
        guard let parsedQrPayload,
              parsedQrPayload.partNumber == normalizedPartId.uppercased()
        else {
            return nil
        }
        return try repository.upsertComponent(
            ComponentDraft(
                protocolPartId: parsedQrPayload.partNumber,
                source: "lcsc_qr",
                lcscPartNumber: parsedQrPayload.partNumber,
                manufacturerPartNumber: parsedQrPayload.manufacturerPartNo,
                name: nil,
                packageName: nil,
                brand: nil,
                specSummary: nil
            )
        )
    }
}

#Preview {
    let dependencies = DependencyGraph.simulatorPreview()
    NavigationStack {
        StockInFlowView(
            repository: dependencies.chassisRepository,
            workflow: dependencies.chassisWorkflow
        )
    }
}
