import AVFoundation
import SwiftUI
import VisionKit

/// Full-screen camera that reads the EAN / UPC code on a game box, passes its digits to `onScan`
/// and closes. When the camera can't read the code (or there is none, as in the simulator), the
/// number printed under the barcode can be typed in instead.
struct BarcodeScannerView: View {
    let onScan: (String) -> Void

    @State private var cameraAccess = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var isEnteringCode = false
    @State private var typedCode = ""
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Scan barcode")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    if showsCamera {
                        ToolbarItem(placement: .bottomBar) {
                            Button("Enter the number instead") { isEnteringCode = true }
                        }
                    }
                }
        }
        .alert("Enter barcode", isPresented: $isEnteringCode) {
            TextField("8–14 digits", text: $typedCode)
                .keyboardType(.numberPad)
            Button("Cancel", role: .cancel) { typedCode = "" }
            Button("Look up") { finish(with: typedCode) }
        } message: {
            Text("Type the number printed under the barcode.")
        }
        .task {
            // Without a supported camera there is nothing to ask for.
            guard DataScannerViewController.isSupported, cameraAccess == .notDetermined else { return }
            _ = await AVCaptureDevice.requestAccess(for: .video)
            cameraAccess = AVCaptureDevice.authorizationStatus(for: .video)
        }
    }

    private var showsCamera: Bool {
        DataScannerViewController.isSupported && cameraAccess == .authorized
    }

    @ViewBuilder
    private var content: some View {
        if !DataScannerViewController.isSupported {
            ContentUnavailableView {
                Label("Camera scanning isn't available", systemImage: "barcode.viewfinder")
            } description: {
                Text("This device can't read barcodes with its camera. Type the number printed under the barcode instead.")
            } actions: {
                enterCodeButton
            }
        } else {
            switch cameraAccess {
            case .authorized:
                DataScanner(onScan: finish(with:))
                    .ignoresSafeArea(edges: .bottom)
                    .overlay(alignment: .bottom) {
                        Text("Point the camera at the barcode on the box.")
                            .font(.subheadline.weight(.medium))
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(.regularMaterial, in: .capsule)
                            .padding(.bottom, 24)
                    }
            case .notDetermined:
                ProgressView()
            default:
                ContentUnavailableView {
                    Label("No access to the camera", systemImage: "camera")
                } description: {
                    Text("Allow Game Shelf to use the camera in Settings to scan barcodes.")
                } actions: {
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            openURL(url)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    enterCodeButton
                }
            }
        }
    }

    private var enterCodeButton: some View {
        Button("Enter the number instead") { isEnteringCode = true }
            .buttonStyle(.bordered)
    }

    private func finish(with code: String) {
        let digits = code.filter(\.isASCIIDigit)
        typedCode = ""
        guard !digits.isEmpty else { return }
        onScan(digits)
        dismiss()
    }
}

/// VisionKit's live scanner, limited to the barcode types used on game boxes. Reports the first code
/// and stops. UPC-A is read as EAN-13 with a leading zero.
private struct DataScanner: UIViewControllerRepresentable {
    let onScan: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.ean13, .ean8, .upce])],
            qualityLevel: .balanced,
            isHighlightingEnabled: true
        )
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ scanner: DataScannerViewController, context: Context) {
        context.coordinator.onScan = onScan
        if !scanner.isScanning, !context.coordinator.hasScanned {
            try? scanner.startScanning()
        }
    }

    static func dismantleUIViewController(_ scanner: DataScannerViewController, coordinator: Coordinator) {
        scanner.stopScanning()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onScan: onScan)
    }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var onScan: (String) -> Void
        private(set) var hasScanned = false

        init(onScan: @escaping (String) -> Void) {
            self.onScan = onScan
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !hasScanned else { return }
            for item in addedItems {
                guard case .barcode(let barcode) = item,
                      let code = barcode.payloadStringValue,
                      Barcode.isValid(code) else { continue }
                hasScanned = true
                dataScanner.stopScanning()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                onScan(code)
                return
            }
        }
    }
}
