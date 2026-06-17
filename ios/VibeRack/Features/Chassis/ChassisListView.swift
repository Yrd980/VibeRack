import SwiftUI

struct ChassisListView: View {
    @Environment(RouterPath.self) private var router

    let repository: ChassisRepository

    @State private var chassisList: [SmartChassisSummary] = []
    @State private var previewSlots: [String: [ChassisSlotState]] = [:]
    @State private var errorMessage: String?
    @State private var isLoading = true

    var body: some View {
        List {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, alignment: .center)
            } else if let errorMessage {
                ContentUnavailableView("无法加载底盘", systemImage: "exclamationmark.triangle", description: Text(errorMessage))
            } else if chassisList.isEmpty {
                ContentUnavailableView("暂无智能底盘", systemImage: "square.grid.3x3.square")
            } else {
                Section("智能底盘") {
                    ForEach(chassisList) { chassis in
                        Button {
                            router.path.append(.chassisDetail(id: chassis.id))
                        } label: {
                            ChassisSummaryRow(chassis: chassis)
                        }
                    }
                }

                if let firstChassis = chassisList.first,
                   let slots = previewSlots[firstChassis.id] {
                    Section("数字孪生预览") {
                        SlotGridView(slots: slots)
                            .padding(.vertical, 8)
                    }
                }
            }
        }
        .navigationTitle("底盘")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await load() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .accessibilityLabel("刷新底盘")
            }
        }
        .task {
            await load()
        }
    }

    @MainActor
    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            let chassis = try repository.fetchChassisList()
            chassisList = chassis
            previewSlots = try Dictionary(uniqueKeysWithValues: chassis.map { item in
                (item.id, try repository.fetchSlots(chassisID: item.id))
            })
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

struct ChassisDetailView: View {
    let chassisID: String
    let repository: ChassisRepository
    let workflow: SmartChassisWorkflow

    @State private var chassis: SmartChassisSummary?
    @State private var slots: [ChassisSlotState] = []
    @State private var selectedSlot: ChassisSlotState?
    @State private var statusMessage: String?
    @State private var errorMessage: String?
    @State private var isLoading = true

    var body: some View {
        List {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, alignment: .center)
            } else if let errorMessage {
                ContentUnavailableView("无法加载底盘", systemImage: "exclamationmark.triangle", description: Text(errorMessage))
            } else {
                Section("状态") {
                    LabeledContent("来源", value: chassis?.code ?? chassisID)
                    LabeledContent("电量", value: chassis?.batteryPct.map { "\($0)%" } ?? "未知")
                    LabeledContent("Binding Table", value: chassis?.tableSeq.map { "seq \($0)" } ?? "未同步")
                }

                Section("槽位") {
                    SlotGridView(slots: slots) { slot in
                        selectedSlot = slot
                    }
                        .padding(.vertical, 8)
                }

                if let statusMessage {
                    Section {
                        Text(statusMessage)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle(chassis?.displayName ?? "智能底盘")
        .sheet(item: $selectedSlot) { slot in
            SlotDetailSheet(
                slot: slot,
                onFind: { result in
                    try await workflow.findByLight(result)
                    statusMessage = "已发送 FIND：槽位 \(slot.slotNumber)"
                },
                onSetQuantity: { quantity in
                    try await workflow.setQuantity(
                        chassisID: chassisID,
                        slotNumber: slot.slotNumber,
                        quantity: quantity
                    )
                    statusMessage = "已通过 SET_QTY 更新槽位 \(slot.slotNumber) 数量"
                    await load()
                },
                onClear: {
                    try await workflow.clearSlot(chassisID: chassisID, slotNumber: slot.slotNumber)
                    statusMessage = "已通过 CLEAR_ONE 清空槽位 \(slot.slotNumber)"
                    await load()
                }
            )
        }
        .task(id: chassisID) {
            await load()
        }
    }

    @MainActor
    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            chassis = try repository.fetchChassisList().first { $0.id == chassisID }
            slots = try repository.fetchSlots(chassisID: chassisID)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

private struct ChassisSummaryRow: View {
    let chassis: SmartChassisSummary

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(chassis.displayName)
                    .font(.headline)
                Text(summary)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if let batteryPct = chassis.batteryPct {
                Label("\(batteryPct)%", systemImage: batterySymbol(for: batteryPct))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var summary: String {
        let batch = chassis.batchId.map { "batch \($0)" } ?? "batch 未知"
        return "\(batch) · \(chassis.slotCount) 槽 · 本地账本"
    }

    private func batterySymbol(for batteryPct: Int) -> String {
        switch batteryPct {
        case 80...100: "battery.100"
        case 50..<80: "battery.75"
        case 20..<50: "battery.25"
        default: "battery.0"
        }
    }
}

private struct SlotGridView: View {
    let slots: [ChassisSlotState]
    var onSelectSlot: ((ChassisSlotState) -> Void)?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 8) {
            ForEach(slots) { slot in
                Button {
                    onSelectSlot?(slot)
                } label: {
                    SlotCell(slot: slot)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct SlotDetailSheet: View {
    let slot: ChassisSlotState
    let onFind: (StockSearchResult) async throws -> Void
    let onSetQuantity: (Int) async throws -> Void
    let onClear: () async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var quantity: Int
    @State private var errorMessage: String?

    init(
        slot: ChassisSlotState,
        onFind: @escaping (StockSearchResult) async throws -> Void,
        onSetQuantity: @escaping (Int) async throws -> Void,
        onClear: @escaping () async throws -> Void
    ) {
        self.slot = slot
        self.onFind = onFind
        self.onSetQuantity = onSetQuantity
        self.onClear = onClear
        _quantity = State(initialValue: slot.quantity ?? 1)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("槽位") {
                    LabeledContent("编号", value: "\(slot.slotNumber)")
                    LabeledContent("组件", value: slot.protocolPartId ?? "空")
                    LabeledContent("数量", value: slot.quantity.map(String.init) ?? "未知")
                }

                if !slot.isEmpty {
                    Section("操作") {
                        Stepper(value: $quantity, in: 0...65_535) {
                            LabeledContent("新数量", value: "\(quantity)")
                        }

                        Button {
                            Task { await run { try await onFind(makeSearchResult()) } }
                        } label: {
                            Label("Find-by-Light", systemImage: "lightbulb")
                        }

                        Button {
                            Task { await run { try await onSetQuantity(quantity) } }
                        } label: {
                            Label("更新数量", systemImage: "number")
                        }

                        Button(role: .destructive) {
                            Task { await run { try await onClear() } }
                        } label: {
                            Label("清空槽位", systemImage: "trash")
                        }
                    }
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("槽位 \(slot.slotNumber)")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }

    @MainActor
    private func run(_ action: () async throws -> Void) async {
        do {
            try await action()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func makeSearchResult() -> StockSearchResult {
        StockSearchResult(
            id: slot.id,
            chassisID: slot.chassisID,
            chassisCode: slot.chassisID,
            chassisDisplayName: slot.chassisID,
            slotID: slot.id,
            slotNumber: slot.slotNumber,
            protocolPartId: slot.protocolPartId ?? "",
            quantity: slot.quantity ?? 0,
            flags: slot.flags
        )
    }
}

private struct SlotCell: View {
    let slot: ChassisSlotState

    var body: some View {
        VStack(spacing: 2) {
            Text("\(slot.slotNumber)")
                .font(.headline)
            Text(slot.protocolPartId ?? "空")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            if let quantity = slot.quantity {
                Text("x\(quantity)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .aspectRatio(1, contentMode: .fit)
        .background(slot.isEmpty ? .thinMaterial : .regularMaterial, in: RoundedRectangle(cornerRadius: 8))
        .accessibilityLabel("槽位 \(slot.slotNumber)")
    }
}

#Preview("Chassis List") {
    NavigationStack {
        ChassisListView(repository: DependencyGraph.simulatorPreview().chassisRepository)
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .chassisDetail(let id):
                    ChassisDetailView(
                        chassisID: id,
                        repository: DependencyGraph.simulatorPreview().chassisRepository,
                        workflow: DependencyGraph.simulatorPreview().chassisWorkflow
                    )
                }
            }
    }
    .environment(RouterPath())
}

#Preview("Chassis Detail") {
    NavigationStack {
        ChassisDetailView(
            chassisID: "simulator",
            repository: DependencyGraph.simulatorPreview().chassisRepository,
            workflow: DependencyGraph.simulatorPreview().chassisWorkflow
        )
    }
}
