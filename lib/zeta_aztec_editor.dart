import 'package:zeta_aztec_editor/src/messages.g.dart';

export 'src/messages.g.dart' hide AztecEditorApi, AztecFlutterApi;

abstract class ZetaAztecEditorCallbacks {
  Future<String?> onAztecFileSelected(String filePath);

  void onAztecFileDeleted(String filePath);
}

class ZetaAztecEditor implements AztecFlutterApi {
  final _api = AztecEditorApi();

  static final ZetaAztecEditor _instance = ZetaAztecEditor._();

  static ZetaAztecEditorCallbacks? _callbacks;

  ZetaAztecEditor._() {
    AztecFlutterApi.setUp(this);
  }

  factory ZetaAztecEditor() {
    return _instance;
  }

  Future<String?> launch({
    String? initialHtml,
    required AztecEditorConfig config,
    required ZetaAztecEditorCallbacks callback,
  }) async {
    try {
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
  Future<String?> onFileSelected(String filePath) {
    return _callbacks?.onAztecFileSelected(filePath) ?? Future.value(null);
  }

  @override
  void onFileDeleted(String filePath) {
    _callbacks?.onAztecFileDeleted(filePath);
  }
}
