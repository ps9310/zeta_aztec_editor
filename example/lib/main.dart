// ignore_for_file: library_private_types_in_public_api

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';

import 'package:zeta_aztec_editor/zeta_aztec_editor.dart';
import 'package:flutter_widget_from_html/flutter_widget_from_html.dart';

void main() {
  runApp(const MyApp());
}

class EditorConfigSettings {
  String placeholder;
  String title;
  int characterLimit;
  String authToken;
  bool simulateUploadSuccess;

  EditorConfigSettings({
    required this.placeholder,
    required this.title,
    required this.characterLimit,
    required this.authToken,
    required this.simulateUploadSuccess,
  });
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: 'Plugin Example App',
      home: EditorPage(),
    );
  }
}

class EditorPage extends StatefulWidget {
  const EditorPage({super.key});

  @override
  _EditorPageState createState() => _EditorPageState();
}

class _EditorPageState extends State<EditorPage> implements ZetaAztecEditorCallbacks {
  String _html = '''
  <h3>Heading</h3>
<p>A paragraph with <strong>strong</strong>, <em>emphasized</em> text. </p>
<ul>
  <li>One</li>
  <li>Two</li>
  <li>Three</li>
  <li>Four</li>
</ul>
<p>This is now very important to get your attention on this topic</p>
<p><img src="https://picsum.photos/id/237/900/1200" class="alignnone size-full"></p>
  ''';

  EditorConfigSettings _configSettings = EditorConfigSettings(
    placeholder: 'Hint from flutter...',
    title: 'Add Instructions',
    characterLimit: 150,
    authToken: 'lksjdhfisdujkmspodfnjdkg',
    simulateUploadSuccess: true,
  );

  late TextEditingController _placeholderController;
  late TextEditingController _titleController;
  late TextEditingController _characterLimitController;
  late TextEditingController _authHeaderController;

  @override
  void initState() {
    super.initState();
    _placeholderController = TextEditingController(text: _configSettings.placeholder);
    _titleController = TextEditingController(text: _configSettings.title);
    _characterLimitController = TextEditingController(text: _configSettings.characterLimit.toString());
    _authHeaderController = TextEditingController(text: _configSettings.authToken);
  }

  @override
  void dispose() {
    _placeholderController.dispose();
    _titleController.dispose();
    _characterLimitController.dispose();
    _authHeaderController.dispose();
    super.dispose();
  }

  Future<void> _launchEditor(AztecEditorTheme theme) async {
    final int charLimit = int.tryParse(_characterLimitController.text) ?? 30;

    _configSettings = EditorConfigSettings(
      placeholder: _placeholderController.text,
      title: _titleController.text,
      characterLimit: charLimit,
      authToken: _authHeaderController.text,
      simulateUploadSuccess: _configSettings.simulateUploadSuccess,
    );

    final config = AztecEditorConfig(
      placeholder: _configSettings.placeholder,
      theme: theme,
      title: _configSettings.title,
      characterLimit: _configSettings.characterLimit,
      toolbarOptions: AztecToolbarOption.values,
      authHeaders: {
        'Authorization': 'Bearer ${_configSettings.authToken}',
      },
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('Editor Page'),
      ),
      drawer: Drawer(
        child: Column(
          children: [
            Expanded(
              child: ListView(
                padding: EdgeInsets.zero,
                children: [
                  DrawerHeader(
                    decoration: BoxDecoration(color: Theme.of(context).colorScheme.primary),
                    child: Text(
                      'Editor Configuration',
                      style: TextStyle(color: Theme.of(context).colorScheme.onPrimary, fontSize: 24),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: TextField(
                      controller: _placeholderController,
                      decoration: const InputDecoration(labelText: 'Editor Placeholder'),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: TextField(
                      controller: _titleController,
                      decoration: const InputDecoration(labelText: 'Editor Title'),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: TextField(
                      controller: _characterLimitController,
                      decoration: const InputDecoration(labelText: 'Character Limit'),
                      keyboardType: TextInputType.number,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: TextField(
                      controller: _authHeaderController,
                      decoration: const InputDecoration(labelText: 'Auth Token'),
                    ),
                  ),
                  SwitchListTile(
                    title: const Text('Upload Success'),
                    value: _configSettings.simulateUploadSuccess,
                    onChanged: (val) {
                      setState(() {
                        _configSettings.simulateUploadSuccess = val;
                      });
                    },
                  ),
                ],
              ),
            ),
            ListTile(
              title: ElevatedButton(
                onPressed: () {
                  Navigator.of(context).pop();
                },
                child: const Text('Done'),
              ),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(8.0),
              child: HtmlWidget(
                _html,
                renderMode: RenderMode.listView,
              ),
            ),
          ),
          SafeArea(
            child: Row(
              children: [
                Expanded(
                  child: TextButton(
                    onPressed: () {
                      Navigator.of(context).maybePop();
                      _launchEditor(AztecEditorTheme.system);
                    },
                    child: const Text('System'),
                  ),
                ),
                Expanded(
                  child: TextButton(
                    onPressed: () {
                      Navigator.of(context).maybePop();
                      _launchEditor(AztecEditorTheme.light);
                    },
                    child: const Text('Light'),
                  ),
                ),
                Expanded(
                  child: TextButton(
                    onPressed: () {
                      Navigator.of(context).maybePop();
                      _launchEditor(AztecEditorTheme.dark);
                    },
                    child: const Text('Dark'),
                  ),
                ),
              ],
            ),
          )
        ],
      ),
    );
  }

  @override
  Future<String?> onAztecFileSelected(String filePath) async {
    debugPrint('EditorPage:onAztecFileSelected: $filePath');
    await Future.delayed(const Duration(seconds: 1));
    if (_configSettings.simulateUploadSuccess) {
      return 'https://picsum.photos/900/1200?random=${DateTime.now().millisecondsSinceEpoch}';
    } else {
      return "The file size exceeds the 5 MB limit.";
    }
  }

  @override
  void onAztecFileDeleted(String filePath) {
    debugPrint('EditorPage:onAztecFileDeleted: $filePath');
  }

  @override
  void onAztecHtmlChanged(String data) {
    debugPrint('EditorPage:onAztecHtmlChanged: $data');
  }
}
