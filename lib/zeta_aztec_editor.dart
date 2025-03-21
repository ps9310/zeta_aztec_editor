import 'package:zeta_aztec_editor/src/messages.g.dart';

export 'src/messages.g.dart' hide AztecEditorApi, AztecFlutterApi;

abstract class ZetaAztecEditorCallbacks {
  Future<String?> onAztecFileSelected(String filePath);
  void onAztecFileDeleted(String filePath);
  void onAztecHtmlChanged(String data);
}

class ZetaAztecEditor implements AztecFlutterApi {
  final _api = AztecEditorApi();

  static final ZetaAztecEditor _instance = ZetaAztecEditor._();

  static ZetaAztecEditorCallbacks? _callbacks;

  ZetaAztecEditor._();

  factory ZetaAztecEditor() {
    return _instance;
  }

  void ensureInitialized() => AztecFlutterApi.setUp(this);

  Future<String?> launch({
    String? initialHtml,
    required AztecEditorConfig config,
    required ZetaAztecEditorCallbacks callback,
  }) async {
    try {
      ensureInitialized();
      _callbacks = callback;
      final result = await _api.launch(initialHtml: initialHtml, config: config);
      return result;
    } catch (e) {
      return initialHtml;
    } finally {
      _callbacks = null;
    }
  }

  @override
  Future<String?> onAztecFileSelected(String filePath) {
    return _callbacks?.onAztecFileSelected(filePath) ?? Future.value(null);
  }

  @override
  void onAztecFileDeleted(String filePath) {
    _callbacks?.onAztecFileDeleted(filePath);
  }

  @override
  void onAztecHtmlChanged(String data) {
    _callbacks?.onAztecHtmlChanged(data);
  }
}
