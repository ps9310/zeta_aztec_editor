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
  heading1,
  heading2,
  heading3,
  heading4,
  heading5,
  heading6,
  orderedList,
  unorderedList,
  blockQuote,
  alignLeft,
  alignCenter,
  alignRight,
  alignJustify,
  textColor,
  backgroundColor,
  link,
  image,
  video,
  horizontalRule,
  removeFormat,
}

class EditorConfig {
  String? primaryColor;
  String? backgroundColor;
  String? textColor;
  List<String>? fileExtensions;
  List<ToolbarOptions>? toolbarOptions;
}

@HostApi()
abstract class AztecEditorApi {
  @async
  String launch(String? initialHtml, {EditorConfig? config});
}

@FlutterApi()
abstract class AztecFlutterApi {
  @async
  String onFileSelected(String filePath);
}
