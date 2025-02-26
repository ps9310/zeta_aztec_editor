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
        completion(.success((initialHtml ?? "") + "\n\nThis is added from the native code."))
        
        
        
        let controller = EditorDemoController(withSampleHTML: initialHtml, wordPressMode: true)
        
        UIApplication.shared.windows.first?.rootViewController?.present(controller, animated: true)
        
    }
    
}
