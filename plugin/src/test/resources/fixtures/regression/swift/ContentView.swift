import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var viewModel = HomeViewModel()

    var body: some View {
        HomeScreen(viewModel: viewModel)
    }
}

class HomeViewModel: ObservableObject {
    private let cameraController: CameraRegistry
    private let subscriptionRepository: SubscriptionRepository

    init() {
        self.cameraController = CameraRegistry.shared
        self.subscriptionRepository = /* injected */
    }

    func startCamera() {
        cameraController.cameraController?.startCamera()
    }

    func takePicture() {
        CameraCaptureBridge.shared.takePicture?()
    }

    func confirmCapture() {
        CameraCaptureBridge.shared.confirmCapture?()
    }

    func applyFilter(_ name: String) {
        FilterApplyBridge.shared.applyLutToFile?(name, "/tmp/output.lut")
    }

    func setZoom(_ ratio: Float) {
        ZoomBridge.shared.setZoomRatio?(ratio)
    }

    func setExposure(_ bias: Float) {
        ExposureBridge.shared.setExposureBias?(bias)
    }

    func triggerFlash() {
        FlashBridge.shared.triggerFlash?()
    }

    func handleShutter() {
        HardwareShutterHandler.shared.onShutterClick?()
    }
}
