import Flutter
import UIKit

public class ZetaAztecEditorPlugin: NSObject, FlutterPlugin, AztecEditorApi {
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = ZetaAztecEditorPlugin()
        AztecEditorApiSetup.setUp(binaryMessenger: registrar.messenger(), api: instance)
    }
    
    func launch(
        initialHtml: String?,
        editorToken: String,
        config: AztecEditorConfig,
        completion: @escaping (Result<String?, any Error>) -> Void
    ) {
        let controller = EditorDemoController(
            initialHtml: initialHtml,
            editorToken: editorToken,
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
