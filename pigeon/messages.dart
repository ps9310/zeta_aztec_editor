// ignore_for_file: public_member_api_docs, sort_constructors_first
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
enum ToolbarOptions {
  bold,
  italic,
  underline,
  strikeThrough,
  heading,
  orderedList,
  unorderedList,
  blockQuote,
  alignLeft,
  alignCenter,
  alignRight,
  link,
  image,
  video,
  horizontalRule,
}

enum Theme {
  light,
  dark,
  system,
}

class EditorConfig {
  final String? primaryColor;
  final String? backgroundColor;
  final String? textColor;
  final String? placeholder;
  final List<String>? fileExtensions;
  final List<ToolbarOptions>? toolbarOptions;
  final String title;
  final Theme theme;

  EditorConfig({
    required this.title,
    required this.primaryColor,
    required this.placeholder,
    required this.backgroundColor,
    required this.textColor,
    required this.fileExtensions,
    required this.toolbarOptions,
    this.theme = Theme.system,
  });
}

@HostApi()
abstract class AztecEditorApi {
  @async
  String? launch(String? initialHtml, {required EditorConfig config});
}

@FlutterApi()
abstract class AztecFlutterApi {
  @async
  String onFileSelected(String filePath);
}
