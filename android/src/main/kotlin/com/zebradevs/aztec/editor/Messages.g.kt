// Autogenerated from Pigeon (v24.2.1), do not edit directly.
// See also: https://pub.dev/packages/pigeon
@file:Suppress("UNCHECKED_CAST", "ArrayInDataClass")

package com.zebradevs.aztec.editor

import android.util.Log
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MessageCodec
import io.flutter.plugin.common.StandardMethodCodec
import io.flutter.plugin.common.StandardMessageCodec
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private fun wrapResult(result: Any?): List<Any?> {
  return listOf(result)
}

private fun wrapError(exception: Throwable): List<Any?> {
  return if (exception is FlutterError) {
    listOf(
      exception.code,
      exception.message,
      exception.details
    )
  } else {
    listOf(
      exception.javaClass.simpleName,
      exception.toString(),
      "Cause: " + exception.cause + ", Stacktrace: " + Log.getStackTraceString(exception)
    )
  }
}

private fun createConnectionError(channelName: String): FlutterError {
  return FlutterError("channel-error",  "Unable to establish connection on channel: '$channelName'.", "")}

/**
 * Error class for passing custom error details to Flutter via a thrown PlatformException.
 * @property code The error code.
 * @property message The error message.
 * @property details The error details. Must be a datatype supported by the api codec.
 */
class FlutterError (
  val code: String,
  override val message: String? = null,
  val details: Any? = null
) : Throwable()

enum class AztecToolbarOption(val raw: Int) {
  HEADING(0),
  BOLD(1),
  ITALIC(2),
  UNDERLINE(3),
  STRIKETHROUGH(4),
  UNORDERED_LIST(5),
  ORDERED_LIST(6),
  QUOTE(7),
  LINK(8),
  CODE(9),
  HORIZONTAL_RULE(10),
  IMAGE(11),
  VIDEO(12);

  companion object {
    fun ofRaw(raw: Int): AztecToolbarOption? {
      return values().firstOrNull { it.raw == raw }
    }
  }
}

enum class AztecEditorTheme(val raw: Int) {
  LIGHT(0),
  DARK(1),
  SYSTEM(2);

  companion object {
    fun ofRaw(raw: Int): AztecEditorTheme? {
      return values().firstOrNull { it.raw == raw }
    }
  }
}

/** Generated class from Pigeon that represents data sent in messages. */
data class AztecEditorConfig (
  val title: String,
  val primaryColor: String? = null,
  val backgroundColor: String? = null,
  val textColor: String? = null,
  val placeholder: String? = null,
  val theme: AztecEditorTheme? = null,
  val fileExtensions: List<String>? = null,
  val toolbarOptions: List<AztecToolbarOption>? = null,
  val authHeaders: Map<String, String>? = null
)
 {
  companion object {
    fun fromList(pigeonVar_list: List<Any?>): AztecEditorConfig {
      val title = pigeonVar_list[0] as String
      val primaryColor = pigeonVar_list[1] as String?
      val backgroundColor = pigeonVar_list[2] as String?
      val textColor = pigeonVar_list[3] as String?
      val placeholder = pigeonVar_list[4] as String?
      val theme = pigeonVar_list[5] as AztecEditorTheme?
      val fileExtensions = pigeonVar_list[6] as List<String>?
      val toolbarOptions = pigeonVar_list[7] as List<AztecToolbarOption>?
      val authHeaders = pigeonVar_list[8] as Map<String, String>?
      return AztecEditorConfig(title, primaryColor, backgroundColor, textColor, placeholder, theme, fileExtensions, toolbarOptions, authHeaders)
    }
  }
  fun toList(): List<Any?> {
    return listOf(
      title,
      primaryColor,
      backgroundColor,
      textColor,
      placeholder,
      theme,
      fileExtensions,
      toolbarOptions,
      authHeaders,
    )
  }
}
private open class MessagesPigeonCodec : StandardMessageCodec() {
  override fun readValueOfType(type: Byte, buffer: ByteBuffer): Any? {
    return when (type) {
      129.toByte() -> {
        return (readValue(buffer) as Long?)?.let {
          AztecToolbarOption.ofRaw(it.toInt())
        }
      }
      130.toByte() -> {
        return (readValue(buffer) as Long?)?.let {
          AztecEditorTheme.ofRaw(it.toInt())
        }
      }
      131.toByte() -> {
        return (readValue(buffer) as? List<Any?>)?.let {
          AztecEditorConfig.fromList(it)
        }
      }
      else -> super.readValueOfType(type, buffer)
    }
  }
  override fun writeValue(stream: ByteArrayOutputStream, value: Any?)   {
    when (value) {
      is AztecToolbarOption -> {
        stream.write(129)
        writeValue(stream, value.raw)
      }
      is AztecEditorTheme -> {
        stream.write(130)
        writeValue(stream, value.raw)
      }
      is AztecEditorConfig -> {
        stream.write(131)
        writeValue(stream, value.toList())
      }
      else -> super.writeValue(stream, value)
    }
  }
}


/** Generated interface from Pigeon that represents a handler of messages from Flutter. */
interface AztecEditorApi {
  fun launch(initialHtml: String?, editorToken: String, config: AztecEditorConfig, callback: (Result<String?>) -> Unit)

  companion object {
    /** The codec used by AztecEditorApi. */
    val codec: MessageCodec<Any?> by lazy {
      MessagesPigeonCodec()
    }
    /** Sets up an instance of `AztecEditorApi` to handle messages through the `binaryMessenger`. */
    @JvmOverloads
    fun setUp(binaryMessenger: BinaryMessenger, api: AztecEditorApi?, messageChannelSuffix: String = "") {
      val separatedMessageChannelSuffix = if (messageChannelSuffix.isNotEmpty()) ".$messageChannelSuffix" else ""
      run {
        val channel = BasicMessageChannel<Any?>(binaryMessenger, "dev.flutter.pigeon.zeta_aztec_editor.AztecEditorApi.launch$separatedMessageChannelSuffix", codec)
        if (api != null) {
          channel.setMessageHandler { message, reply ->
            val args = message as List<Any?>
            val initialHtmlArg = args[0] as String?
            val editorTokenArg = args[1] as String
            val configArg = args[2] as AztecEditorConfig
            api.launch(initialHtmlArg, editorTokenArg, configArg) { result: Result<String?> ->
              val error = result.exceptionOrNull()
              if (error != null) {
                reply.reply(wrapError(error))
              } else {
                val data = result.getOrNull()
                reply.reply(wrapResult(data))
              }
            }
          }
        } else {
          channel.setMessageHandler(null)
        }
      }
    }
  }
}
/** Generated class from Pigeon that represents Flutter messages that can be called from Kotlin. */
class AztecFlutterApi(private val binaryMessenger: BinaryMessenger, private val messageChannelSuffix: String = "") {
  companion object {
    /** The codec used by AztecFlutterApi. */
    val codec: MessageCodec<Any?> by lazy {
      MessagesPigeonCodec()
    }
  }
  fun onFileSelected(editorTokenArg: String, filePathArg: String, callback: (Result<String?>) -> Unit)
{
    val separatedMessageChannelSuffix = if (messageChannelSuffix.isNotEmpty()) ".$messageChannelSuffix" else ""
    val channelName = "dev.flutter.pigeon.zeta_aztec_editor.AztecFlutterApi.onFileSelected$separatedMessageChannelSuffix"
    val channel = BasicMessageChannel<Any?>(binaryMessenger, channelName, codec)
    channel.send(listOf(editorTokenArg, filePathArg)) {
      if (it is List<*>) {
        if (it.size > 1) {
          callback(Result.failure(FlutterError(it[0] as String, it[1] as String, it[2] as String?)))
        } else {
          val output = it[0] as String?
          callback(Result.success(output))
        }
      } else {
        callback(Result.failure(createConnectionError(channelName)))
      } 
    }
  }
  fun onFileDeleted(editorTokenArg: String, filePathArg: String, callback: (Result<Unit>) -> Unit)
{
    val separatedMessageChannelSuffix = if (messageChannelSuffix.isNotEmpty()) ".$messageChannelSuffix" else ""
    val channelName = "dev.flutter.pigeon.zeta_aztec_editor.AztecFlutterApi.onFileDeleted$separatedMessageChannelSuffix"
    val channel = BasicMessageChannel<Any?>(binaryMessenger, channelName, codec)
    channel.send(listOf(editorTokenArg, filePathArg)) {
      if (it is List<*>) {
        if (it.size > 1) {
          callback(Result.failure(FlutterError(it[0] as String, it[1] as String, it[2] as String?)))
        } else {
          callback(Result.success(Unit))
        }
      } else {
        callback(Result.failure(createConnectionError(channelName)))
      } 
    }
  }
}
