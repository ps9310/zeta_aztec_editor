import Flutter
import UIKit

public class ZetaAztecEditorPlugin: NSObject, FlutterPlugin, AztecEditorApi {
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        AztecEditorApiSetup.setUp(binaryMessenger: registrar.messenger(), api: ZetaAztecEditorPlugin())
        if AztecFlutterContainer.shared.flutterApi == nil {
            AztecFlutterContainer.shared.flutterApi = AztecFlutterApi(binaryMessenger: registrar.messenger())
        }
    }
    
    func launch(
        initialHtml: String?,
        config: AztecEditorConfig,
        completion: @escaping (Result<String?, any Error>) -> Void
    ) {
        let controller = AztecEditorController(
            initialHtml: initialHtml,
            config: config,
            completion: completion
        )
        
        let navController = UINavigationController(rootViewController: controller)
        navController.modalPresentationStyle = .fullScreen
        
        
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let keyWindow = windowScene.windows.first(where: { $0.isKeyWindow }) {
            keyWindow.rootViewController?.present(navController, animated: true, completion: nil)
        } else {
            completion(.failure(NSError(domain: "ZetaAztecEditorPlugin", code: 1, userInfo: nil)))
        }
    }
}
