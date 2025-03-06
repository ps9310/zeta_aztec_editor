import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    // Platform interface for shared Dart code
    dartOut: 'lib/src/messages.g.dart',
    dartOptions: DartOptions(),
    // Kotlin for Android
    kotlinOut: 'android/src/main/kotlin/com/zebradevs/aztec/editor/messages/Messages.g.kt',
    kotlinOptions: KotlinOptions(package: 'com.zebradevs.aztec.editor.messages'),
    // Swift for iOS
    swiftOut: 'ios/Classes/Messages.g.swift',
    swiftOptions: SwiftOptions(),
    // C++ for Windows
    // cppHeaderOut: 'windows/runner/messages.g.h',
    // cppSourceOut: 'windows/runner/messages.g.cpp',
    // cppOptions: CppOptions(namespace: 'comms_kit'),
    // GObject for Linux
    // gobjectHeaderOut: 'linux/messages.g.h',
    // gobjectSourceOut: 'linux/messages.g.cc',
    // gobjectOptions: GObjectOptions(),
    // Objective-C for macOS
    // objcHeaderOut: 'macos/Classes/messages.g.h',
    // objcSourceOut: 'macos/Classes/messages.g.m',
    // objcOptions: ObjcOptions(prefix: 'ZAE'),
  ),
)
enum AztecToolbarOption {
  heading,
  bold,
  italic,
  underline,
  strikethrough,
  unorderedList,
  orderedList,
  quote,
  link,
  code,
  horizontalRule,
  image,
  video,
}

enum AztecEditorTheme {
  light,
  dark,
  system,
}

class AztecEditorConfig {
  final String title;
  final String? primaryColor;
  final String? backgroundColor;
  final String? textColor;
  final String? placeholder;
  final int? characterLimit;
  final AztecEditorTheme? theme;
  final List<AztecToolbarOption>? toolbarOptions;
  final Map<String, String>? authHeaders;

  AztecEditorConfig({
    required this.title,
    this.primaryColor,
    this.placeholder,
    this.characterLimit,
    this.backgroundColor,
    this.textColor,
    this.toolbarOptions,
    this.authHeaders,
    this.theme,
  });
}

@HostApi()
abstract class AztecEditorApi {
  @async
  String? launch({String? initialHtml, required AztecEditorConfig config});
}

@FlutterApi()
abstract class AztecFlutterApi {
  @async
  String? onAztecFileSelected(String filePath);
  void onAztecFileDeleted(String filePath);
  void onAztecHtmlChanged(String data);
}
