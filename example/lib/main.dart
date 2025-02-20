import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:zeta_aztec_editor/zeta_aztec_editor.dart' as editor;
import 'package:flutter_widget_from_html/flutter_widget_from_html.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
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
    _launchEditor(editor.Theme.system);
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> _launchEditor(editor.Theme theme) async {
    String html;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      html = await editor.ZetaAztecEditor().launch(
            initialHtml: _html,
            config: editor.EditorConfig(
              theme: theme,
              title: 'Add HTML',
            ),
          ) ??
          '<p>Unknown</p>';
    } on Exception {
      html = '<p>Failed to get platform version.</p>';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _html = html;
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
            ElevatedButton(
              onPressed: () => _launchEditor(editor.Theme.light),
              child: const Text('System theme editor'),
            ),
            ElevatedButton(
              onPressed: () => _launchEditor(editor.Theme.light),
              child: const Text('Light theme editor'),
            ),
            ElevatedButton(
              onPressed: () => _launchEditor(editor.Theme.dark),
              child: const Text('Dark theme editor'),
            ),
          ],
        ),
      ),
    );
  }
}
