// Autogenerated from Pigeon (v24.2.1), do not edit directly.
// See also: https://pub.dev/packages/pigeon
// ignore_for_file: public_member_api_docs, non_constant_identifier_names, avoid_as, unused_import, unnecessary_parenthesis, prefer_null_aware_operators, omit_local_variable_types, unused_shown_name, unnecessary_import, no_leading_underscores_for_local_identifiers

import 'dart:async';
import 'dart:typed_data' show Float64List, Int32List, Int64List, Uint8List;

import 'package:flutter/foundation.dart' show ReadBuffer, WriteBuffer;
import 'package:flutter/services.dart';

PlatformException _createConnectionError(String channelName) {
  return PlatformException(
    code: 'channel-error',
    message: 'Unable to establish connection on channel: "$channelName".',
  );
}

List<Object?> wrapResponse({Object? result, PlatformException? error, bool empty = false}) {
  if (empty) {
    return <Object?>[];
  }
  if (error == null) {
    return <Object?>[result];
  }
  return <Object?>[error.code, error.message, error.details];
}

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
  AztecEditorConfig({
    required this.title,
    this.primaryColor,
    this.backgroundColor,
    this.textColor,
    this.placeholder,
    this.theme,
    this.fileExtensions,
    this.toolbarOptions,
    this.authHeaders,
  });

  String title;

  String? primaryColor;

  String? backgroundColor;

  String? textColor;

  String? placeholder;

  AztecEditorTheme? theme;

  List<String>? fileExtensions;

  List<AztecToolbarOption>? toolbarOptions;

  Map<String, String>? authHeaders;

  Object encode() {
    return <Object?>[
      title,
      primaryColor,
      backgroundColor,
      textColor,
      placeholder,
      theme,
      fileExtensions,
      toolbarOptions,
      authHeaders,
    ];
  }

  static AztecEditorConfig decode(Object result) {
    result as List<Object?>;
    return AztecEditorConfig(
      title: result[0]! as String,
      primaryColor: result[1] as String?,
      backgroundColor: result[2] as String?,
      textColor: result[3] as String?,
      placeholder: result[4] as String?,
      theme: result[5] as AztecEditorTheme?,
      fileExtensions: (result[6] as List<Object?>?)?.cast<String>(),
      toolbarOptions: (result[7] as List<Object?>?)?.cast<AztecToolbarOption>(),
      authHeaders: (result[8] as Map<Object?, Object?>?)?.cast<String, String>(),
    );
  }
}


class _PigeonCodec extends StandardMessageCodec {
  const _PigeonCodec();
  @override
  void writeValue(WriteBuffer buffer, Object? value) {
    if (value is int) {
      buffer.putUint8(4);
      buffer.putInt64(value);
    }    else if (value is AztecToolbarOption) {
      buffer.putUint8(129);
      writeValue(buffer, value.index);
    }    else if (value is AztecEditorTheme) {
      buffer.putUint8(130);
      writeValue(buffer, value.index);
    }    else if (value is AztecEditorConfig) {
      buffer.putUint8(131);
      writeValue(buffer, value.encode());
    } else {
      super.writeValue(buffer, value);
    }
  }

  @override
  Object? readValueOfType(int type, ReadBuffer buffer) {
    switch (type) {
      case 129: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : AztecToolbarOption.values[value];
      case 130: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : AztecEditorTheme.values[value];
      case 131: 
        return AztecEditorConfig.decode(readValue(buffer)!);
      default:
        return super.readValueOfType(type, buffer);
    }
  }
}

class AztecEditorApi {
  /// Constructor for [AztecEditorApi].  The [binaryMessenger] named argument is
  /// available for dependency injection.  If it is left null, the default
  /// BinaryMessenger will be used which routes to the host platform.
  AztecEditorApi({BinaryMessenger? binaryMessenger, String messageChannelSuffix = ''})
      : pigeonVar_binaryMessenger = binaryMessenger,
        pigeonVar_messageChannelSuffix = messageChannelSuffix.isNotEmpty ? '.$messageChannelSuffix' : '';
  final BinaryMessenger? pigeonVar_binaryMessenger;

  static const MessageCodec<Object?> pigeonChannelCodec = _PigeonCodec();

  final String pigeonVar_messageChannelSuffix;

  Future<String?> launch({String? initialHtml, required AztecEditorConfig config}) async {
    final String pigeonVar_channelName = 'dev.flutter.pigeon.zeta_aztec_editor.AztecEditorApi.launch$pigeonVar_messageChannelSuffix';
    final BasicMessageChannel<Object?> pigeonVar_channel = BasicMessageChannel<Object?>(
      pigeonVar_channelName,
      pigeonChannelCodec,
      binaryMessenger: pigeonVar_binaryMessenger,
    );
    final Future<Object?> pigeonVar_sendFuture = pigeonVar_channel.send(<Object?>[initialHtml, config]);
    final List<Object?>? pigeonVar_replyList =
        await pigeonVar_sendFuture as List<Object?>?;
    if (pigeonVar_replyList == null) {
      throw _createConnectionError(pigeonVar_channelName);
    } else if (pigeonVar_replyList.length > 1) {
      throw PlatformException(
        code: pigeonVar_replyList[0]! as String,
        message: pigeonVar_replyList[1] as String?,
        details: pigeonVar_replyList[2],
      );
    } else {
      return (pigeonVar_replyList[0] as String?);
    }
  }
}

abstract class AztecFlutterApi {
  static const MessageCodec<Object?> pigeonChannelCodec = _PigeonCodec();

  Future<String?> onFileSelected(String filePath);

  void onFileDeleted(String filePath);

  static void setUp(AztecFlutterApi? api, {BinaryMessenger? binaryMessenger, String messageChannelSuffix = '',}) {
    messageChannelSuffix = messageChannelSuffix.isNotEmpty ? '.$messageChannelSuffix' : '';
    {
      final BasicMessageChannel<Object?> pigeonVar_channel = BasicMessageChannel<Object?>(
          'dev.flutter.pigeon.zeta_aztec_editor.AztecFlutterApi.onFileSelected$messageChannelSuffix', pigeonChannelCodec,
          binaryMessenger: binaryMessenger);
      if (api == null) {
        pigeonVar_channel.setMessageHandler(null);
      } else {
        pigeonVar_channel.setMessageHandler((Object? message) async {
          assert(message != null,
          'Argument for dev.flutter.pigeon.zeta_aztec_editor.AztecFlutterApi.onFileSelected was null.');
          final List<Object?> args = (message as List<Object?>?)!;
          final String? arg_filePath = (args[0] as String?);
          assert(arg_filePath != null,
              'Argument for dev.flutter.pigeon.zeta_aztec_editor.AztecFlutterApi.onFileSelected was null, expected non-null String.');
          try {
            final String? output = await api.onFileSelected(arg_filePath!);
            return wrapResponse(result: output);
          } on PlatformException catch (e) {
            return wrapResponse(error: e);
          }          catch (e) {
            return wrapResponse(error: PlatformException(code: 'error', message: e.toString()));
          }
        });
      }
    }
    {
      final BasicMessageChannel<Object?> pigeonVar_channel = BasicMessageChannel<Object?>(
          'dev.flutter.pigeon.zeta_aztec_editor.AztecFlutterApi.onFileDeleted$messageChannelSuffix', pigeonChannelCodec,
          binaryMessenger: binaryMessenger);
      if (api == null) {
        pigeonVar_channel.setMessageHandler(null);
      } else {
        pigeonVar_channel.setMessageHandler((Object? message) async {
          assert(message != null,
          'Argument for dev.flutter.pigeon.zeta_aztec_editor.AztecFlutterApi.onFileDeleted was null.');
          final List<Object?> args = (message as List<Object?>?)!;
          final String? arg_filePath = (args[0] as String?);
          assert(arg_filePath != null,
              'Argument for dev.flutter.pigeon.zeta_aztec_editor.AztecFlutterApi.onFileDeleted was null, expected non-null String.');
          try {
            api.onFileDeleted(arg_filePath!);
            return wrapResponse(empty: true);
          } on PlatformException catch (e) {
            return wrapResponse(error: e);
          }          catch (e) {
            return wrapResponse(error: PlatformException(code: 'error', message: e.toString()));
          }
        });
      }
    }
  }
}
