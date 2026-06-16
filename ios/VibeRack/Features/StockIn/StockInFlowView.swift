import SwiftUI

struct StockInFlowView: View {
    var body: some View {
        Form {
            Section("组件") {
                LabeledContent("扫码", value: "等待相机")
                LabeledContent("目标底盘", value: "未选择")
                LabeledContent("目标槽位", value: "未选择")
            }

            Section {
                Button {
                } label: {
                    Label("开始 STOCK_IN 引导", systemImage: "lightbulb")
                }
            }
        }
        .navigationTitle("入库")
    }
}
