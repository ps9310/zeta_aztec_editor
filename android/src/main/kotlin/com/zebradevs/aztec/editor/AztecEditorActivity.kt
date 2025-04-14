package com.zebradevs.aztec.editor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.ToggleButton
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import com.zebradevs.aztec.editor.messages.AztecEditorTheme
import com.zebradevs.aztec.editor.messages.AztecToolbarOption
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.PermissionUtils
import org.wordpress.android.util.ToastUtils
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.AztecAttributes
import org.wordpress.aztec.AztecExceptionHandler
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.IHistoryListener
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.plugins.CssUnderlinePlugin
import org.wordpress.aztec.plugins.IMediaToolbarButton
import org.wordpress.aztec.plugins.shortcodes.AudioShortcodePlugin
import org.wordpress.aztec.plugins.shortcodes.CaptionShortcodePlugin
import org.wordpress.aztec.plugins.shortcodes.VideoShortcodePlugin
import org.wordpress.aztec.plugins.shortcodes.extensions.ATTRIBUTE_VIDEOPRESS_HIDDEN_SRC
import org.wordpress.aztec.plugins.wpcomments.HiddenGutenbergPlugin
import org.wordpress.aztec.plugins.wpcomments.WordPressCommentsPlugin
import org.wordpress.aztec.spans.AztecMediaSpan
import org.wordpress.aztec.toolbar.AztecToolbar
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener
import org.wordpress.aztec.toolbar.ToolbarAction
import org.wordpress.aztec.toolbar.ToolbarItems
import org.xml.sax.Attributes
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Random

class AztecEditorActivity : AppCompatActivity(),
    AztecText.OnImeBackListener,
    AztecText.OnImageTappedListener,
    AztecText.OnVideoTappedListener,
    AztecText.OnAudioTappedListener,
    AztecText.OnMediaDeletedListener,
    AztecText.OnVideoInfoRequestedListener,
    IAztecToolbarClickListener,
    IHistoryListener,
    OnRequestPermissionsResultCallback,
    PopupMenu.OnMenuItemClickListener,
    View.OnTouchListener {

    // region Companion & Constants
    companion object {
        const val REQUEST_CODE: Int = 9001
        private const val MEDIA_CAMERA_PHOTO_PERMISSION_REQUEST_CODE = 1001
        private const val MEDIA_CAMERA_VIDEO_PERMISSION_REQUEST_CODE = 1002
        private const val MEDIA_PHOTOS_PERMISSION_REQUEST_CODE = 1003
        private const val MEDIA_VIDEOS_PERMISSION_REQUEST_CODE = 1004

        fun createIntent(
            activity: Activity,
            initialHtml: String?,
            editorConfig: EditorConfig
        ): Intent {
            Log.d(
                "AztecEditorActivity",
                "createIntent: Creating intent with initialHtml and editorConfig"
            )
            return Intent(activity, AztecEditorActivity::class.java).apply {
                putExtra("initialHtml", initialHtml)
                putExtra("editorConfig", editorConfig)
            }
        }
    }
    // endregion

    // region Properties
    private lateinit var aztec: Aztec
    private lateinit var mediaFile: String
    private lateinit var mediaPath: String

    private lateinit var invalidateOptionsHandler: Handler
    private lateinit var invalidateOptionsRunnable: Runnable

    private var mediaUploadDialog: AlertDialog? = null
    private var mediaProgressDialog: AlertDialog? = null
    private var mediaMenu: PopupMenu? = null

    private var mIsKeyboardOpen = false
    private var mHideActionBarOnSoftKeyboardUp = false
    private lateinit var videoOptionLauncher: ActivityResultLauncher<Intent>
    private lateinit var imageCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var imageSelectLauncher: ActivityResultLauncher<Intent>
    private val DEBOUNCE_DELAY: Long = 300
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var editorConfig: EditorConfig? = null
    // endregion

    // region Lifecycle & Setup
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("AztecEditorActivity", "onCreate: Started")
        IntentCompat.getParcelableExtra(intent, "editorConfig", EditorConfig::class.java)?.let {
            editorConfig = it
            Log.d("AztecEditorActivity", "onCreate: EditorConfig loaded")
        }

        setupThemeAndToolbar()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aztec_editor)
        Log.d("AztecEditorActivity", "onCreate: Content view set")

        WindowCompat.setDecorFitsSystemWindows(window, true)

        setupBackPressHandler()
        setupEditorConfiguration()
        setupAztecEditor(savedInstanceState)
        setupInvalidateOptionsHandler()
        Log.d("AztecEditorActivity", "onCreate: Setup methods completed")

        videoOptionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                Log.d("AztecEditorActivity", "videoOptionLauncher: Received result: $result")
                if (result.resultCode == RESULT_OK) {
                    Log.d("AztecEditorActivity", "Video result received: ${result.data}")
                    handleVideoResult(result.data)
                } else {
                    Log.d("AztecEditorActivity", "Video result canceled or failed")
                }
            }

        imageSelectLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                Log.d("AztecEditorActivity", "imageSelectLauncher: Received result: $result")
                if (result.resultCode == RESULT_OK) {
                    Log.d("AztecEditorActivity", "Gallery photo result received: ${result.data}")
                    handleGalleryPhotoResult(result.data)
                } else {
                    Log.d("AztecEditorActivity", "Gallery photo result canceled or failed")
                }
            }

        imageCaptureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                Log.d("AztecEditorActivity", "imageCaptureLauncher: Received result: $result")
                if (result.resultCode == RESULT_OK) {
                    Log.d("AztecEditorActivity", "Camera photo result received")
                    handleCameraPhotoResult()
                } else {
                    Log.d("AztecEditorActivity", "Camera photo result canceled or failed")
                }
            }
        Log.d("AztecEditorActivity", "onCreate: Completed")
    }

    override fun onStart() {
        super.onStart()
        Log.d("AztecEditorActivity", "onStart: Called")
        findViewById<AztecToolbar>(R.id.formatting_toolbar)?.let { toolbar ->
            Log.d("AztecEditorActivity", "onStart: Setting up AztecToolbar")
            setupAztecToolbar(toolbar)
        }

        editorConfig?.characterLimit?.let { limit ->
            if (limit > 0) {
                aztec.visualEditor.post {
                    val text = aztec.visualEditor.text
                    // Delete if text is longer than the character limit.
                    if (text.isNotEmpty() && text.length > limit) {
                        text.delete(limit.toInt(), text.length)
                    }
                    aztec.visualEditor.filters = arrayOf(InputFilter.LengthFilter(limit.toInt()))
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mIsKeyboardOpen = false
        Log.d("AztecEditorActivity", "onPause: Called and keyboard flag reset")
    }

    override fun onResume() {
        super.onResume()
        Log.d("AztecEditorActivity", "onResume: Called")
        showActionBarIfNeeded()

        // Request focus on the EditText
        aztec.visualEditor.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(aztec.visualEditor, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AztecEditorActivity", "onDestroy: Called")
        aztec.visualEditor.disableCrashLogging()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(
            "AztecEditorActivity",
            "onConfigurationChanged: New configuration received: $newConfig"
        )
        mHideActionBarOnSoftKeyboardUp =
            newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    !resources.getBoolean(R.bool.is_large_tablet_landscape)
        if (mHideActionBarOnSoftKeyboardUp) {
            Log.d(
                "AztecEditorActivity",
                "onConfigurationChanged: Hiding action bar due to keyboard"
            )
            hideActionBarIfNeeded()
        } else {
            Log.d("AztecEditorActivity", "onConfigurationChanged: Showing action bar")
            showActionBarIfNeeded()
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.d("AztecEditorActivity", "onRestoreInstanceState: Called")
        aztec.initSourceEditorHistory()
        if (savedInstanceState.getBoolean("isMediaUploadDialogVisible")) {
            Log.d(
                "AztecEditorActivity",
                "onRestoreInstanceState: Media upload dialog was visible, showing again"
            )
            showMediaUploadDialog()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("AztecEditorActivity", "onSaveInstanceState: Called")
        if (mediaUploadDialog?.isShowing == true) {
            outState.putBoolean("isMediaUploadDialogVisible", true)
            Log.d("AztecEditorActivity", "onSaveInstanceState: Saving media upload dialog state")
        }
    }

    private fun setupThemeAndToolbar() {
        Log.d("AztecEditorActivity", "setupThemeAndToolbar: Setting theme and toolbar")
        val themeParam = editorConfig?.theme ?: AztecEditorTheme.SYSTEM
        when (themeParam) {
            AztecEditorTheme.DARK -> {
                setTheme(R.style.EditorDarkTheme)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Log.d("AztecEditorActivity", "setupThemeAndToolbar: Dark theme applied")
            }

            AztecEditorTheme.LIGHT -> {
                setTheme(R.style.EditorLightTheme)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Log.d("AztecEditorActivity", "setupThemeAndToolbar: Light theme applied")
            }

            else -> {
                setTheme(R.style.EditorDayNightTheme)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                Log.d("AztecEditorActivity", "setupThemeAndToolbar: System default theme applied")
            }
        }
    }

    private fun setupBackPressHandler() {
        Log.d("AztecEditorActivity", "setupBackPressHandler: Setting up back press handler")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("AztecEditorActivity", "Back pressed: Handling back press")
                mIsKeyboardOpen = false
                showActionBarIfNeeded()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun setupEditorConfiguration() {
        Log.d("AztecEditorActivity", "setupEditorConfiguration: Configuring editor settings")
        // Setup hiding the action bar when the soft keyboard is displayed for narrow viewports
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            !resources.getBoolean(R.bool.is_large_tablet_landscape)
        ) {
            mHideActionBarOnSoftKeyboardUp = true
            Log.d(
                "AztecEditorActivity",
                "setupEditorConfiguration: Action bar will hide on soft keyboard up"
            )
        }
    }

    @SuppressLint("UseKtx")
    private fun setupAztecEditor(savedInstanceState: Bundle?) {
        Log.d("AztecEditorActivity", "setupAztecEditor: Initializing Aztec editor")
        val isDarkMode = isDarkMode()
        val appBarColor = if (isDarkMode) Color.BLACK else Color.WHITE
        val appBarTextColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val visualEditor = findViewById<ZetaAztecText>(R.id.aztec)
        val aztecToolbar = findViewById<AztecToolbar>(R.id.formatting_toolbar)
        val topToolbar = findViewById<Toolbar>(R.id.top_toolbar)

        val toolbarOptions = availableToolbarOptions()

        topToolbar.setBackgroundColor(appBarColor)
        topToolbar.setTitleTextColor(appBarTextColor)
        topToolbar.setSubtitleTextColor(appBarTextColor)

        window.setBackgroundDrawable(ColorDrawable(appBarColor))
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDarkMode //

        setSupportActionBar(topToolbar)
        Log.d("AztecEditorActivity", "setupAztecEditor: Top toolbar configured")

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(true)
            setHomeButtonEnabled(true)
            title = editorConfig?.title ?: ""
        }

        setTitle(editorConfig?.title ?: "")

        visualEditor.maxLength = editorConfig?.characterLimit?.toInt()
        visualEditor.enableSamsungPredictiveBehaviorOverride()
        visualEditor.setBackgroundColor(appBarColor)
        visualEditor.setTextAppearance(android.R.style.TextAppearance)
        visualEditor.hint =
            editorConfig?.placeholder ?: getString(R.string.edit_hint)

        val toolbarActions = availableToolbarOptions().mapNotNull { toAztecOption(it) }.toSet()
        aztecToolbar.setBackgroundColor(appBarColor)
        aztecToolbar.setToolbarItems(
            ToolbarItems.BasicLayout(
                *toolbarActions.toTypedArray()
            )
        )
        Log.d("AztecEditorActivity", "setupAztecEditor: Toolbar actions set: $toolbarActions")

        val headers = editorConfig?.authHeaders ?: emptyMap()
        aztec = Aztec.with(visualEditor, aztecToolbar, this)
            .setImageGetter(ZGlideImageLoader(this, headers))
            .setVideoThumbnailGetter(GlideVideoThumbnailLoader(this, headers))
            .setOnImeBackListener(this)
            .setOnTouchListener(this)
            .setHistoryListener(this)
            .setOnImageTappedListener(this)
            .setOnVideoTappedListener(this)
            .setOnAudioTappedListener(this)
            .setOnVideoInfoRequestedListener(this)
            .addOnMediaDeletedListener(this)
            .addPlugin(WordPressCommentsPlugin(visualEditor))
            .addPlugin(CaptionShortcodePlugin(visualEditor))
            .addPlugin(VideoShortcodePlugin())
            .addPlugin(AudioShortcodePlugin())
            .addPlugin(CssUnderlinePlugin())
            .addPlugin(HiddenGutenbergPlugin(visualEditor))
        Log.d("AztecEditorActivity", "setupAztecEditor: Aztec instance created")

        if (toolbarOptions.contains(AztecToolbarOption.VIDEO)) {
            aztec.addPlugin(MediaToolbarImageButton(aztecToolbar).apply {
                setMediaToolbarButtonClickListener(object :
                    IMediaToolbarButton.IMediaToolbarClickListener {
                    override fun onClick(view: View) {
                        Log.d("AztecEditorActivity", "MediaToolbarImageButton clicked for VIDEO")
                        mediaMenu = PopupMenu(this@AztecEditorActivity, view).apply {
                            setOnMenuItemClickListener(this@AztecEditorActivity)
                            inflate(R.menu.aztec_menu_image)
                            show()
                        }
                        if (view is ToggleButton) view.isChecked = false
                    }
                })
            })
        }

        if (toolbarOptions.contains(AztecToolbarOption.IMAGE)) {
            aztec.addPlugin(MediaToolbarVideoButton(aztecToolbar).apply {
                setMediaToolbarButtonClickListener(object :
                    IMediaToolbarButton.IMediaToolbarClickListener {
                    override fun onClick(view: View) {
                        Log.d("AztecEditorActivity", "MediaToolbarVideoButton clicked for IMAGE")
                        mediaMenu = PopupMenu(this@AztecEditorActivity, view).apply {
                            setOnMenuItemClickListener(this@AztecEditorActivity)
                            inflate(R.menu.aztec_menu_video)
                            show()
                        }
                        if (view is ToggleButton) view.isChecked = false
                    }
                })
            })
        }

        aztec.visualEditor.enableCrashLogging(object :
            AztecExceptionHandler.ExceptionHandlerHelper {
            override fun shouldLog(ex: Throwable): Boolean {
                Log.d("AztecEditorActivity", "Exception captured in crash logging: ${ex.message}")
                return true
            }
        })

        aztec.visualEditor.setCalypsoMode(false)
        aztec.sourceEditor?.setCalypsoMode(false)

        try {
            val initialHtml = intent.getStringExtra("initialHtml") ?: ""
            Log.d("AztecEditorActivity", "setupAztecEditor: Loading initial HTML")
            aztec.visualEditor.fromHtml(initialHtml)
        } catch (e: Exception) {
            Log.e("AztecEditorActivity", "setupAztecEditor: Error loading initial HTML", e)
        }

        visualEditor.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    // No debug needed here.
                }

                override fun afterTextChanged(s: Editable?) {
                    // No debug needed here.
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    Log.d(
                        "AztecEditorActivity",
                        "Text changed in editor. Scheduling debounce update."
                    )
                    // Cancel any previously scheduled update
                    debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
                    // Create a new Runnable to send the update after the debounce delay
                    debounceRunnable = Runnable {
                        val htmlContent = correctVideoTags(aztec.visualEditor.toHtml())
                        Log.d("AztecEditorActivity", "Debounce complete. HTML content updated.")
                        runOnUiThread {
                            AztecFlutterContainer.flutterApi?.onAztecHtmlChanged(htmlContent) {
                                Log.d("AztecEditorActivity", "HTML > flutter : $htmlContent")
                            }
                        }
                    }
                    // Post the runnable with the debounce delay
                    debounceHandler.postDelayed(debounceRunnable!!, DEBOUNCE_DELAY)
                }
            }
        )

        if (savedInstanceState == null) {
            Log.d("AztecEditorActivity", "setupAztecEditor: Initializing source editor history")
            aztec.initSourceEditorHistory()
        }
    }

    private fun setupAztecToolbar(toolbar: AztecToolbar) {
        Log.d("AztecEditorActivity", "setupAztecToolbar: Configuring toolbar")
        val availableToolbarOptions = availableToolbarOptions()
        val toolbarActions = availableToolbarOptions.mapNotNull { toAztecOption(it) }.toSet()

        val stateList = AppCompatResources.getColorStateList(
            this@AztecEditorActivity,
            R.color.toolbar_button_tint_selector
        )

        // Set the color state list for each toolbar button and hide/show them based on the options
        toolbarActions.forEach { action ->
            toolbar.findViewById<View>(action.buttonId)?.let {
                it.backgroundTintList = stateList
                Log.d(
                    "AztecEditorActivity",
                    "setupAztecToolbar: Set tint for buttonId ${action.buttonId}"
                )
            }
        }

        toolbar.findViewById<View>(org.wordpress.aztec.R.id.format_bar_button_media_expanded)?.let {
            it.backgroundTintList = stateList
        }

        // Set the color state list for the vertical divider and hide/show it based on the options
        toolbar.findViewById<View>(org.wordpress.aztec.R.id.format_bar_vertical_divider)?.let {
            if (
                availableToolbarOptions.contains(AztecToolbarOption.IMAGE)
                || availableToolbarOptions.contains(AztecToolbarOption.VIDEO)
            ) {
                it.setBackgroundColor(
                    ContextCompat.getColor(
                        this,
                        R.color.aztec_toolbar_border
                    )
                )
                it.visibility = View.VISIBLE
                Log.d("AztecEditorActivity", "setupAztecToolbar: Divider set visible")
            } else {
                it.visibility = View.GONE
                Log.d("AztecEditorActivity", "setupAztecToolbar: Divider hidden")
            }
        }
    }

    private fun availableToolbarOptions(): List<AztecToolbarOption> {
        return editorConfig?.toolbarOptions ?: AztecToolbarOption.entries.toList()
    }

    private fun setupInvalidateOptionsHandler() {
        Log.d(
            "AztecEditorActivity",
            "setupInvalidateOptionsHandler: Initializing handler for options menu invalidation"
        )
        invalidateOptionsHandler = Handler(Looper.getMainLooper())
        invalidateOptionsRunnable = Runnable {
            Log.d("AztecEditorActivity", "invalidateOptionsRunnable: Invalidating options menu")
            invalidateOptionsMenu()
        }
    }

    private fun toAztecOption(option: AztecToolbarOption): ToolbarAction? {
        if (AztecToolbarOption.BOLD == option) return ToolbarAction.BOLD
        if (AztecToolbarOption.ITALIC == option) return ToolbarAction.ITALIC
        if (AztecToolbarOption.UNDERLINE == option) return ToolbarAction.UNDERLINE
        if (AztecToolbarOption.STRIKETHROUGH == option) return ToolbarAction.STRIKETHROUGH
        if (AztecToolbarOption.HEADING == option) return ToolbarAction.HEADING
        if (AztecToolbarOption.UNORDERED_LIST == option) return ToolbarAction.UNORDERED_LIST
        if (AztecToolbarOption.ORDERED_LIST == option) return ToolbarAction.ORDERED_LIST
        if (AztecToolbarOption.QUOTE == option) return ToolbarAction.QUOTE
        if (AztecToolbarOption.LINK == option) return ToolbarAction.LINK
        if (AztecToolbarOption.CODE == option) return ToolbarAction.CODE
        if (AztecToolbarOption.HORIZONTAL_RULE == option) return ToolbarAction.HORIZONTAL_RULE
        if (AztecToolbarOption.IMAGE == option || AztecToolbarOption.VIDEO == option) return ToolbarAction.ADD_MEDIA_COLLAPSE
        return null
    }
    // endregion

    // region Media Handling
    private fun handleCameraPhotoResult() {
        Log.d("AztecEditorActivity", "handleCameraPhotoResult: Started")
        // For camera photo, mediaPath is already set to the external file path.
        val sourceFile = File(mediaPath)
        if (sourceFile.exists()) {
            Log.d("AztecEditorActivity", "handleCameraPhotoResult: Source file exists: $mediaPath")
            // Create a new file name for internal storage.
            val newFileName = "IMG_${System.currentTimeMillis()}.jpg"
            val destinationFile = File(filesDir, newFileName)
            try {
                sourceFile.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(
                    "AztecEditorActivity",
                    "handleCameraPhotoResult: Image copied to internal storage: ${destinationFile.absolutePath}"
                )
                mediaPath = destinationFile.absolutePath
                insertImageAndInitiateUpload(destinationFile.absolutePath)
            } catch (e: Exception) {
                Log.e(
                    "AztecEditorActivity",
                    "handleCameraPhotoResult: Failed to copy image to internal storage",
                    e
                )
            }
        } else {
            Log.d(
                "AztecEditorActivity",
                "handleCameraPhotoResult: Source file does not exist: $mediaPath"
            )
        }
    }

    private fun handleGalleryPhotoResult(data: Intent?) {
        Log.d("AztecEditorActivity", "handleGalleryPhotoResult: Started")
        data?.data?.let { uri ->
            val newFileName = "IMG_${System.currentTimeMillis()}.jpg"
            val internalPath = copyFileToInternalStorage(uri, newFileName)
            if (internalPath != null) {
                Log.d(
                    "AztecEditorActivity",
                    "handleGalleryPhotoResult: Gallery photo copied to internal storage: $internalPath"
                )
                mediaPath = internalPath
                insertImageAndInitiateUpload(internalPath)
            } else {
                Log.e(
                    "AztecEditorActivity",
                    "handleGalleryPhotoResult: Failed to copy gallery photo to internal storage"
                )
                ToastUtils.showToast(this, "Failed to copy image to internal storage!")
            }
        }
    }

    private fun handleVideoResult(data: Intent?) {
        Log.d("AztecEditorActivity", "handleVideoResult: Started")
        data?.data?.let { uri ->
            val newFileName = "VID_${System.currentTimeMillis()}.mp4"
            val internalPath = copyFileToInternalStorage(uri, newFileName)
            if (internalPath != null) {
                Log.d(
                    "AztecEditorActivity",
                    "handleVideoResult: Video copied to internal storage: $internalPath"
                )
                mediaPath = internalPath
                insertVideoAndInitiateUpload(internalPath)
            } else {
                Log.e(
                    "AztecEditorActivity",
                    "handleVideoResult: Failed to copy video to internal storage"
                )
                ToastUtils.showToast(this, "Failed to copy video to internal storage!")
            }
        }
    }

    private fun insertImageAndInitiateUpload(mediaPath: String) {
        Log.d("AztecEditorActivity", "insertImageAndSimulateUpload: Started for image: $mediaPath")
        val thumbnail = ZThumbnailUtils.getImageThumbnail(
            mediaPath,
            maxWidth = aztec.visualEditor.maxWidth
        )
        Log.d(
            "AztecEditorActivity",
            "insertImageAndSimulateUpload: Thumbnail generated for image: $mediaPath"
        )

        val (id, attrs) = generateAttributesForMedia(mediaPath, isVideo = false)
        aztec.visualEditor.insertImage(thumbnail?.toDrawable(resources), attrs)
        Log.d(
            "AztecEditorActivity",
            "insertImageAndSimulateUpload: Image inserted into editor with attributes: $attrs"
        )
        initiateMediaUpload(id, attrs)  // simulates upload progress and updates overlays
        aztec.toolbar.toggleMediaToolbar()
        Log.d("AztecEditorActivity", "insertImageAndSimulateUpload: Media toolbar toggled")
    }

    private fun insertVideoAndInitiateUpload(mediaPath: String) {
        Log.d("AztecEditorActivity", "insertVideoAndSimulateUpload: Started for video: $mediaPath")
        val thumbnail = ZThumbnailUtils.getVideoThumbnail(mediaPath)
        val (id, attrs) = generateAttributesForMedia(mediaPath, isVideo = true)
        aztec.visualEditor.insertVideo(thumbnail?.toDrawable(resources), attrs)
        Log.d(
            "AztecEditorActivity",
            "insertVideoAndSimulateUpload: Video inserted into editor with attributes: $attrs"
        )
        initiateMediaUpload(id, attrs)
        aztec.toolbar.toggleMediaToolbar()
        Log.d("AztecEditorActivity", "insertVideoAndSimulateUpload: Media toolbar toggled")
    }

    private fun generateAttributesForMedia(
        mediaPath: String,
        isVideo: Boolean
    ): Pair<String, AztecAttributes> {
        val id = Random().nextInt(Int.MAX_VALUE).toString()
        val attrs = AztecAttributes().apply {
            setValue("src", mediaPath) // Temporary source value – replace with URL after upload.
            setValue("id", id)
            setValue("width", "100%")
            setValue("uploading", "true")
            if (isVideo) setValue("video", "true")
        }
        Log.d(
            "AztecEditorActivity",
            "generateAttributesForMedia: Generated attributes for media: $mediaPath, isVideo: $isVideo"
        )
        return Pair(id, attrs)
    }

    private fun initiateMediaUpload(id: String, attrs: AztecAttributes) {
        Log.d(
            "AztecEditorActivity",
            "simulateMediaUpload: Started for media id: $id, mediaPath: $mediaPath"
        )

        val predicate = object : AztecText.AttributePredicate {
            override fun matches(attrs: Attributes): Boolean = attrs.getValue("id") == id
        }

        // Set initial overlay and progress drawable.
        aztec.visualEditor.setOverlay(predicate, 0, 0x80000000.toInt().toDrawable(), Gravity.FILL)
        aztec.visualEditor.updateElementAttributes(predicate, attrs)

        val progressDrawable =
            AppCompatResources.getDrawable(this, android.R.drawable.progress_horizontal)
        progressDrawable?.setBounds(0, 0, 0, 4)
        aztec.visualEditor.setOverlay(
            predicate,
            1,
            progressDrawable,
            Gravity.FILL_HORIZONTAL or Gravity.TOP
        )
        aztec.visualEditor.updateElementAttributes(predicate, attrs)

        runOnUiThread {
            if (isFinishing) {
                Log.d(
                    "AztecEditorActivity",
                    "simulateMediaUpload: Activity is finishing, skipping showing progress bar"
                )
            } else {
                Log.d("AztecEditorActivity", "simulateMediaUpload: Showing media progress bar")
                showMediaProgressBar()
            }
        }

        if (AztecFlutterContainer.flutterApi == null) {
            Log.d("AztecEditorActivity", "simulateMediaUpload: flutterApi is null!")
        }

        AztecFlutterContainer.flutterApi?.onAztecFileSelected(mediaPath) { result ->
            Log.d(
                "AztecEditorActivity",
                "simulateMediaUpload: Flutter API callback invoked with result: $result"
            )

            runOnUiThread {
                if (mediaProgressDialog?.isShowing == true) {
                    Log.d(
                        "AztecEditorActivity",
                        "simulateMediaUpload: Dismissing media progress dialog"
                    )
                    mediaProgressDialog?.dismiss()
                } else {
                    Log.d(
                        "AztecEditorActivity",
                        "simulateMediaUpload: Media progress dialog already dismissed"
                    )
                }
            }

            if (result.isSuccess) {
                val returnedPath = result.getOrNull()
                Log.d(
                    "AztecEditorActivity",
                    "simulateMediaUpload: Upload success, returned path: $returnedPath"
                )
                // Validate the returned URL.
                if (!returnedPath.isNullOrEmpty() && Patterns.WEB_URL.matcher(returnedPath)
                        .matches()
                ) {
                    attrs.removeAttribute(attrs.getIndex("uploading"))
                    aztec.visualEditor.clearOverlays(predicate)
                    val videoIndex = attrs.getIndex("video")
                    if (videoIndex != -1) {
                        attrs.removeAttribute(videoIndex)
                        val playDrawable = AppCompatResources.getDrawable(
                            this@AztecEditorActivity,
                            android.R.drawable.ic_media_play
                        )
                        aztec.visualEditor.setOverlay(predicate, 0, playDrawable, Gravity.CENTER)
                    }
                    val index = attrs.getIndex("src")
                    if (index != -1) attrs.removeAttribute(index)
                    attrs.setValue("src", returnedPath)
                    Log.d(
                        "AztecEditorActivity",
                        "simulateMediaUpload: Updated attributes after upload: $attrs"
                    )
                    aztec.visualEditor.updateElementAttributes(predicate, attrs)
                } else {
                    Log.d(
                        "AztecEditorActivity",
                        "simulateMediaUpload: Upload returned an invalid or empty URL; removing media"
                    )
                    handleUploadFailure(predicate, returnedPath.orEmpty())
                }
            } else {
                Log.d(
                    "AztecEditorActivity",
                    "simulateMediaUpload: Upload failed. Error: ${result.exceptionOrNull()}"
                )
                handleUploadFailure(predicate, result.exceptionOrNull()?.toString().orEmpty())
            }

            aztec.visualEditor.refreshText()
            aztec.visualEditor.refreshDrawableState()
            Log.d(
                "AztecEditorActivity",
                "simulateMediaUpload: Finished processing Flutter API callback for media id: $id"
            )
        } ?: run {
            Log.d(
                "AztecEditorActivity",
                "simulateMediaUpload: flutterApi returned null; cancelling upload"
            )
            runOnUiThread {
                if (mediaProgressDialog?.isShowing == true) {
                    Log.d(
                        "AztecEditorActivity",
                        "simulateMediaUpload: Dismissing media progress dialog"
                    )
                    mediaProgressDialog?.dismiss()
                }
            }
            aztec.visualEditor.clearOverlays(predicate)
            aztec.visualEditor.removeMedia(predicate)
            aztec.visualEditor.refreshText()
            aztec.visualEditor.refreshDrawableState()
        }

        aztec.visualEditor.refreshText()
        Log.d("AztecEditorActivity", "simulateMediaUpload: Completed for media id: $id")
    }

    /**
     * Handles upload failures by showing an alert dialog with an error message and removing the media.
     *
     * @param predicate The predicate to identify the media element.
     * @param message The error message to display. If null or empty, a generic message is used.
     */
    private fun handleUploadFailure(predicate: AztecText.AttributePredicate, message: String?) {
        val errorMessage =
            if (message.isNullOrEmpty()) "Upload failed. Please try again." else message
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Upload Failed")
                .setMessage(errorMessage)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
        aztec.visualEditor.clearOverlays(predicate)
        aztec.visualEditor.removeMedia(predicate)
    }

    private fun showMediaProgressBar() {
        Log.d("AztecEditorActivity", "showMediaProgressBar: Attempting to show media progress bar")
        if (isFinishing) {
            Log.d(
                "AztecEditorActivity",
                "showMediaProgressBar: Activity is finishing; skipping dialog display"
            )
            return
        }
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(R.layout.aztec_progress_dialog)
            .create()
            .also { dialog ->
                if (!isFinishing) {
                    dialog.show()
                    dialog.window?.setGravity(Gravity.CENTER)
                    mediaProgressDialog = dialog
                    Log.d("AztecEditorActivity", "showMediaProgressBar: Dialog shown successfully")
                } else {
                    Log.d(
                        "AztecEditorActivity",
                        "showMediaProgressBar: Activity is finishing after dialog creation; not showing dialog"
                    )
                }
            }
    }
    // endregion

    // region Permission Handling
    private fun onCameraPhotoMediaOptionSelected() {
        Log.d("AztecEditorActivity", "onCameraPhotoMediaOptionSelected: Called")
        if (PermissionUtils.checkAndRequestCameraAndStoragePermissions(
                this, MEDIA_CAMERA_PHOTO_PERMISSION_REQUEST_CODE
            )
        ) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaFile = "IMG_" + System.currentTimeMillis()
                mediaPath = File.createTempFile(
                    mediaFile, ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ).absolutePath
                Log.d(
                    "AztecEditorActivity",
                    "onCameraPhotoMediaOptionSelected: Temp file created: $mediaPath"
                )
            } else {
                mediaFile = "IMG_" + System.currentTimeMillis() + ".jpg"
                mediaPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        .toString() + File.separator + "Camera" + File.separator + mediaFile
                Log.d(
                    "AztecEditorActivity",
                    "onCameraPhotoMediaOptionSelected: File path set for legacy devices: $mediaPath"
                )
            }
            intent.putExtra(
                MediaStore.EXTRA_OUTPUT,
                FileProvider.getUriForFile(this, "$packageName.provider", File(mediaPath))
            )
            if (intent.resolveActivity(packageManager) != null) {
                Log.d(
                    "AztecEditorActivity",
                    "onCameraPhotoMediaOptionSelected: Launching camera intent"
                )
                imageCaptureLauncher.launch(intent)
            }
        }
    }

    private fun onCameraVideoMediaOptionSelected() {
        Log.d("AztecEditorActivity", "onCameraVideoMediaOptionSelected: Called")
        if (PermissionUtils.checkAndRequestCameraAndStoragePermissions(
                this, MEDIA_CAMERA_PHOTO_PERMISSION_REQUEST_CODE
            )
        ) {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            if (intent.resolveActivity(packageManager) != null) {
                Log.d(
                    "AztecEditorActivity",
                    "onCameraVideoMediaOptionSelected: Launching video capture intent"
                )
                videoOptionLauncher.launch(intent)
            }
        }
    }

    private fun onPhotosMediaOptionSelected() {
        Log.d("AztecEditorActivity", "onPhotosMediaOptionSelected: Called")
        if (PermissionUtils.checkAndRequestStoragePermission(
                this, MEDIA_PHOTOS_PERMISSION_REQUEST_CODE
            )
        ) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            try {
                Log.d("AztecEditorActivity", "onPhotosMediaOptionSelected: Launching photo chooser")
                imageSelectLauncher.launch(intent)
            } catch (exception: ActivityNotFoundException) {
                AppLog.e(AppLog.T.EDITOR, exception.message)
                ToastUtils.showToast(
                    this,
                    getString(org.wordpress.aztec.R.string.error_chooser_photo),
                    ToastUtils.Duration.LONG
                )
            }
        }
    }

    private fun onVideosMediaOptionSelected() {
        Log.d("AztecEditorActivity", "onVideosMediaOptionSelected: Called")
        if (PermissionUtils.checkAndRequestStoragePermission(
                this, MEDIA_PHOTOS_PERMISSION_REQUEST_CODE
            )
        ) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }
            try {
                Log.d("AztecEditorActivity", "onVideosMediaOptionSelected: Launching video chooser")
                videoOptionLauncher.launch(intent)
            } catch (exception: ActivityNotFoundException) {
                AppLog.e(AppLog.T.EDITOR, exception.message)
                ToastUtils.showToast(
                    this,
                    getString(org.wordpress.aztec.R.string.error_chooser_video),
                    ToastUtils.Duration.LONG
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        Log.d(
            "AztecEditorActivity",
            "onRequestPermissionsResult: Called with requestCode $requestCode"
        )
        when (requestCode) {
            MEDIA_CAMERA_PHOTO_PERMISSION_REQUEST_CODE, MEDIA_CAMERA_VIDEO_PERMISSION_REQUEST_CODE -> {
                val isPermissionDenied = permissions.indices.any { i ->
                    (permissions[i] == Manifest.permission.CAMERA ||
                            permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                            (grantResults[i] == PackageManager.PERMISSION_DENIED)
                }
                if (isPermissionDenied) {
                    Log.d(
                        "AztecEditorActivity",
                        "onRequestPermissionsResult: Camera permissions denied"
                    )
                    ToastUtils.showToast(
                        this,
                        getString(R.string.permission_required_media_camera)
                    )
                } else {
                    when (requestCode) {
                        MEDIA_CAMERA_PHOTO_PERMISSION_REQUEST_CODE -> {
                            Log.d(
                                "AztecEditorActivity",
                                "onRequestPermissionsResult: Permissions granted, launching camera photo option"
                            )
                            onCameraPhotoMediaOptionSelected()
                        }

                        MEDIA_CAMERA_VIDEO_PERMISSION_REQUEST_CODE -> {
                            Log.d(
                                "AztecEditorActivity",
                                "onRequestPermissionsResult: Permissions granted, launching camera video option"
                            )
                            onCameraVideoMediaOptionSelected()
                        }
                    }
                }
            }

            MEDIA_PHOTOS_PERMISSION_REQUEST_CODE, MEDIA_VIDEOS_PERMISSION_REQUEST_CODE -> {
                val isPermissionDenied = permissions.indices.any { i ->
                    permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE &&
                            (grantResults[i] == PackageManager.PERMISSION_DENIED)
                }
                when (requestCode) {
                    MEDIA_PHOTOS_PERMISSION_REQUEST_CODE -> {
                        if (isPermissionDenied) {
                            Log.d(
                                "AztecEditorActivity",
                                "onRequestPermissionsResult: Photos permission denied"
                            )
                            ToastUtils.showToast(
                                this,
                                getString(R.string.permission_required_media_photos)
                            )
                        } else {
                            Log.d(
                                "AztecEditorActivity",
                                "onRequestPermissionsResult: Photos permission granted, launching photos option"
                            )
                            onPhotosMediaOptionSelected()
                        }
                    }

                    MEDIA_VIDEOS_PERMISSION_REQUEST_CODE -> {
                        if (isPermissionDenied) {
                            Log.d(
                                "AztecEditorActivity",
                                "onRequestPermissionsResult: Videos permission denied"
                            )
                            ToastUtils.showToast(
                                this,
                                getString(R.string.permission_required_media_videos)
                            )
                        } else {
                            Log.d(
                                "AztecEditorActivity",
                                "onRequestPermissionsResult: Videos permission granted, launching videos option"
                            )
                            onVideosMediaOptionSelected()
                        }
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    // endregion

    // region Action Bar & UI Helpers
    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun isHardwareKeyboardPresent(): Boolean {
        return resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
    }

    private fun hideActionBarIfNeeded() {
        supportActionBar?.let { actionBar ->
            if (!isHardwareKeyboardPresent() && mHideActionBarOnSoftKeyboardUp && mIsKeyboardOpen && actionBar.isShowing) {
                Log.d("AztecEditorActivity", "hideActionBarIfNeeded: Hiding action bar")
                actionBar.hide()
            }
        }
    }

    private fun showActionBarIfNeeded() {
        supportActionBar?.let { actionBar ->
            if (!actionBar.isShowing) {
                Log.d("AztecEditorActivity", "showActionBarIfNeeded: Showing action bar")
                actionBar.show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            Log.d("AztecEditorActivity", "onTouch: ACTION_UP detected, setting keyboard open flag")
            mIsKeyboardOpen = true
            hideActionBarIfNeeded()
        }
        return false
    }

    override fun onImeBack() {
        Log.d("AztecEditorActivity", "onImeBack: IME back pressed")
        mIsKeyboardOpen = false
        showActionBarIfNeeded()
    }
    // endregion

    // region Options Menu & Toolbar Callbacks
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d("AztecEditorActivity", "onCreateOptionsMenu: Inflating options menu")
        menuInflater.inflate(R.menu.aztec_menu_main, menu)
        val menuIconColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        menu.forEach { it.icon?.setTint(menuIconColor) }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d("AztecEditorActivity", "onOptionsItemSelected: Item selected with id ${item.itemId}")
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("AztecEditorActivity", "onOptionsItemSelected: Home/up button pressed")
                finish()
            }

            R.id.undo -> {
                Log.d("AztecEditorActivity", "onOptionsItemSelected: Undo action selected")
                if (aztec.visualEditor.isVisible) {
                    aztec.visualEditor.undo()
                } else {
                    aztec.sourceEditor?.undo()
                }
            }

            R.id.redo -> {
                Log.d("AztecEditorActivity", "onOptionsItemSelected: Redo action selected")
                if (aztec.visualEditor.isVisible) {
                    aztec.visualEditor.redo()
                } else {
                    aztec.sourceEditor?.redo()
                }
            }

            R.id.done -> {
                Log.d("AztecEditorActivity", "onOptionsItemSelected: Done action selected")
                doneEditing()
            }
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        Log.d("AztecEditorActivity", "onPrepareOptionsMenu: Called")
        menu?.findItem(R.id.redo)?.isEnabled =
            aztec.visualEditor.history.redoValid()
        menu?.findItem(R.id.undo)?.isEnabled =
            aztec.visualEditor.history.undoValid()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onRedoEnabled() {
        Log.d("AztecEditorActivity", "onRedoEnabled: Called")
        invalidateOptionsHandler.removeCallbacks(invalidateOptionsRunnable)
        invalidateOptionsHandler.postDelayed(
            invalidateOptionsRunnable,
            resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        )
    }

    override fun onUndoEnabled() {
        Log.d("AztecEditorActivity", "onUndoEnabled: Called")
        invalidateOptionsHandler.removeCallbacks(invalidateOptionsRunnable)
        invalidateOptionsHandler.postDelayed(
            invalidateOptionsRunnable,
            resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        )
    }

    override fun onUndo() {
        Log.d("AztecEditorActivity", "onUndo: Called")
    }

    override fun onRedo() {
        Log.d("AztecEditorActivity", "onRedo: Called")
    }

    private fun doneEditing() {
        Log.d("AztecEditorActivity", "doneEditing: Called")
        val html = correctVideoTags(aztec.visualEditor.toHtml())
        setResult(RESULT_OK, Intent().apply { putExtra("html", html) })
        Log.d("AztecEditorActivity", "doneEditing: Finished editing with HTML: $html")
        finish()
    }
    // endregion

    // region Toolbar & Menu Item Callbacks
    override fun onToolbarCollapseButtonClicked() {
        Log.d("AztecEditorActivity", "onToolbarCollapseButtonClicked: Called")
    }

    override fun onToolbarExpandButtonClicked() {
        Log.d("AztecEditorActivity", "onToolbarExpandButtonClicked: Called")
    }

    override fun onToolbarFormatButtonClicked(format: ITextFormat, isKeyboardShortcut: Boolean) {
        Log.d(
            "AztecEditorActivity",
            "onToolbarFormatButtonClicked: Format clicked: $format, isKeyboardShortcut: $isKeyboardShortcut"
        )
    }

    override fun onToolbarHeadingButtonClicked() {
        Log.d("AztecEditorActivity", "onToolbarHeadingButtonClicked: Called")
    }

    override fun onToolbarHtmlButtonClicked() {
        Log.d("AztecEditorActivity", "onToolbarHtmlButtonClicked: Called")
        val uploadingPredicate = object : AztecText.AttributePredicate {
            override fun matches(attrs: Attributes): Boolean = attrs.getIndex("uploading") > -1
        }
        val mediaPending =
            aztec.visualEditor.getAllElementAttributes(uploadingPredicate).isNotEmpty()
        if (mediaPending) {
            ToastUtils.showToast(this, org.wordpress.aztec.R.string.media_upload_dialog_message)
        } else {
            aztec.toolbar.toggleEditorMode()
        }
    }

    override fun onToolbarListButtonClicked() {
        Log.d("AztecEditorActivity", "onToolbarListButtonClicked: Called")
    }

    override fun onToolbarMediaButtonClicked(): Boolean {
        Log.d("AztecEditorActivity", "onToolbarMediaButtonClicked: Called")
        return false
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        Log.d("AztecEditorActivity", "onMenuItemClick: Called for item id: ${item?.itemId}")
        item?.isChecked = !item.isChecked
        return when (item?.itemId) {
            R.id.take_photo -> {
                onCameraPhotoMediaOptionSelected()
                true
            }

            R.id.take_video -> {
                onCameraVideoMediaOptionSelected()
                true
            }

            R.id.gallery_photo -> {
                onPhotosMediaOptionSelected()
                true
            }

            R.id.gallery_video -> {
                onVideosMediaOptionSelected()
                true
            }

            else -> false
        }
    }
    // endregion

    // region Media Tap & Info Callbacks
    override fun onImageTapped(attrs: AztecAttributes, naturalWidth: Int, naturalHeight: Int) {
        Log.d("AztecEditorActivity", "onImageTapped: Called with attributes: $attrs")
        val url = attrs.getValue("src")
        url?.let { showMediaOptions(it) }
    }

    override fun onVideoTapped(attrs: AztecAttributes) {
        Log.d("AztecEditorActivity", "onVideoTapped: Called with attributes: $attrs")
        val url = if (attrs.hasAttribute(ATTRIBUTE_VIDEOPRESS_HIDDEN_SRC))
            attrs.getValue(ATTRIBUTE_VIDEOPRESS_HIDDEN_SRC)
        else
            attrs.getValue("src")

        url?.let { showMediaOptions(it) }
    }

    override fun onVideoInfoRequested(attrs: AztecAttributes) {
        Log.d("AztecEditorActivity", "onVideoInfoRequested: Called with attributes: $attrs")
    }

    override fun onAudioTapped(attrs: AztecAttributes) {
        Log.d("AztecEditorActivity", "onAudioTapped: Called with attributes: $attrs")
        val url = attrs.getValue("src")
        url?.let { showMediaOptions(it) }
    }

    override fun onMediaDeleted(attrs: AztecAttributes) {
        Log.d(
            "AztecEditorActivity",
            "onMediaDeleted: Called for media with src: ${attrs.getValue("src")}"
        )
        try {
            AztecFlutterContainer.flutterApi?.onAztecFileDeleted(attrs.getValue("src")) {}
        } catch (e: Exception) {
            AppLog.e(AppLog.T.EDITOR, e)
        }
    }

    private fun showMediaOptions(url: String) {
        Log.d("AztecEditorActivity", "showMediaOptions: Called for URL: $url")
        val options = arrayOf("Add new line above", "Add new line below", "Delete Media", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Media Options")
            .setItems(options) { dialog, which ->
                Log.d("AztecEditorActivity", "showMediaOptions: Option selected index $which")
                when (which) {
                    0 -> addNewLine(true, url)
                    1 -> addNewLine(false, url)
                    2 -> showDeleteConfirmation(url)
                    3 -> {
                        dialog.dismiss()
                        showKeyboard(aztec.visualEditor)
                    }
                }
            }
            .show()

        Handler(Looper.getMainLooper()).postDelayed({
            dismissKeyboard(aztec.visualEditor)
        }, 100)
    }

    private fun addNewLine(above: Boolean, url: String) {
        Log.d("AztecEditorActivity", "addNewLine: Called for URL: $url, above: $above")
        // Get the editor's editable text.
        val editableText = aztec.visualEditor.text
        // Find all AztecMediaSpan instances matching the given URL.
        val mediaSpans = editableText.getSpans(0, editableText.length, AztecMediaSpan::class.java)
            .filter { it.attributes.getValue("src") == url }
        Log.d(
            "AztecEditorActivity",
            "addNewLine: Found ${mediaSpans.size} media spans for URL: $url"
        )

        mediaSpans.forEach { mediaSpan ->
            val start = editableText.getSpanStart(mediaSpan)
            val end = editableText.getSpanEnd(mediaSpan)
            if (above) {
                // Insert newline before the span.
                editableText.insert(start, "\n")
                Log.d(
                    "AztecEditorActivity",
                    "addNewLine: Inserted newline above media span at position $start"
                )
                // Set the cursor to the beginning of the new line.
                aztec.visualEditor.setSelection(start)
            } else {
                // Insert newline after the span.
                editableText.insert(end, "\n")
                Log.d(
                    "AztecEditorActivity",
                    "addNewLine: Inserted newline below media span at position $end"
                )
                // For below, move the cursor to the beginning of the new line (after the inserted newline).
                aztec.visualEditor.setSelection(end + 1)
            }
        }

        showKeyboard(aztec.visualEditor)
    }

    private fun showDeleteConfirmation(url: String) {
        Log.d("AztecEditorActivity", "showDeleteConfirmation: Called for URL: $url")
        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete this media? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Log.d(
                    "AztecEditorActivity",
                    "showDeleteConfirmation: Delete confirmed for URL: $url"
                )
                deleteMedia(url)
                showKeyboard(aztec.visualEditor)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d(
                    "AztecEditorActivity",
                    "showDeleteConfirmation: Delete canceled for URL: $url"
                )
                showKeyboard(aztec.visualEditor)
            }
            .show()
    }

    private fun deleteMedia(url: String) {
        Log.d("AztecEditorActivity", "deleteMedia: Called for URL: $url")
        aztec.visualEditor.removeMedia { attrs ->
            attrs.getValue("src") == url
        }
    }

    private fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    // endregion

    // region Media Upload Dialog
    private fun showMediaUploadDialog() {
        Log.d("AztecEditorActivity", "showMediaUploadDialog: Showing media upload dialog")
        AlertDialog.Builder(this)
            .setMessage(getString(org.wordpress.aztec.R.string.media_upload_dialog_message))
            .setPositiveButton(
                getString(org.wordpress.aztec.R.string.media_upload_dialog_positive),
                null
            )
            .create()
            .also { mediaUploadDialog = it; it.show() }
    }

    private fun copyFileToInternalStorage(sourceUri: Uri, newFileName: String): String? {
        Log.d(
            "AztecEditorActivity",
            "copyFileToInternalStorage: Started for URI: $sourceUri, newFileName: $newFileName"
        )
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        return try {
            // Open the source input stream
            inputStream = contentResolver.openInputStream(sourceUri) ?: return null

            // Create a file in internal storage
            val outputFile = File(filesDir, newFileName)
            outputStream = FileOutputStream(outputFile)

            // Copy the contents from the source to the destination
            inputStream.copyTo(outputStream)
            Log.d(
                "AztecEditorActivity",
                "copyFileToInternalStorage: File copied to ${outputFile.absolutePath}"
            )

            // Return the absolute path of the copied file
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e("AztecEditorActivity", "copyFileToInternalStorage: Error copying file", e)
            null
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private fun correctVideoTags(html: String): String {
        Log.d("AztecEditorActivity", "correctVideoTags: Called")
        return html.replace(videoRegex, """<video src="$1"></video>""")
    }
    // endregion
}

fun Context.dismissKeyboard(view: View) {
    runOnUi {
        Log.d("AztecEditorActivity", "dismissKeyboard: Called")
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}