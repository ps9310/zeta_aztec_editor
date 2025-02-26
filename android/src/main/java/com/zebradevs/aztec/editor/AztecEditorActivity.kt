package com.zebradevs.aztec.editor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ToggleButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.forEach
import com.zebradevs.aztec.AztecToolbarOption
import com.zebradevs.aztec.messages.AztecFlutterContainer
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.ImageUtils
import org.wordpress.android.util.PermissionUtils
import org.wordpress.android.util.ToastUtils
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.AztecAttributes
import org.wordpress.aztec.AztecExceptionHandler
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.Html
import org.wordpress.aztec.IHistoryListener
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.glideloader.GlideImageLoader
import org.wordpress.aztec.glideloader.GlideVideoThumbnailLoader
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
import java.util.Locale
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
        private const val REQUEST_MEDIA_CAMERA_PHOTO = 2001
        private const val REQUEST_MEDIA_CAMERA_VIDEO = 2002
        private const val REQUEST_MEDIA_PHOTO = 2003
        private const val REQUEST_MEDIA_VIDEO = 2004

        fun createIntent(
            activity: Activity,
            title: String,
            editorToken: String,
            placeholder: String?,
            initialHtml: String?,
            theme: String?,
            toolbarOptions: List<AztecToolbarOption>
        ): Intent {
            return Intent(activity, AztecEditorActivity::class.java).apply {
                putExtra("title", title)
                putExtra("editorToken", editorToken)
                putExtra("placeholder", placeholder)
                putExtra("initialHtml", initialHtml)
                putExtra("theme", theme)
                putIntegerArrayListExtra("toolbarOptions", ArrayList(toolbarOptions.map { it.raw }))
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
    // endregion

    // region Lifecycle & Setup
    override fun onCreate(savedInstanceState: Bundle?) {
        setupThemeAndToolbar()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aztec_editor)

        setupBackPressHandler()
        setupEditorConfiguration()
        setupAztecEditor(savedInstanceState)
        setupInvalidateOptionsHandler()
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
        val themeParam = intent.getStringExtra("theme") ?: "system"
        when (themeParam.lowercase(Locale.getDefault())) {
            "dark" -> {
                setTheme(R.style.EditorDarkTheme)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            "light" -> {
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

    private fun editorToken(): String {
        return intent.getStringExtra("editorToken") ?: ""
    }

    private fun setupAztecEditor(savedInstanceState: Bundle?) {
        val isDarkMode = isDarkMode()
        val defaultAppBarColor = if (isDarkMode) Color.BLACK else Color.WHITE
        val defaultAppBarTextColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val appBarColor = intent.getIntExtra("appBarColor", defaultAppBarColor)
        val appBarTextColor = intent.getIntExtra("appBarTextColor", defaultAppBarTextColor)
        val visualEditor = findViewById<AztecText>(R.id.aztec)
        val aztecToolbar = findViewById<AztecToolbar>(R.id.formatting_toolbar)
        val topToolbar = findViewById<Toolbar>(R.id.top_toolbar)

        val toolbarOptions = availableToolbarOptions()

        topToolbar.setBackgroundColor(appBarColor)
        topToolbar.setTitleTextColor(appBarTextColor)
        topToolbar.setSubtitleTextColor(appBarTextColor)
        setSupportActionBar(topToolbar)
        setTitle(intent.getStringExtra("title") ?: "")

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(true)
            setHomeButtonEnabled(true)
            title = intent.getStringExtra("title") ?: ""
        }

        visualEditor.enableSamsungPredictiveBehaviorOverride()
        visualEditor.setBackgroundColor(defaultAppBarColor)
        visualEditor.setTextAppearance(android.R.style.TextAppearance)
        visualEditor.hint = intent.getStringExtra("placeholder") ?: getString(R.string.edit_hint)
        aztecToolbar.setBackgroundColor(defaultAppBarColor)

        val toolbarActions = availableToolbarOptions().mapNotNull { toAztecOption(it) }.toSet()
        aztecToolbar.setToolbarItems(
            ToolbarItems.BasicLayout(
                *toolbarActions.toTypedArray()
            )
        )

        aztec = Aztec.with(visualEditor, aztecToolbar, this)
            .setImageGetter(GlideImageLoader(this))
            .setVideoThumbnailGetter(GlideVideoThumbnailLoader(this))
            .setOnImeBackListener(this)
            .setOnTouchListener(this)
            .setHistoryListener(this)
            .setOnImageTappedListener(this)
            .setOnVideoTappedListener(this)
            .setOnAudioTappedListener(this)
            .addOnMediaDeletedListener(this)
            .setOnVideoInfoRequestedListener(this)
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
                            inflate(R.menu.menu_image)
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
                            inflate(R.menu.menu_video)
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

        if (savedInstanceState == null) {
            aztec.initSourceEditorHistory()
        }
    }

    private fun setupAztecToolbar(toolbar: AztecToolbar) {
        val availableToolbarOptions = availableToolbarOptions()
        val toolbarActions = availableToolbarOptions.mapNotNull { toAztecOption(it) }.toSet()

        val stateList = AppCompatResources.getColorStateList(
            this@AztecEditorActivity, R.color.toolbar_button_tint_selector
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
        val options = intent.getIntegerArrayListExtra("toolbarOptions")
        val toolbarOptions =
            options?.map { AztecToolbarOption.ofRaw(it) } ?: AztecToolbarOption.entries
        return toolbarOptions.filterNotNull()
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
        if (AztecToolbarOption.LIST == option) return ToolbarAction.LIST
        if (AztecToolbarOption.UNORDERED_LIST == option) return ToolbarAction.UNORDERED_LIST
        if (AztecToolbarOption.ORDERED_LIST == option) return ToolbarAction.ORDERED_LIST
        if (AztecToolbarOption.TASK_LIST == option) return ToolbarAction.TASK_LIST
        if (AztecToolbarOption.INDENT == option) return ToolbarAction.INDENT
        if (AztecToolbarOption.OUTDENT == option) return ToolbarAction.OUTDENT
        if (AztecToolbarOption.ALIGN_LEFT == option) return ToolbarAction.ALIGN_LEFT
        if (AztecToolbarOption.ALIGN_CENTER == option) return ToolbarAction.ALIGN_CENTER
        if (AztecToolbarOption.ALIGN_RIGHT == option) return ToolbarAction.ALIGN_RIGHT
        if (AztecToolbarOption.QUOTE == option) return ToolbarAction.QUOTE
        if (AztecToolbarOption.LINK == option) return ToolbarAction.LINK
        if (AztecToolbarOption.CODE == option) return ToolbarAction.CODE
        if (AztecToolbarOption.PREFORMAT == option) return ToolbarAction.PREFORMAT
        if (AztecToolbarOption.HORIZONTAL_RULE == option) return ToolbarAction.HORIZONTAL_RULE
        if (AztecToolbarOption.IMAGE == option || AztecToolbarOption.VIDEO == option) return ToolbarAction.ADD_MEDIA_COLLAPSE
        return null
    }

    // endregion

    // region Media Handling
//
//    override fun onActivityResult(
//        requestCode: Int,
//        resultCode: Int,
//        data: Intent?,
//        caller: ComponentCaller
//    ) {
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_MEDIA_CAMERA_PHOTO -> handleCameraPhotoResult()
            REQUEST_MEDIA_PHOTO -> handleGalleryPhotoResult(data)
            REQUEST_MEDIA_CAMERA_VIDEO -> handleVideoResult(data)
            REQUEST_MEDIA_VIDEO -> handleVideoResult(data)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleCameraPhotoResult() {
        val options = BitmapFactory.Options().apply { inDensity = DisplayMetrics.DENSITY_DEFAULT }
        val bitmap = BitmapFactory.decodeFile(mediaPath, options)
        insertImageAndSimulateUpload(bitmap, mediaPath)
    }

    private fun handleGalleryPhotoResult(data: Intent?) {
        mediaPath = data?.data.toString()
        val stream = contentResolver.openInputStream(Uri.parse(mediaPath))
        val options = BitmapFactory.Options().apply { inDensity = DisplayMetrics.DENSITY_DEFAULT }
        val bitmap = BitmapFactory.decodeStream(stream, null, options)
        insertImageAndSimulateUpload(bitmap, mediaPath)
    }

    private fun handleVideoResult(data: Intent?) {
        mediaPath = data?.data.toString()
        createVideoThumbnailAndUpload()
    }

    private fun createVideoThumbnailAndUpload() {
        aztec.visualEditor.videoThumbnailGetter?.loadVideoThumbnail(
            mediaPath,
            object : Html.VideoThumbnailGetter.Callbacks {
                override fun onThumbnailFailed() {}
                override fun onThumbnailLoaded(drawable: Drawable?) {
                    drawable?.let {
                        val bitmap = Bitmap.createBitmap(
                            it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888
                        )
                        Canvas(bitmap).apply {
                            it.setBounds(0, 0, width, height)
                            it.draw(this)
                        }
                        insertVideoAndSimulateUpload(bitmap, mediaPath)
                    }
                }

                override fun onThumbnailLoading(drawable: Drawable?) {}
            },
            resources.displayMetrics.widthPixels
        )
    }

    private fun insertImageAndSimulateUpload(bitmap: Bitmap?, mediaPath: String) {
        val bitmapResized = ImageUtils.getScaledBitmapAtLongestSide(
            bitmap, aztec.visualEditor.maxWidth
        )
        val (id, attrs) = generateAttributesForMedia(mediaPath, isVideo = false)
        aztec.visualEditor.insertImage(BitmapDrawable(resources, bitmapResized), attrs)
        simulateMediaUpload(id, attrs)  // simulates upload progress and updates overlays
        aztec.toolbar.toggleMediaToolbar()
    }

    fun insertVideoAndSimulateUpload(bitmap: Bitmap?, mediaPath: String) {
        val bitmapResized = ImageUtils.getScaledBitmapAtLongestSide(
            bitmap, aztec.visualEditor.maxWidth
        )
        val (id, attrs) = generateAttributesForMedia(mediaPath, isVideo = true)
        aztec.visualEditor.insertVideo(BitmapDrawable(resources, bitmapResized), attrs)
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
        AztecFlutterContainer.flutterApi?.onFileSelected(editorToken(), mediaPath) {
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
                mediaFile = "wp-" + System.currentTimeMillis()
                mediaPath = File.createTempFile(
                    mediaFile, ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ).absolutePath
            } else {
                mediaFile = "wp-" + System.currentTimeMillis() + ".jpg"
                mediaPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        .toString() + File.separator + "Camera" + File.separator + mediaFile
            }
            intent.putExtra(
                MediaStore.EXTRA_OUTPUT,
                FileProvider.getUriForFile(this, "$packageName.provider", File(mediaPath))
            )
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, REQUEST_MEDIA_CAMERA_PHOTO)
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
                startActivityForResult(intent, REQUEST_MEDIA_CAMERA_VIDEO)
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
                startActivityForResult(intent, REQUEST_MEDIA_PHOTO)
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
                startActivityForResult(intent, REQUEST_MEDIA_VIDEO)
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
                    ToastUtils.showToast(this, getString(R.string.permission_required_media_camera))
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
                                this, getString(R.string.permission_required_media_photos)
                            )
                        } else {
                            onPhotosMediaOptionSelected()
                        }
                    }

                    MEDIA_VIDEOS_PERMISSION_REQUEST_CODE -> {
                        if (isPermissionDenied) {
                            ToastUtils.showToast(
                                this, getString(R.string.permission_required_media_videos)
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
        menuInflater.inflate(R.menu.menu_main, menu)
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
        menu?.findItem(R.id.redo)?.isEnabled = aztec.visualEditor.history.redoValid()
        menu?.findItem(R.id.undo)?.isEnabled = aztec.visualEditor.history.undoValid()
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
        val html = aztec.visualEditor.toHtml()
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
        val url = attrs.getValue("src")
        ToastUtils.showToast(this, "Media Deleted! $url")
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
            .setView(ProgressBar(this))
            .create()
            .also {
                it.show()
                mediaProgressDialog = it
            }
    }
    // endregion
}