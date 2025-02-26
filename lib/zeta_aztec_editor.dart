import 'package:zeta_aztec_editor/src/messages.g.dart';

export 'src/messages.g.dart' hide AztecEditorApi, AztecFlutterApi;

abstract class ZetaAztecEditorCallbacks implements AztecFlutterApi {}

class ZetaAztecEditor implements AztecFlutterApi {
  final _api = AztecEditorApi();

  static final ZetaAztecEditor _instance = ZetaAztecEditor._();

  static final _callbacks = <String, ZetaAztecEditorCallbacks>{};

  ZetaAztecEditor._() {
    AztecFlutterApi.setUp(this);
  }

  factory ZetaAztecEditor() {
    return _instance;
  }

  Future<String?> launch({
    String? initialHtml,
    required AztecEditorConfig config,
    ZetaAztecEditorCallbacks? callback,
  }) async {
    final editorToken = DateTime.now().microsecondsSinceEpoch.toString();
    try {
      if (callback != null) _callbacks[editorToken] = callback;
      final result = await _api.launch(initialHtml: initialHtml, editorToken: editorToken, config: config);
      return result;
    } catch (e) {
      return initialHtml;
    } finally {
      _callbacks.remove(editorToken);
    }
  }

  @override
  Future<String?> onFileSelected(String editorToken, String filePath) {
    return _callbacks[editorToken]?.onFileSelected(editorToken, filePath) ?? Future.value(null);
  }
}
