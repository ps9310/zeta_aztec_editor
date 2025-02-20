import 'package:zeta_aztec_editor/src/messages.g.dart';

class ZetaAztecEditor {
  final _api = AztecEditorApi();

  Future<String> launch(String? initialHtml, {EditorConfig? config}) {
    return _api.launch(initialHtml, config: config);
  }
}
