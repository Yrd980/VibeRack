import SwiftUI

struct SearchView: View {
    let repository: ChassisRepository
    let workflow: SmartChassisWorkflow

    @State private var query = ""
    @State private var results: [StockSearchResult] = []
    @State private var statusMessage: String?
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                NavigationLink {
                    BOMPickView(repository: repository, workflow: workflow)
                } label: {
                    Label("BOM Pick", systemImage: "tablecells")
                }
            }

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
                                Text(result.displayPartNumber)
                                    .font(.headline)
                                if let detail = result.componentDisplayDetail {
                                    Text(detail)
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                                Text("\(result.chassisCode) · 槽位 \(result.slotNumber) · 数量 \(result.quantity)")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button {
                                Task { await findByLight(result) }
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

    @MainActor
    private func findByLight(_ result: StockSearchResult) async {
        do {
            try await workflow.findByLight(result)
            let command = result.makeFindLightCommand()
            statusMessage = "已发送 FIND：\(result.chassisCode) 槽位 \(result.slotNumber)，mask 0x\(String(command.maskA, radix: 16).uppercased())"
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private extension StockSearchResult {
    var componentDisplayDetail: String? {
        let parts = [
            component?.name,
            component?.packageName,
            component?.specSummary
        ].compactMap { value in
            let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed?.isEmpty == false ? trimmed : nil
        }
        guard parts.isEmpty == false else {
            return nil
        }
        return parts.joined(separator: " · ")
    }
}

#Preview {
    let dependencies = DependencyGraph.simulatorPreview()
    NavigationStack {
        SearchView(
            repository: dependencies.chassisRepository,
            workflow: dependencies.chassisWorkflow
        )
    }
}
