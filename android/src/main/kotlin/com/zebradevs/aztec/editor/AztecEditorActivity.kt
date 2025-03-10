package com.zebradevs.aztec.editor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
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
import androidx.core.view.forEach
import com.zebradevs.aztec.editor.EditorConfig
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
import org.wordpress.aztec.plugins.shortcodes.extensions.ATTRIBUTE_VIDEOPRESS_HIDDEN_ID
import org.wordpress.aztec.plugins.shortcodes.extensions.ATTRIBUTE_VIDEOPRESS_HIDDEN_SRC
import org.wordpress.aztec.plugins.shortcodes.extensions.updateVideoPressThumb
import org.wordpress.aztec.plugins.wpcomments.HiddenGutenbergPlugin
import org.wordpress.aztec.plugins.wpcomments.WordPressCommentsPlugin
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
    private val DEBOUNCE_DELAY: Long = 750
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var editorConfig: EditorConfig? = null
    // endregion

    // region Lifecycle & Setup
    override fun onCreate(savedInstanceState: Bundle?) {
        setupThemeAndToolbar()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aztec_editor)

        IntentCompat.getParcelableExtra(intent, "editorConfig", EditorConfig::class.java)?.let {
            editorConfig = it
        }

        setupBackPressHandler()
        setupEditorConfiguration()
        setupAztecEditor(savedInstanceState)
        setupInvalidateOptionsHandler()

        videoOptionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    handleVideoResult(result.data)
                }
            }

        imageSelectLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    handleGalleryPhotoResult(result.data)
                }
            }

        imageCaptureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    handleCameraPhotoResult()
                }
            }
    }

    override fun onStart() {
        super.onStart()
        findViewById<AztecToolbar>(R.id.formatting_toolbar)?.let { toolbar ->
            setupAztecToolbar(toolbar)
        }
    }

    override fun onPause() {
        super.onPause()
        mIsKeyboardOpen = false
    }

    override fun onResume() {
        super.onResume()
        showActionBarIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        aztec.visualEditor.disableCrashLogging()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mHideActionBarOnSoftKeyboardUp =
            newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    !resources.getBoolean(R.bool.is_large_tablet_landscape)
        if (mHideActionBarOnSoftKeyboardUp) hideActionBarIfNeeded() else showActionBarIfNeeded()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        aztec.initSourceEditorHistory()
        if (savedInstanceState.getBoolean("isMediaUploadDialogVisible")) {
            showMediaUploadDialog()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mediaUploadDialog?.isShowing == true) {
            outState.putBoolean("isMediaUploadDialogVisible", true)
        }
    }

    private fun setupThemeAndToolbar() {
        val themeParam = editorConfig?.theme ?: AztecEditorTheme.SYSTEM
        when (themeParam) {
            AztecEditorTheme.DARK -> {
                setTheme(R.style.EditorDarkTheme)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            AztecEditorTheme.LIGHT -> {
                setTheme(R.style.EditorLightTheme)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            else -> {
                setTheme(R.style.EditorDayNightTheme)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                mIsKeyboardOpen = false
                showActionBarIfNeeded()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun setupEditorConfiguration() {
        // Setup hiding the action bar when the soft keyboard is displayed for narrow viewports
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            !resources.getBoolean(R.bool.is_large_tablet_landscape)
        ) {
            mHideActionBarOnSoftKeyboardUp = true
        }
    }

    private fun setupAztecEditor(savedInstanceState: Bundle?) {
        val isDarkMode = isDarkMode()
        val appBarColor = if (isDarkMode) Color.BLACK else Color.WHITE
        val appBarTextColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val visualEditor = findViewById<AztecText>(R.id.aztec)
        val aztecToolbar =
            findViewById<AztecToolbar>(R.id.formatting_toolbar)
        val topToolbar = findViewById<Toolbar>(R.id.top_toolbar)

        val toolbarOptions = availableToolbarOptions()

        topToolbar.setBackgroundColor(appBarColor)
        topToolbar.setTitleTextColor(appBarTextColor)
        topToolbar.setSubtitleTextColor(appBarTextColor)
        setSupportActionBar(topToolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(true)
            setHomeButtonEnabled(true)
            title = editorConfig?.title ?: ""
        }

        setTitle(editorConfig?.title ?: "")

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

        if (toolbarOptions.contains(AztecToolbarOption.VIDEO)) {
            aztec.addPlugin(MediaToolbarImageButton(aztecToolbar).apply {
                setMediaToolbarButtonClickListener(object :
                    IMediaToolbarButton.IMediaToolbarClickListener {
                    override fun onClick(view: View) {
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
            override fun shouldLog(ex: Throwable): Boolean = true
        })

        aztec.visualEditor.setCalypsoMode(false)
        aztec.sourceEditor?.setCalypsoMode(false)
        aztec.visualEditor.fromHtml(intent.getStringExtra("initialHtml") ?: "")

        aztec.visualEditor.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Cancel any previously scheduled update
                    debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
                    // Create a new Runnable to send the update after the debounce delay
                    debounceRunnable = Runnable {
                        val htmlContent = correctVideoTags(aztec.visualEditor.toHtml())
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
            aztec.initSourceEditorHistory()
        }
    }

    private fun setupAztecToolbar(toolbar: AztecToolbar) {
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
            }
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
            } else {
                it.visibility = View.GONE
            }
        }
    }

    private fun availableToolbarOptions(): List<AztecToolbarOption> {
        return editorConfig?.toolbarOptions ?: AztecToolbarOption.entries.toList()
    }

    private fun setupInvalidateOptionsHandler() {
        invalidateOptionsHandler = Handler(Looper.getMainLooper())
        invalidateOptionsRunnable = Runnable { invalidateOptionsMenu() }
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
        // For camera photo, mediaPath is already set to the external file path.
        val sourceFile = File(mediaPath)
        if (sourceFile.exists()) {
            // Create a new file name for internal storage.
            val newFileName = "IMG_${System.currentTimeMillis()}.jpg"
            val destinationFile = File(filesDir, newFileName)
            try {
                sourceFile.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                mediaPath = destinationFile.absolutePath
                insertImageAndSimulateUpload(destinationFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showToast(this, "Failed to copy image to internal storage!")
            }
        }
    }

    private fun handleGalleryPhotoResult(data: Intent?) {
        data?.data?.let { uri ->
            val newFileName = "IMG_${System.currentTimeMillis()}.jpg"
            val internalPath = copyFileToInternalStorage(uri, newFileName)
            if (internalPath != null) {
                mediaPath = internalPath
                insertImageAndSimulateUpload(internalPath)
            } else {
                ToastUtils.showToast(this, "Failed to copy image to internal storage!")
            }
        }
    }

    private fun handleVideoResult(data: Intent?) {
        data?.data?.let { uri ->
            val newFileName = "VID_${System.currentTimeMillis()}.mp4"
            val internalPath = copyFileToInternalStorage(uri, newFileName)
            if (internalPath != null) {
                mediaPath = internalPath
                insertVideoAndSimulateUpload(internalPath)
            } else {
                ToastUtils.showToast(this, "Failed to copy video to internal storage!")
            }
        }
    }

    private fun insertImageAndSimulateUpload(mediaPath: String) {
        val thumbnail = ZThumbnailUtils.getImageThumbnail(
            mediaPath,
            maxWidth = aztec.visualEditor.maxWidth
        )

        val (id, attrs) = generateAttributesForMedia(mediaPath, isVideo = false)
        aztec.visualEditor.insertImage(BitmapDrawable(resources, thumbnail), attrs)
        simulateMediaUpload(id, attrs)  // simulates upload progress and updates overlays
        aztec.toolbar.toggleMediaToolbar()
    }

    private fun insertVideoAndSimulateUpload(mediaPath: String) {
        val thumbnail = ZThumbnailUtils.getVideoThumbnail(mediaPath)
        val (id, attrs) = generateAttributesForMedia(mediaPath, isVideo = true)
        aztec.visualEditor.insertVideo(BitmapDrawable(resources, thumbnail), attrs)
        simulateMediaUpload(id, attrs)
        aztec.toolbar.toggleMediaToolbar()
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
        return Pair(id, attrs)
    }

    private fun simulateMediaUpload(id: String, attrs: AztecAttributes) {
        val predicate = object : AztecText.AttributePredicate {
            override fun matches(attrs: Attributes): Boolean = attrs.getValue("id") == id
        }

        // Set initial overlay and progress drawable
        aztec.visualEditor.setOverlay(predicate, 0, ColorDrawable(0x80000000.toInt()), Gravity.FILL)
        aztec.visualEditor.updateElementAttributes(predicate, attrs)
        val progressDrawable = AppCompatResources.getDrawable(
            this, android.R.drawable.progress_horizontal
        )
        progressDrawable?.setBounds(0, 0, 0, 4)
        aztec.visualEditor.setOverlay(
            predicate,
            1,
            progressDrawable,
            Gravity.FILL_HORIZONTAL or Gravity.TOP
        )

        aztec.visualEditor.updateElementAttributes(predicate, attrs)

        runOnUiThread { showMediaProgressBar() }
        AztecFlutterContainer.flutterApi?.onAztecFileSelected(mediaPath) {
            runOnUiThread { mediaProgressDialog?.dismiss() }
            if (it.isSuccess && it.getOrNull()?.isNotEmpty() == true) {
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
                attrs.setValue("src", it.getOrNull() ?: "")
                aztec.visualEditor.updateElementAttributes(predicate, attrs)
            } else {
                runOnUiThread { ToastUtils.showToast(this, "Media upload failed!") }
                aztec.visualEditor.clearOverlays(predicate)
                aztec.visualEditor.removeMedia(predicate)
            }

            aztec.visualEditor.refreshText()
            aztec.visualEditor.refreshDrawableState()
        } ?: run {
            runOnUiThread { ToastUtils.showToast(this, "Media upload failed!") }
            aztec.visualEditor.clearOverlays(predicate)
            aztec.visualEditor.removeMedia(predicate)
            aztec.visualEditor.refreshText()
            aztec.visualEditor.refreshDrawableState()
        }

        aztec.visualEditor.refreshText()
    }
    // endregion

    // region Permission Handling
    private fun onCameraPhotoMediaOptionSelected() {
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
            } else {
                mediaFile = "IMG_" + System.currentTimeMillis() + ".jpg"
                mediaPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        .toString() + File.separator + "Camera" + File.separator + mediaFile
            }
            intent.putExtra(
                MediaStore.EXTRA_OUTPUT,
                FileProvider.getUriForFile(this, "$packageName.provider", File(mediaPath))
            )
            if (intent.resolveActivity(packageManager) != null) {
                imageCaptureLauncher.launch(intent)
            }
        }
    }

    private fun onCameraVideoMediaOptionSelected() {
        if (PermissionUtils.checkAndRequestCameraAndStoragePermissions(
                this, MEDIA_CAMERA_PHOTO_PERMISSION_REQUEST_CODE
            )
        ) {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            if (intent.resolveActivity(packageManager) != null) {
                videoOptionLauncher.launch(intent)
            }
        }
    }

    private fun onPhotosMediaOptionSelected() {
        if (PermissionUtils.checkAndRequestStoragePermission(
                this, MEDIA_PHOTOS_PERMISSION_REQUEST_CODE
            )
        ) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            try {
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
        if (PermissionUtils.checkAndRequestStoragePermission(
                this, MEDIA_PHOTOS_PERMISSION_REQUEST_CODE
            )
        ) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }
            try {
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
        when (requestCode) {
            MEDIA_CAMERA_PHOTO_PERMISSION_REQUEST_CODE, MEDIA_CAMERA_VIDEO_PERMISSION_REQUEST_CODE -> {
                val isPermissionDenied = permissions.indices.any { i ->
                    (permissions[i] == Manifest.permission.CAMERA ||
                            permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                            (grantResults[i] == PackageManager.PERMISSION_DENIED)
                }
                if (isPermissionDenied) {
                    ToastUtils.showToast(
                        this,
                        getString(R.string.permission_required_media_camera)
                    )
                } else {
                    when (requestCode) {
                        MEDIA_CAMERA_PHOTO_PERMISSION_REQUEST_CODE -> onCameraPhotoMediaOptionSelected()
                        MEDIA_CAMERA_VIDEO_PERMISSION_REQUEST_CODE -> onCameraVideoMediaOptionSelected()
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
                            ToastUtils.showToast(
                                this,
                                getString(R.string.permission_required_media_photos)
                            )
                        } else {
                            onPhotosMediaOptionSelected()
                        }
                    }

                    MEDIA_VIDEOS_PERMISSION_REQUEST_CODE -> {
                        if (isPermissionDenied) {
                            ToastUtils.showToast(
                                this,
                                getString(R.string.permission_required_media_videos)
                            )
                        } else {
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
                actionBar.hide()
            }
        }
    }

    private fun showActionBarIfNeeded() {
        supportActionBar?.let { actionBar ->
            if (!actionBar.isShowing) actionBar.show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            mIsKeyboardOpen = true
            hideActionBarIfNeeded()
        }
        return false
    }

    override fun onImeBack() {
        mIsKeyboardOpen = false
        showActionBarIfNeeded()
    }
    // endregion

    // region Options Menu & Toolbar Callbacks
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.aztec_menu_main, menu)
        val menuIconColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        menu.forEach { it.icon?.setTint(menuIconColor) }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.undo -> {
                if (aztec.visualEditor.visibility == View.VISIBLE) {
                    aztec.visualEditor.undo()
                } else {
                    aztec.sourceEditor?.undo()
                }
            }

            R.id.redo -> {
                if (aztec.visualEditor.visibility == View.VISIBLE) {
                    aztec.visualEditor.redo()
                } else {
                    aztec.sourceEditor?.redo()
                }
            }

            R.id.done -> doneEditing()
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.redo)?.isEnabled =
            aztec.visualEditor.history.redoValid()
        menu?.findItem(R.id.undo)?.isEnabled =
            aztec.visualEditor.history.undoValid()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onRedoEnabled() {
        invalidateOptionsHandler.removeCallbacks(invalidateOptionsRunnable)
        invalidateOptionsHandler.postDelayed(
            invalidateOptionsRunnable,
            resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        )
    }

    override fun onUndoEnabled() {
        invalidateOptionsHandler.removeCallbacks(invalidateOptionsRunnable)
        invalidateOptionsHandler.postDelayed(
            invalidateOptionsRunnable,
            resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        )
    }

    override fun onUndo() {}
    override fun onRedo() {}

    private fun doneEditing() {
        val html = correctVideoTags(aztec.visualEditor.toHtml())
        setResult(RESULT_OK, Intent().apply { putExtra("html", html) })
        finish()
    }
    // endregion

    // region Toolbar & Menu Item Callbacks
    override fun onToolbarCollapseButtonClicked() {}
    override fun onToolbarExpandButtonClicked() {}
    override fun onToolbarFormatButtonClicked(format: ITextFormat, isKeyboardShortcut: Boolean) {}

    override fun onToolbarHeadingButtonClicked() {}
    override fun onToolbarHtmlButtonClicked() {
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

    override fun onToolbarListButtonClicked() {}
    override fun onToolbarMediaButtonClicked(): Boolean = false

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        item?.isChecked = !(item?.isChecked ?: false)
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
        ToastUtils.showToast(this, "Image tapped!")
    }

    override fun onVideoTapped(attrs: AztecAttributes) {
        val url = if (attrs.hasAttribute(ATTRIBUTE_VIDEOPRESS_HIDDEN_SRC))
            attrs.getValue(ATTRIBUTE_VIDEOPRESS_HIDDEN_SRC)
        else
            attrs.getValue("src")

        url?.let {
            try {
                Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply {
                    setDataAndType(Uri.parse(it), "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.also { intent -> startActivity(intent) }
            } catch (e: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                } catch (e: ActivityNotFoundException) {
                    ToastUtils.showToast(this, "Video tapped!")
                }
            }
        }
    }

    override fun onVideoInfoRequested(attrs: AztecAttributes) {
        if (attrs.hasAttribute(ATTRIBUTE_VIDEOPRESS_HIDDEN_ID)) {
            AppLog.d(
                AppLog.T.EDITOR, "Video Info Requested for shortcode " +
                        attrs.getValue(ATTRIBUTE_VIDEOPRESS_HIDDEN_ID)
            )
            aztec.visualEditor.postDelayed({
                aztec.visualEditor.updateVideoPressThumb(
                    "https://videos.files.wordpress.com/OcobLTqC/img_5786_hd.original.jpg",
                    "https://videos.files.wordpress.com/OcobLTqC/img_5786.m4v",
                    attrs.getValue(ATTRIBUTE_VIDEOPRESS_HIDDEN_ID)
                )
            }, 500)
        }
    }

    override fun onAudioTapped(attrs: AztecAttributes) {
        val url = attrs.getValue("src")
        url?.let {
            try {
                Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply {
                    setDataAndType(Uri.parse(it), "audio/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.also { intent -> startActivity(intent) }
            } catch (e: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                } catch (e: ActivityNotFoundException) {
                    ToastUtils.showToast(this, "Audio tapped!")
                }
            }
        }
    }

    override fun onMediaDeleted(attrs: AztecAttributes) {
        try {
            AztecFlutterContainer.flutterApi?.onAztecFileDeleted(attrs.getValue("src")) {}
        } catch (e: Exception) {
            AppLog.e(AppLog.T.EDITOR, e)
        }
    }
    // endregion

    // region Media Upload Dialog
    private fun showMediaUploadDialog() {
        AlertDialog.Builder(this)
            .setMessage(getString(org.wordpress.aztec.R.string.media_upload_dialog_message))
            .setPositiveButton(
                getString(org.wordpress.aztec.R.string.media_upload_dialog_positive),
                null
            )
            .create()
            .also { mediaUploadDialog = it; it.show() }
    }

    private fun showMediaProgressBar() {
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(R.layout.aztec_progress_dialog)
            .create()
            .also {
                it.show()
                it.window?.setGravity(Gravity.CENTER)
                mediaProgressDialog = it
            }
    }

    private fun copyFileToInternalStorage(sourceUri: Uri, newFileName: String): String? {
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

            // Return the absolute path of the copied file
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private fun correctVideoTags(html: String): String {
        return html.replace(videoRegex, """<video src="$1"></video>""")
    }
    // endregion
}