// ignore_for_file: public_member_api_docs, sort_constructors_first, constant_identifier_names
import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    // Platform interface for shared Dart code
    dartOut: 'lib/src/messages.g.dart',
    dartOptions: DartOptions(),
    // Kotlin for Android
    kotlinOut: 'android/src/main/kotlin/com/zebradevs/aztec/messages/Messages.g.kt',
    kotlinOptions: KotlinOptions(package: 'com.zebradevs.aztec'),
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
  // Text Styling
  BOLD,
  ITALIC,
  UNDERLINE,
  STRIKETHROUGH,

  // Headings & Block Styles
  HEADING,

  // Lists & Related Controls
  LIST,
  UNORDERED_LIST,
  ORDERED_LIST,
  TASK_LIST,

  // Indentation & Alignment
  INDENT,
  OUTDENT,
  ALIGN_LEFT,
  ALIGN_CENTER,
  ALIGN_RIGHT,

  // Additional Formatting
  QUOTE,
  LINK,

  // Code & Preformatted Text
  CODE,
  PREFORMAT,

  // Media & Dividers
  HORIZONTAL_RULE,
  IMAGE,
  VIDEO,
}

enum AztecEditorTheme {
  light,
  dark,
  system,
}

class AztecEditorConfig {
  final String? primaryColor;
  final String? backgroundColor;
  final String? textColor;
  final String? placeholder;
  final List<String>? fileExtensions;
  final List<AztecToolbarOption>? toolbarOptions;
  final String title;
  final AztecEditorTheme theme;

  AztecEditorConfig({
    required this.title,
    required this.primaryColor,
    required this.placeholder,
    required this.backgroundColor,
    required this.textColor,
    required this.fileExtensions,
    required this.toolbarOptions,
    this.theme = AztecEditorTheme.system,
  });
}

@HostApi()
abstract class AztecEditorApi {
  @async
  String? launch({String? initialHtml, required String editorToken, required AztecEditorConfig config});
}

@FlutterApi()
abstract class AztecFlutterApi {
  @async
  String? onFileSelected(String editorToken, String filePath);
}
