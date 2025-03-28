import 'package:flutter/foundation.dart';
import 'package:zeta_aztec_editor/src/messages.g.dart';

export 'src/messages.g.dart' hide AztecEditorApi, AztecFlutterApi;

abstract class ZetaAztecFileCallbacks {
  Future<String?> onAztecFileSelected(String filePath);

  void onAztecFileDeleted(String filePath);
}

class ZetaAztecEditor implements AztecFlutterApi {
  final _api = AztecEditorApi();

  static final ZetaAztecEditor _instance = ZetaAztecEditor._();

  static ZetaAztecFileCallbacks? _callbacks;

  static ValueChanged<String>? _onAztecHtmlChanged;

  ZetaAztecEditor._();

  factory ZetaAztecEditor() {
    return _instance;
  }

  void ensureInitialized() => AztecFlutterApi.setUp(this);

  Future<String?> launch({
    String? initialHtml,
    required AztecEditorConfig config,
    ZetaAztecFileCallbacks? fileCallbacks,
    ValueChanged<String>? onHtmlChanged,
  }) async {
    try {
      ensureInitialized();
      _onAztecHtmlChanged = onHtmlChanged;
      _callbacks = fileCallbacks;
      final result = await _api.launch(initialHtml: initialHtml, config: config);
      return result;
    } catch (e) {
      return initialHtml;
    } finally {
      _onAztecHtmlChanged = null;
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
    _onAztecHtmlChanged?.call(data);
  }
}
