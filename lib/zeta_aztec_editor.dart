import 'package:zeta_aztec_editor/src/messages.g.dart';

export 'src/messages.g.dart' show EditorConfig, Theme, ToolbarOptions;

class ZetaAztecEditor {
  final _api = AztecEditorApi();

  static final ZetaAztecEditor _instance = ZetaAztecEditor._();

  ZetaAztecEditor._();

  factory ZetaAztecEditor() {
    return _instance;
  }

  Future<String?> launch({String? initialHtml, required EditorConfig config}) async {
    return _api.launch(initialHtml, config: config);
  }
}
