import SwiftUI

struct SearchView: View {
    @State private var query = ""

    var body: some View {
        List {
            Section {
                if query.isEmpty {
                    ContentUnavailableView("搜索组件", systemImage: "magnifyingglass")
                } else {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(query.uppercased())
                                .font(.headline)
                            Text("VBRK-0000 · 槽位 1")
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button {
                        } label: {
                            Image(systemName: "lightbulb")
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("Find by Light")
                    }
                }
            }
        }
        .navigationTitle("搜索")
        .searchable(text: $query, prompt: "料号、MPN、封装")
    }
}
