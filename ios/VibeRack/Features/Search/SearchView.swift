import SwiftUI

struct SearchView: View {
    let repository: ChassisRepository

    @State private var query = ""
    @State private var results: [StockSearchResult] = []
    @State private var statusMessage: String?
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                if normalizedQuery.isEmpty {
                    ContentUnavailableView("搜索组件", systemImage: "magnifyingglass")
                } else if let errorMessage {
                    ContentUnavailableView(
                        "搜索失败",
                        systemImage: "exclamationmark.triangle",
                        description: Text(errorMessage)
                    )
                } else if results.isEmpty {
                    ContentUnavailableView("未找到组件", systemImage: "tray")
                } else {
                    ForEach(results) { result in
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(result.protocolPartId)
                                    .font(.headline)
                                Text("\(result.chassisCode) · 槽位 \(result.slotNumber) · 数量 \(result.quantity)")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button {
                                makeFindCommand(for: result)
                            } label: {
                                Image(systemName: "lightbulb")
                            }
                            .buttonStyle(.bordered)
                            .accessibilityLabel("Find by Light")
                        }
                    }
                }
            }

            if let statusMessage {
                Section {
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("搜索")
        .searchable(text: $query, prompt: "料号、MPN、封装")
        .task(id: normalizedQuery) {
            refreshResults()
        }
    }

    private var normalizedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func refreshResults() {
        statusMessage = nil
        errorMessage = nil
        guard normalizedQuery.isEmpty == false else {
            results = []
            return
        }

        do {
            results = try repository.searchStock(query: normalizedQuery)
        } catch {
            results = []
            errorMessage = error.localizedDescription
        }
    }

    private func makeFindCommand(for result: StockSearchResult) {
        let command = result.makeFindLightCommand()
        statusMessage = "已生成 FIND 指令：\(result.chassisCode) 槽位 \(result.slotNumber)，mask 0x\(String(command.maskA, radix: 16).uppercased())"
    }
}

#Preview {
    NavigationStack {
        SearchView(repository: DependencyGraph.simulatorPreview().chassisRepository)
    }
}
