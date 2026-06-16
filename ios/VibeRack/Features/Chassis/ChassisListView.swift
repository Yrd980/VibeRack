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

    @State private var chassis: SmartChassisSummary?
    @State private var slots: [ChassisSlotState] = []
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
                    SlotGridView(slots: slots)
                        .padding(.vertical, 8)
                }
            }
        }
        .navigationTitle(chassis?.displayName ?? "智能底盘")
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

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 8) {
            ForEach(slots) { slot in
                SlotCell(slot: slot)
            }
        }
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
                        repository: DependencyGraph.simulatorPreview().chassisRepository
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
            repository: DependencyGraph.simulatorPreview().chassisRepository
        )
    }
}
