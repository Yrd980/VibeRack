import SwiftUI
import UniformTypeIdentifiers

struct BOMPickView: View {
    let repository: ChassisRepository
    let workflow: SmartChassisWorkflow

    @State private var workbook: BOMWorkbook?
    @State private var plan = BOMPickPlan(groups: [], unmatchedLines: [])
    @State private var completedLineIDs: Set<String> = []
    @State private var statusMessage: String?
    @State private var errorMessage: String?
    @State private var isFileImporterPresented = false

    private let planner = BOMPickPlanner()

    var body: some View {
        List {
            importSection

            if let workbook {
                summarySection(workbook)
                pickGroupsSection
                bomRowsSection(workbook)
                unmatchedSection
            } else {
                ContentUnavailableView("导入 BOM", systemImage: "tablecells", description: Text("加载样例或选择 XLSX/CSV 文件"))
            }

            if let statusMessage {
                Section {
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
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
        .navigationTitle("BOM Pick")
        .fileImporter(
            isPresented: $isFileImporterPresented,
            allowedContentTypes: [.spreadsheet, .commaSeparatedText, .item],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                loadWorkbook(at: url)
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
        }
        .onAppear {
            if workbook == nil {
                loadSampleWorkbook()
            }
        }
    }

    private var importSection: some View {
        Section {
            Button {
                loadSampleWorkbook()
            } label: {
                Label("加载样例 BOM", systemImage: "tray.and.arrow.down")
            }
            Button {
                isFileImporterPresented = true
            } label: {
                Label("选择 BOM 文件", systemImage: "doc.badge.plus")
            }
        }
    }

    private func summarySection(_ workbook: BOMWorkbook) -> some View {
        Section("概览") {
            LabeledContent("Sheet", value: workbook.sheetName)
            LabeledContent("数据行", value: "\(workbook.lines.count)")
            LabeledContent("已匹配", value: "\(plan.matchedLineCount)")
            LabeledContent("未匹配", value: "\(plan.unmatchedLines.count)")
            LabeledContent("剩余分组", value: "\(plan.groups.count)")
        }
    }

    private var pickGroupsSection: some View {
        Section("按底盘 PICK") {
            if plan.groups.isEmpty {
                Text("暂无可 PICK 的匹配行")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(plan.groups) { group in
                    VStack(alignment: .leading, spacing: 8) {
                        let slotList = group.slotNumbers.map(String.init).joined(separator: ", ")
                        let maskText = String(group.makePickLightCommand().maskA, radix: 16).uppercased()
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(group.chassisDisplayName)
                                    .font(.headline)
                                Text("槽位 \(slotList) · mask 0x\(maskText)")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button {
                                Task { await sendPick(group) }
                            } label: {
                                Image(systemName: "lightbulb.2")
                            }
                            .buttonStyle(.bordered)
                            .accessibilityLabel("发送 PICK")
                        }

                        ForEach(group.targets) { target in
                            HStack {
                                Text(target.designator ?? "Row \(target.rowNumber)")
                                Spacer()
                                Text("\(target.protocolPartId) · \(target.quantityAvailable)")
                                    .foregroundStyle(.secondary)
                            }
                            .font(.caption)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    private func bomRowsSection(_ workbook: BOMWorkbook) -> some View {
        Section("BOM 行") {
            ForEach(workbook.lines) { line in
                Toggle(isOn: completionBinding(for: line)) {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(line.designator ?? "Row \(line.rowNumber)")
                                .font(.headline)
                            Spacer()
                            Text(statusText(for: line))
                                .font(.caption)
                                .foregroundStyle(statusColor(for: line))
                        }
                        Text([line.supplierPart, line.manufacturerPart, line.comment, line.footprint, line.value].compactMap { $0 }.joined(separator: " · "))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var unmatchedSection: some View {
        Section("未匹配") {
            if plan.unmatchedLines.isEmpty {
                Text("所有剩余行都已匹配或完成")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(plan.unmatchedLines) { line in
                    Text("\(line.designator ?? "Row \(line.rowNumber)") · \(line.comment ?? line.supplierPart ?? "-")")
                }
            }
        }
    }

    private func loadSampleWorkbook() {
        guard let url = Bundle.main.url(forResource: "bom", withExtension: "xlsx") else {
            errorMessage = "未找到内置样例 BOM"
            return
        }
        loadWorkbook(at: url)
    }

    private func loadWorkbook(at url: URL) {
        do {
            let scoped = url.startAccessingSecurityScopedResource()
            defer {
                if scoped {
                    url.stopAccessingSecurityScopedResource()
                }
            }
            workbook = try BOMWorkbookImporter.import(at: url)
            completedLineIDs = []
            rebuildPlan()
            statusMessage = "已导入 \(workbook?.lines.count ?? 0) 条 BOM 行"
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func completionBinding(for line: BOMLine) -> Binding<Bool> {
        Binding {
            completedLineIDs.contains(line.id)
        } set: { isCompleted in
            if isCompleted {
                completedLineIDs.insert(line.id)
            } else {
                completedLineIDs.remove(line.id)
            }
            rebuildPlan()
        }
    }

    private func rebuildPlan() {
        guard let workbook else {
            plan = BOMPickPlan(groups: [], unmatchedLines: [])
            return
        }
        do {
            let stock = try matchingStock(for: workbook.lines)
            plan = planner.buildPickPlan(lines: workbook.lines, stock: stock, completedLineIDs: completedLineIDs)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func matchingStock(for lines: [BOMLine]) throws -> [StockSearchResult] {
        var resultsByID: [String: StockSearchResult] = [:]
        for token in lines.flatMap(\.searchTokens).uniqueSorted {
            for result in try repository.searchStock(query: token) {
                resultsByID[result.id] = result
            }
        }
        return Array(resultsByID.values)
    }

    @MainActor
    private func sendPick(_ group: BOMPickGroup) async {
        do {
            try await workflow.pickByLight(group)
            let command = group.makePickLightCommand()
            statusMessage = "已发送 PICK：\(group.chassisCode)，mask 0x\(String(command.maskA, radix: 16).uppercased())"
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func statusText(for line: BOMLine) -> String {
        if completedLineIDs.contains(line.id) {
            return "完成"
        }
        if plan.groups.flatMap(\.targets).contains(where: { $0.lineID == line.id }) {
            return "匹配"
        }
        return "未匹配"
    }

    private func statusColor(for line: BOMLine) -> Color {
        if completedLineIDs.contains(line.id) {
            return .secondary
        }
        if plan.groups.flatMap(\.targets).contains(where: { $0.lineID == line.id }) {
            return .green
        }
        return .orange
    }
}

private extension BOMLine {
    var searchTokens: [String] {
        [supplierPart, manufacturerPart]
            .compactMap { value in
                let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed?.isEmpty == false ? trimmed : nil
            }
    }
}

private extension Sequence where Element: Hashable {
    var uniqueSorted: [Element] {
        Array(Set(self)).sorted { String(describing: $0) < String(describing: $1) }
    }
}

#Preview {
    let dependencies = DependencyGraph.simulatorPreview()
    NavigationStack {
        BOMPickView(
            repository: dependencies.chassisRepository,
            workflow: dependencies.chassisWorkflow
        )
    }
}
