import SwiftUI

struct ChassisListView: View {
    @Environment(RouterPath.self) private var router

    private let demoSlots = Array(1...SmartChassisProtocol.slotCount)

    var body: some View {
        List {
            Section {
                Button {
                    router.path.append(.chassisDetail(id: "simulator"))
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("VBRK-0000")
                                .font(.headline)
                            Text("batch 1 · 25 槽 · 模拟器")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Label("100%", systemImage: "battery.100")
                            .foregroundStyle(.secondary)
                    }
                }
            } header: {
                Text("智能底盘")
            }

            Section("数字孪生预览") {
                SlotGridView(slots: demoSlots)
                    .padding(.vertical, 8)
            }
        }
        .navigationTitle("底盘")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                } label: {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                }
                .accessibilityLabel("扫描底盘")
            }
        }
    }
}

struct ChassisDetailView: View {
    let chassisID: String

    var body: some View {
        List {
            Section("状态") {
                LabeledContent("来源", value: chassisID)
                LabeledContent("Device Health", value: "等待 BLE")
                LabeledContent("Binding Table", value: "模拟数据")
            }

            Section("槽位") {
                SlotGridView(slots: Array(1...SmartChassisProtocol.slotCount))
                    .padding(.vertical, 8)
            }
        }
        .navigationTitle("VBRK-0000")
    }
}

private struct SlotGridView: View {
    let slots: [Int]

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 8) {
            ForEach(slots, id: \.self) { slot in
                VStack(spacing: 2) {
                    Text("\(slot)")
                        .font(.headline)
                    Text(slot == 1 ? "C1234567" : "空")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .frame(maxWidth: .infinity)
                .aspectRatio(1, contentMode: .fit)
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 8))
                .accessibilityLabel("槽位 \(slot)")
            }
        }
    }
}

#Preview("Chassis List") {
    NavigationStack {
        ChassisListView()
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .chassisDetail(let id):
                    ChassisDetailView(chassisID: id)
                }
            }
    }
    .environment(RouterPath())
}

#Preview("Chassis Detail") {
    NavigationStack {
        ChassisDetailView(chassisID: "simulator")
    }
}
