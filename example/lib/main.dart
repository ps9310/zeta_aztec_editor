import 'package:flutter/material.dart';
import 'dart:async';

import 'package:zeta_aztec_editor/zeta_aztec_editor.dart';
import 'package:flutter_widget_from_html/flutter_widget_from_html.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> implements ZetaAztecEditorCallbacks {
  String _html = '''
  <h3>Heading</h3>
  <p>
    A paragraph with <strong>strong</strong>, <em>emphasized</em>
    and <span style="color: red">colored</span> text.
  </p>
  ''';

  @override
  void initState() {
    super.initState();
    _launchEditor(AztecEditorTheme.system);
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> _launchEditor(AztecEditorTheme theme) async {
    final config = AztecEditorConfig(
      placeholder: 'Hint from flutter...',
      theme: theme,
      title: 'Add Instructions',
      toolbarOptions: [
        ...AztecToolbarOption.values,
      ]..remove(AztecToolbarOption.LIST),
    );

    ZetaAztecEditor().launch(initialHtml: _html, config: config, callback: this).then((value) {
      if (mounted && value != null) {
        setState(() {
          _html = value;
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
          children: [
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: HtmlWidget(
                  _html,
                  renderMode: RenderMode.listView,
                ),
              ),
            ),
            Row(
              children: [
                Expanded(
                  child: TextButton(
                    onPressed: () => _launchEditor(AztecEditorTheme.system),
                    child: const Text('System'),
                  ),
                ),
                Expanded(
                  child: TextButton(
                    onPressed: () => _launchEditor(AztecEditorTheme.light),
                    child: const Text('Light'),
                  ),
                ),
                Expanded(
                  child: TextButton(
                    onPressed: () => _launchEditor(AztecEditorTheme.dark),
                    child: const Text('Dark'),
                  ),
                ),
              ],
            )
          ],
        ),
      ),
    );
  }

  @override
  Future<String?> onAztecFileSelected(String filePath) async {
    await Future.delayed(const Duration(seconds: 1));
    return 'https://picsum.photos/id/237/200/300'; // upload success
    // return null; // upload failed
  }

  @override
  void onAztecFileDeleted(String filePath) {
    // Delete the file from the server
  }
}
