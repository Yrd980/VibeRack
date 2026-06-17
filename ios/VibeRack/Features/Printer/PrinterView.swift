import SwiftUI
import UIKit

struct PrinterView: View {
    @State private var p0Client = P0BlePrinterClient()
    @State private var positionCode = "A-01"
    @State private var partNumber = "C2040"
    @State private var renderedImage: UIImage?
    @State private var p0PrintImage: UIImage?
    @State private var errorMessage: String?
    @State private var shareItem: LabelShareItem?

    var body: some View {
        Form {
            Section("标签内容") {
                TextField("位置", text: $positionCode)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                TextField("料号", text: $partNumber)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
            }

            Section("预览") {
                if let renderedImage {
                    Image(uiImage: renderedImage)
                        .resizable()
                        .interpolation(.none)
                        .scaledToFit()
                        .frame(maxWidth: .infinity)
                        .frame(height: 112)
                        .padding(.vertical, 4)
                } else {
                    ContentUnavailableView("暂无标签预览", systemImage: "tag")
                }
            }

            Section("P0 BLE 直连") {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(p0Client.connectedName ?? "佟 P0 / 印立方")
                            .font(.headline)
                        Text(p0Client.statusMessage)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    if p0Client.isScanning || p0Client.isPrinting {
                        ProgressView()
                    }
                }

                if !p0Client.isConnected {
                    Button {
                        p0Client.scan()
                    } label: {
                        Label(p0Client.isScanning ? "扫描中" : "扫描 P0", systemImage: "dot.radiowaves.left.and.right")
                    }
                    .disabled(p0Client.isScanning)

                    ForEach(p0Client.printers) { printer in
                        Button {
                            p0Client.connect(to: printer)
                        } label: {
                            HStack {
                                Text(printer.name)
                                Spacer()
                                Text("\(printer.rssi) dBm")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                } else {
                    Button {
                        renderAndPrintP0()
                    } label: {
                        Label("打印到 P0", systemImage: "printer.filled.and.paper")
                    }
                    .disabled(p0Client.isPrinting)

                    Button(role: .destructive) {
                        p0Client.disconnect()
                    } label: {
                        Label("断开 P0", systemImage: "xmark.circle")
                    }
                }
            }

            Section("备用出口") {
                Button {
                    renderAndPrint()
                } label: {
                    Label("系统打印", systemImage: "printer")
                }

                Button {
                    renderAndShare()
                } label: {
                    Label("分享给厂商 App", systemImage: "square.and.arrow.up")
                }
            }

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("打印")
        .task {
            renderPreview()
        }
        .onChange(of: positionCode) { _, _ in
            renderPreview()
        }
        .onChange(of: partNumber) { _, _ in
            renderPreview()
        }
        .sheet(item: $shareItem) { item in
            ShareSheet(items: [item.image])
        }
    }

    private var currentLabel: BoxLayerLabel {
        BoxLayerLabel(positionCode: positionCode, partNumber: partNumber)
    }

    private func renderPreview() {
        do {
            p0PrintImage = try BoxLayerLabelRenderer.renderP0PrintImage(label: currentLabel)
            renderedImage = try BoxLayerLabelRenderer.renderPrinterPreview(label: currentLabel)
            errorMessage = nil
        } catch BoxLayerLabelRenderError.emptyPositionCode {
            renderedImage = nil
            p0PrintImage = nil
            errorMessage = "请填写位置"
        } catch BoxLayerLabelRenderError.emptyPartNumber {
            renderedImage = nil
            p0PrintImage = nil
            errorMessage = "请填写料号"
        } catch {
            renderedImage = nil
            p0PrintImage = nil
            errorMessage = error.localizedDescription
        }
    }

    private func renderAndShare() {
        renderPreview()
        guard let renderedImage else { return }
        shareItem = LabelShareItem(image: renderedImage)
    }

    private func renderAndPrintP0() {
        renderPreview()
        guard let p0PrintImage else { return }
        p0Client.print(image: p0PrintImage)
    }

    private func renderAndPrint() {
        renderPreview()
        guard let renderedImage else { return }

        let controller = UIPrintInteractionController.shared
        let printInfo = UIPrintInfo(dictionary: nil)
        printInfo.outputType = .photo
        printInfo.jobName = "VibeRack Label \(currentLabel.positionCode)"
        controller.printInfo = printInfo
        controller.printingItem = renderedImage
        controller.present(animated: true)
    }
}

private struct LabelShareItem: Identifiable {
    let id = UUID()
    let image: UIImage
}

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {
    }
}

#Preview {
    NavigationStack {
        PrinterView()
    }
}
