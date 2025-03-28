import Aztec
import Foundation
import MobileCoreServices
import Photos
import UIKit
import WordPressEditor

enum EditorDemoControllerError : Error {
    case cancelled
}

class CustomFormatBar: Aztec.FormatBar {
    override var intrinsicContentSize: CGSize {
        return CGSize(width: UIView.noIntrinsicMetric, height: 45)
    }
    
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        return CGSize(width: size.width, height: 45)
    }
}

class AztecEditorController: UIViewController {
    
    // Define your debounce delay (in seconds)
    private let debounceDelay: TimeInterval = 0.3
    // Timer to schedule the debounce update
    private var debounceTimer: Timer?
    
    fileprivate(set) lazy var formatBar: Aztec.FormatBar = {
        return self.createToolbar()
    }()
    
    private var richTextView: TextView {
        return editorView.richTextView
    }
    
    private var htmlTextView: UITextView {
        return editorView.htmlTextView
    }
    
    fileprivate(set) lazy var mediaInserter: MediaInserter = {
        let inserter = MediaInserter(
            textView: self.richTextView,
            attachmentTextAttributes: Constants.mediaMessageAttributes
        )
        
        inserter.uploadCallback = { fileURL, completion in
            // Call your onFileSelected function with the editor token and file path.
            AztecFlutterContainer.shared.flutterApi?.onAztecFileSelected(filePath: fileURL.path) { result in
                // When the upload finishes, invoke the completion closure.
                completion(result)
            }
        }
        
        return inserter
    }()
    
    fileprivate(set) lazy var textViewAttachmentDelegate: TextViewAttachmentDelegate = {
        return TextViewAttachmentDelegateProvider(
            baseController: self,
            attachmentTextAttributes: Constants.mediaMessageAttributes,
            authHeaders: self.config.authHeaders
        )
    }()
    
    fileprivate(set) lazy var editorView: Aztec.EditorView = {
        let defaultHTMLFont = UIFontMetrics.default.scaledFont(for: Constants.defaultContentFont)
        
        let editorView = Aztec.EditorView(
            defaultFont: Constants.defaultContentFont,
            defaultHTMLFont: defaultHTMLFont,
            defaultParagraphStyle: .default,
            defaultMissingImage: Constants.defaultMissingImage
        )
        
        editorView.clipsToBounds = false
        
        setupHTMLTextView(editorView.htmlTextView)
        setupRichTextView(editorView.richTextView)
        
        return editorView
    }()
    
    private func setupRichTextView(_ textView: TextView) {
        let accessibilityLabel = NSLocalizedString("Rich Content", comment: "Post Rich content")
        self.configureDefaultProperties(for: textView, accessibilityLabel: accessibilityLabel)
        
        textView.delegate = self
        textView.formattingDelegate = self
        textView.textAttachmentDelegate = self.textViewAttachmentDelegate
        textView.accessibilityIdentifier = "richContentView"
        textView.clipsToBounds = false
        textView.smartDashesType = .no
        textView.smartQuotesType = .no
    }
    
    private func setupHTMLTextView(_ textView: UITextView) {
        let accessibilityLabel = NSLocalizedString("HTML Content", comment: "Post HTML content")
        self.configureDefaultProperties(htmlTextView: textView, accessibilityLabel: accessibilityLabel)
        
        textView.isHidden = true
        textView.delegate = self
        textView.accessibilityIdentifier = "HTMLContentView"
        textView.autocorrectionType = .no
        textView.autocapitalizationType = .none
        textView.clipsToBounds = false
        textView.adjustsFontForContentSizeCategory = true
        textView.smartDashesType = .no
        textView.smartQuotesType = .no
    }
    
    let initialHtml: String?;
    let config: AztecEditorConfig;
    let completion: (Result<String?, any Error>) -> Void
    
    // 3) Undo button with image + optional text
    private lazy var undoButton = UIBarButtonItem(
        image: UIImage(systemName: "arrow.uturn.backward"), // iOS 15+ symbol for “undo”
        style: .plain,
        target: self,
        action: #selector(undoAction)
    )
    
    // 4) Redo button with image + optional text
    private lazy var redoButton = UIBarButtonItem(
        image: UIImage(systemName: "arrow.uturn.forward"), // iOS 15+ symbol for “redo”
        style: .plain,
        target: self,
        action: #selector(redoAction)
    )
    
    // 4) Redo button with image + optional text
    private lazy var doneButton = UIBarButtonItem(
        title: "DONE",
        style: .done,
        target: self,
        action: #selector(doneAction)
    )
    
    private lazy var optionsTablePresenter = OptionsTablePresenter(presentingViewController: self, presentingTextView: richTextView)
    
    // MARK: - Lifecycle Methods
    init(
        initialHtml: String?,
        config: AztecEditorConfig,
        completion: @escaping (Result<String?, any Error>) -> Void
    ) {
        self.config = config
        self.initialHtml = initialHtml
        self.completion = completion
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // 1) Custom back button with SF Symbol icon
        let backButton = UIBarButtonItem(
            image: UIImage(systemName: "chevron.left"),
            style: .plain,
            target: self,
            action: #selector(backAction)
        )
        
        // 2) Left-aligned title as a custom view
        let titleLabel = UILabel()
        titleLabel.text = self.config.title
        titleLabel.textAlignment = .left
        titleLabel.textColor = config.theme == .dark ? .white : .black
        titleLabel.font = UIFont.systemFont(ofSize: 18)
        titleLabel.sizeToFit()
        let titleItem = UIBarButtonItem(customView: titleLabel)
        
        
        // 5) Assign them all to the left side
        navigationItem.leftBarButtonItems = [backButton, titleItem]
        
        // 6) Place a Done button on the right
        navigationItem.rightBarButtonItems = [doneButton, redoButton, undoButton]
        
        navigationItem.title = nil

        setupNavigationBarTheme(isDarkTheme: config.theme == .dark)
        // ---------------------------------
        
        MediaAttachment.defaultAppearance.progressColor = UIColor.blue
        MediaAttachment.defaultAppearance.progressBackgroundColor = UIColor.lightGray
        MediaAttachment.defaultAppearance.progressHeight = 2.0
        MediaAttachment.defaultAppearance.overlayColor = UIColor(red: 46.0/255.0, green: 69.0/255.0, blue: 83.0/255.0, alpha: 0.6)
        
        // Uncomment to add a border
        // MediaAttachment.defaultAppearance.overlayBorderWidth = 3.0
        // MediaAttachment.defaultAppearance.overlayBorderColor = UIColor(red: 0.0/255.0, green: 135.0/255.0, blue: 190.0/255.0, alpha: 0.8)
        
        edgesForExtendedLayout = UIRectEdge()
        navigationController?.navigationBar.isTranslucent = false
        view.addSubview(editorView)
        
        editorView.translatesAutoresizingMaskIntoConstraints = false
        
        editorView.richTextView.textContainer.lineFragmentPadding = 0
        // color setup
        if #available(iOS 13.0, *) {
            view.backgroundColor = UIColor.systemBackground
            editorView.htmlTextView.textColor = UIColor.label
            editorView.richTextView.textColor = UIColor.label
            editorView.richTextView.blockquoteBackgroundColor = UIColor.secondarySystemBackground
            editorView.richTextView.preBackgroundColor = UIColor.secondarySystemBackground
            editorView.richTextView.blockquoteBorderColors = [.secondarySystemFill, .systemTeal, .systemBlue]
            var attributes = editorView.richTextView.linkTextAttributes
            attributes?[.foregroundColor] = UIColor.link
        } else {
            view.backgroundColor = UIColor.white
        }
        // Don't allow scroll while the constraints are being setup and text set
        editorView.isScrollEnabled = false
        configureConstraints()
        registerAttachmentImageProviders()
        
        editorView.setHTML(initialHtml ?? "")
    }
    
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        debounceTimer?.invalidate()
    }
    
    // MARK: - Navigation Bar Button Actions`
    
    @objc func undoAction() {
        richTextView.undoManager?.undo()
    }
    
    @objc func redoAction() {
        richTextView.undoManager?.redo()
    }
    
    @objc func doneAction() {
        view.endEditing(true)
        completion(.success(correctUnorderedList(richTextView.getHTML())))
        dismiss(animated: true)
    }
    
    @objc func backAction() {
        view.endEditing(true)
        completion(.failure(EditorDemoControllerError.cancelled))
        dismiss(animated: true, completion: nil)
    }
    
    func sendUpdateToFlutter() {
        // Invalidate any previous timer
        debounceTimer?.invalidate()
        
        // Schedule a new timer for the debounce delay
        debounceTimer = Timer.scheduledTimer(withTimeInterval: debounceDelay, repeats: false) { [weak self] _ in
            guard let self = self else { return }
            let htmlContent = correctUnorderedList(richTextView.getHTML())
            AztecFlutterContainer.shared.flutterApi?.onAztecHtmlChanged(data: htmlContent) { _ in
            }
        }
    }
    
    func correctUnorderedList(_ html: String) -> String {
        // Pattern to match any <ul>...</ul> block (including newlines)
        let pattern = "<ul>(.*?)</ul>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators]) else {
            return html
        }
        
        // Get all matches in the HTML string.
        let nsRange = NSRange(html.startIndex..<html.endIndex, in: html)
        let matches = regex.matches(in: html, options: [], range: nsRange)
        
        // Collect the ranges and inner text for those lists that do not contain any <li> tags.
        var invalidMatches: [(range: NSRange, content: String)] = []
        for match in matches {
            if let innerRange = Range(match.range(at: 1), in: html) {
                let innerText = String(html[innerRange])
                if !innerText.contains("<li>") {
                    invalidMatches.append((range: match.range, content: innerText))
                }
            }
        }
        
        // If there are no invalid lists, return the original HTML.
        if invalidMatches.isEmpty {
            return html
        }
        
        // Compute the combined range spanning from the first to the last invalid list.
        let firstRange = invalidMatches.first!.range
        let lastRange = invalidMatches.last!.range
        let combinedRange = NSRange(location: firstRange.location,
                                    length: (lastRange.location + lastRange.length) - firstRange.location)
        
        // Build the new valid list by wrapping each invalid list’s inner text in <li> tags.
        var newList = "<ul>"
        for item in invalidMatches {
            let trimmed = item.content.trimmingCharacters(in: .whitespacesAndNewlines)
            newList += "<li>\(trimmed)</li>"
        }
        newList += "</ul>"
        
        // Replace the combined invalid range with the new valid list.
        if let range = Range(combinedRange, in: html) {
            let correctedHTML = html.replacingCharacters(in: range, with: newList)
            return correctedHTML
        }
        
        return html
    }
    
    // MARK: - Navigation Bar Theme Setup
    /// Configures the navigation bar appearance for a light or dark theme.
    private func setupNavigationBarTheme(isDarkTheme: Bool) {
        self.overrideUserInterfaceStyle = isDarkTheme ? .dark : .light
        
        // Create a new appearance object
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()  // ensures a solid color background
        
        if isDarkTheme {
            appearance.backgroundColor = .black
            appearance.titleTextAttributes = [.foregroundColor: UIColor.white]
            navigationController?.navigationBar.tintColor = .white
            navigationController?.navigationBar.barStyle = .black
        } else {
            appearance.backgroundColor = .white
            appearance.titleTextAttributes = [.foregroundColor: UIColor.black]
            navigationController?.navigationBar.tintColor = .black
            navigationController?.navigationBar.barStyle = .default
        }
        
        // Apply the appearance to both standard and scroll edge states
        navigationController?.navigationBar.standardAppearance = appearance
        navigationController?.navigationBar.scrollEdgeAppearance = appearance
        
        // Make it fully opaque (no translucency)
        navigationController?.navigationBar.isTranslucent = false
    }
    

    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(keyboardWillShow), name: UIResponder.keyboardWillShowNotification, object: nil)
        nc.addObserver(self, selector: #selector(keyboardWillHide), name: UIResponder.keyboardWillHideNotification, object: nil)
        if let characterLimit = config.characterLimit, characterLimit > 0 {
            enforceCharacterLimitAttributed(for: richTextView, limit: Int(characterLimit))
        }
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // Re-enable scroll after setup is done
        editorView.isScrollEnabled = true
        richTextView.becomeFirstResponder()
        let endPosition = richTextView.endOfDocument
        richTextView.selectedTextRange = richTextView.textRange(from: endPosition, to: endPosition)
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        let nc = NotificationCenter.default
        nc.removeObserver(self, name: UIResponder.keyboardWillShowNotification, object: nil)
        nc.removeObserver(self, name: UIResponder.keyboardWillHideNotification, object: nil)
    }
    
    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        optionsTablePresenter.dismiss()
    }
    
    // MARK: - Configuration Methods
    override func updateViewConstraints() {
        super.updateViewConstraints()
    }
    
    private func configureConstraints() {
        let layoutGuide = view.readableContentGuide
        
        NSLayoutConstraint.activate([
            editorView.leadingAnchor.constraint(equalTo: layoutGuide.leadingAnchor),
            editorView.trailingAnchor.constraint(equalTo: layoutGuide.trailingAnchor),
            editorView.topAnchor.constraint(equalTo: view.topAnchor),
            editorView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    func enforceCharacterLimitAttributed(for textView: UITextView, limit: Int) {
        guard let attributedText = textView.attributedText, attributedText.length > limit else { return }
        let range = NSRange(location: 0, length: limit)
        let truncated = attributedText.attributedSubstring(from: range)
        textView.attributedText = truncated
    }
    
    private func configureDefaultProperties(for textView: TextView, accessibilityLabel: String) {
        textView.accessibilityLabel = accessibilityLabel
        textView.font = Constants.defaultContentFont
        textView.keyboardDismissMode = .interactive
        if #available(iOS 13.0, *) {
            textView.textColor = UIColor.label
            textView.defaultTextColor = UIColor.label
        } else {
            textView.textColor = UIColor(red: 0x1A/255.0, green: 0x1A/255.0, blue: 0x1A/255.0, alpha: 1)
            textView.defaultTextColor = UIColor(red: 0x1A/255.0, green: 0x1A/255.0, blue: 0x1A/255.0, alpha: 1)
        }
        textView.linkTextAttributes = [
            .foregroundColor: UIColor(red: 0x01/255.0, green: 0x60/255.0, blue: 0x87/255.0, alpha: 1),
            NSAttributedString.Key.underlineStyle: NSNumber(value: NSUnderlineStyle.single.rawValue)
        ]
    }
    
    private func configureDefaultProperties(htmlTextView textView: UITextView, accessibilityLabel: String) {
        textView.accessibilityLabel = accessibilityLabel
        textView.font = Constants.defaultContentFont
        textView.keyboardDismissMode = .interactive
        if #available(iOS 13.0, *) {
            textView.textColor = UIColor.label
            if let htmlStorage = textView.textStorage as? HTMLStorage {
                htmlStorage.textColor = UIColor.label
            }
        } else {
            textView.textColor = UIColor(red: 0x1A/255.0, green: 0x1A/255.0, blue: 0x1A/255.0, alpha: 1)
        }
        textView.linkTextAttributes = [
            .foregroundColor: UIColor(red: 0x01/255.0, green: 0x60/255.0, blue: 0x87/255.0, alpha: 1),
            NSAttributedString.Key.underlineStyle: NSNumber(value: NSUnderlineStyle.single.rawValue)
        ]
    }
    
    private func registerAttachmentImageProviders() {
        let providers: [TextViewAttachmentImageProvider] = [
            GutenpackAttachmentRenderer(),
            SpecialTagAttachmentRenderer(),
            CommentAttachmentRenderer(font: Constants.defaultContentFont),
            HTMLAttachmentRenderer(font: Constants.defaultHtmlFont),
        ]
        
        for provider in providers {
            richTextView.registerAttachmentImageProvider(provider)
        }
    }
    
    // MARK: - Helpers
    
    @IBAction func toggleEditingMode() {
        formatBar.overflowToolbar(expand: true)
        editorView.toggleEditingMode()
    }
    
    // MARK: - Options VC
    
    private let formattingIdentifiersWithOptions: [FormattingIdentifier] = [.orderedlist, .unorderedlist, .p, .header1, .header2, .header3, .header4, .header5, .header6]
    
    private func formattingIdentifierHasOptions(_ formattingIdentifier: FormattingIdentifier) -> Bool {
        return formattingIdentifiersWithOptions.contains(formattingIdentifier)
    }
    
    // MARK: - Keyboard Handling
    
    @objc func keyboardWillShow(_ notification: Notification) {
        guard let userInfo = notification.userInfo as? [String: AnyObject],
              let keyboardFrame = (userInfo[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue else {
            return
        }
        refreshInsets(forKeyboardFrame: keyboardFrame)
    }
    
    @objc func keyboardWillHide(_ notification: Notification) {
        guard let userInfo = notification.userInfo as? [String: AnyObject],
              let keyboardFrame = (userInfo[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue else {
            return
        }
        refreshInsets(forKeyboardFrame: keyboardFrame)
        optionsTablePresenter.dismiss()
    }
    
    fileprivate func refreshInsets(forKeyboardFrame keyboardFrame: CGRect) {
        let localKeyboardOrigin = view.convert(keyboardFrame.origin, from: nil)
        let keyboardInset = max(view.frame.height - localKeyboardOrigin.y, 0)
        
        let contentInset = UIEdgeInsets(
            top: editorView.contentInset.top,
            left: 0,
            bottom: keyboardInset,
            right: 0)
        
        editorView.contentInset = contentInset
    }
    
    func updateFormatBar() {
        undoButton.isEnabled = richTextView.undoManager?.canUndo ?? false
        redoButton.isEnabled = richTextView.undoManager?.canRedo ?? false
        
        guard let toolbar = richTextView.inputAccessoryView as? Aztec.FormatBar else {
            return
        }
        
        let identifiers: Set<FormattingIdentifier>
        if richTextView.selectedRange.length > 0 {
            identifiers = richTextView.formattingIdentifiersSpanningRange(richTextView.selectedRange)
        } else {
            identifiers = richTextView.formattingIdentifiersForTypingAttributes()
        }
        
        toolbar.selectItemsMatchingIdentifiers(identifiers.map({ $0.rawValue }))
    }
    
    override var keyCommands: [UIKeyCommand] {
        if richTextView.isFirstResponder {
            return [
                UIKeyCommand(title: NSLocalizedString("Bold", comment: "Discoverability title for bold formatting keyboard shortcut."), action: #selector(toggleBold), input:"B", modifierFlags: .command),
                UIKeyCommand(title: NSLocalizedString("Italic", comment: "Discoverability title for italic formatting keyboard shortcut."), action: #selector(toggleItalic), input:"I", modifierFlags: .command),
                UIKeyCommand(title: NSLocalizedString("Strikethrough", comment:"Discoverability title for strikethrough formatting keyboard shortcut."), action: #selector(toggleStrikethrough), input:"S", modifierFlags: [.command]),
                UIKeyCommand(title: NSLocalizedString("Underline", comment:"Discoverability title for underline formatting keyboard shortcut."), action: #selector(toggleUnderline), input:"U", modifierFlags: .command),
                UIKeyCommand(title: NSLocalizedString("Block Quote", comment: "Discoverability title for block quote keyboard shortcut."), action: #selector(toggleBlockquote), input:"Q", modifierFlags:[.command, .alternate]),
                UIKeyCommand(title: NSLocalizedString("Insert Link", comment: "Discoverability title for insert link keyboard shortcut."), action: #selector(toggleLink), input:"K", modifierFlags:.command),
                UIKeyCommand(title: NSLocalizedString("Insert Media", comment: "Discoverability title for insert media keyboard shortcut."), action: #selector(showImagePicker), input:"M", modifierFlags:[.command, .alternate]),
                UIKeyCommand(title: NSLocalizedString("Bullet List", comment: "Discoverability title for bullet list keyboard shortcut."), action: #selector(toggleUnorderedList), input:"U", modifierFlags:[.command, .alternate]),
                UIKeyCommand(title: NSLocalizedString("Numbered List", comment:"Discoverability title for numbered list keyboard shortcut."), action: #selector(toggleOrderedList), input:"O", modifierFlags:[.command, .alternate]),
                UIKeyCommand(title: NSLocalizedString("Toggle HTML Source ", comment: "Discoverability title for HTML keyboard shortcut."), action: #selector(toggleEditingMode), input:"H", modifierFlags:[.command, .shift])
            ]
        } else if htmlTextView.isFirstResponder {
            return [
                UIKeyCommand(title: NSLocalizedString("Toggle HTML Source ", comment: "Discoverability title for HTML keyboard shortcut."), action: #selector(toggleEditingMode), input:"H", modifierFlags:[.command, .shift])
            ]
        }
        return []
    }
    
    // MARK: - Sample Content
    
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        if richTextView.resignFirstResponder() {
            richTextView.becomeFirstResponder()
        }
        
        if htmlTextView.resignFirstResponder() {
            htmlTextView.becomeFirstResponder()
        }
    }
}

extension AztecEditorController : UITextViewDelegate {
    func textViewDidChangeSelection(_ textView: UITextView) {
        updateFormatBar()
        changeRichTextInputView(to: nil)
    }
    
    func textViewDidChange(_ textView: UITextView) {
        switch textView {
            case richTextView:
                updateFormatBar()
                sendUpdateToFlutter()
            default:
                break
        }
    }
    
    func textViewShouldBeginEditing(_ textView: UITextView) -> Bool {
        switch textView {
            case richTextView:
                formatBar.enabled = true
                editorView.richTextView.inputAccessoryView = formatBar
            case htmlTextView:
                formatBar.enabled = false
                let htmlButton = formatBar.items.first(where: { $0.identifier == FormattingIdentifier.sourcecode.rawValue })
                htmlButton?.isEnabled = true
            default:
                break
        }
        
        return true
    }
    
    func textView(_ textView: UITextView, shouldInteractWith URL: URL, in characterRange: NSRange, interaction: UITextItemInteraction) -> Bool {
        return false
    }
    
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        // No title position update needed anymore
    }
    
    func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
        if let characterLimit = config.characterLimit, characterLimit > 0 {
            if text.isEmpty {
                return true
            }
            
            // Get the current text, or use empty string if nil
            let currentText = textView.text ?? ""
            // Determine the range in the current text corresponding to the change
            guard let stringRange = Range(range, in: currentText) else { return false }
            // Create the updated text string
            let updatedText = currentText.replacingCharacters(in: stringRange, with: text)
            // Allow change if updated text is within the limit
            return updatedText.count <= characterLimit
        } else {
            return true
        }
    }
}

extension AztecEditorController : Aztec.TextViewFormattingDelegate {
    func textViewCommandToggledAStyle() {
        updateFormatBar()
    }
}

// MARK: - Format Bar Delegate

extension AztecEditorController : Aztec.FormatBarDelegate {
    func formatBarTouchesBegan(_ formatBar: FormatBar) {
    }
    
    func formatBar(_ formatBar: FormatBar, didChangeOverflowState state: FormatBarOverflowState) {
        switch state {
            case .hidden:
                print("Format bar collapsed")
            case .visible:
                print("Format bar expanded")
        }
    }
}

// MARK: - Format Bar Actions
extension AztecEditorController {
    func handleAction(for barItem: FormatBarItem) {
        guard let identifier = barItem.identifier,
              let formattingIdentifier = FormattingIdentifier(rawValue: identifier) else {
            return
        }
        
        if !formattingIdentifierHasOptions(formattingIdentifier) {
            optionsTablePresenter.dismiss()
        }
        
        switch formattingIdentifier {
            case .bold:
                toggleBold()
            case .italic:
                toggleItalic()
            case .underline:
                toggleToolbarUnderline()
            case .strikethrough:
                toggleStrikethrough()
            case .blockquote:
                toggleBlockquote()
            case .unorderedlist:
                toggleUnorderedList()
            case .orderedlist:
                toggleOrderedList()
            case .link:
                toggleLink()
            case .media:
                break
            case .sourcecode:
                toggleEditingMode()
            case .p, .header1, .header2, .header3, .header4, .header5, .header6:
                toggleHeader(fromItem: barItem)
            case .more:
                insertMoreAttachment()
            case .horizontalruler:
                insertHorizontalRuler()
            case .code:
                toggleCode()
            default:
                break
        }
        
        updateFormatBar()
    }
    
    @objc func toggleBold() {
        richTextView.toggleBold(range: richTextView.selectedRange)
    }
    
    @objc func toggleItalic() {
        richTextView.toggleItalic(range: richTextView.selectedRange)
    }
    
    func toggleToolbarUnderline() { // 1
        richTextView.toggleUnderline(range: richTextView.selectedRange)
    }
    
    @objc func toggleStrikethrough() {
        richTextView.toggleStrikethrough(range: richTextView.selectedRange)
    }
    
    @objc func toggleBlockquote() {
        richTextView.toggleBlockquote(range: richTextView.selectedRange)
    }
    
    @objc func toggleCode() {
        richTextView.toggleCode(range: richTextView.selectedRange)
    }
    
    @objc func toggleUnorderedList() {
        richTextView.toggleUnorderedList(range: richTextView.selectedRange)
    }
    
    @objc func toggleOrderedList() {
        richTextView.toggleOrderedList(range: richTextView.selectedRange)
    }
    
    func insertHorizontalRuler() {
        richTextView.replaceWithHorizontalRuler(at: richTextView.selectedRange)
    }
    
    func toggleHeader(fromItem item: FormatBarItem) {
        guard !optionsTablePresenter.isOnScreen() else {
            optionsTablePresenter.dismiss()
            return
        }
        
        let options = Constants.headers.map { headerType -> OptionsTableViewOption in
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: CGFloat(headerType.fontSize))
            ]
            let title = NSAttributedString(string: headerType.description, attributes: attributes)
            return OptionsTableViewOption(image: headerType.iconImage, title: title)
        }
        
        let selectedIndex = Constants.headers.firstIndex(of: headerLevelForSelectedText())
        let optionsTableViewController = OptionsTableViewController(options: options)
        optionsTableViewController.cellDeselectedTintColor = .gray
        
        optionsTablePresenter.present(
            optionsTableViewController,
            fromBarItem: item,
            selectedRowIndex: selectedIndex,
            onSelect: { [weak self] selected in
                guard let range = self?.richTextView.selectedRange else { return }
                self?.richTextView.toggleHeader(Constants.headers[selected], range: range)
                self?.optionsTablePresenter.dismiss()
            }
        )
    }
    
    func changeRichTextInputView(to: UIView?) {
        if richTextView.inputView == to { return }
        richTextView.inputView = to
        richTextView.reloadInputViews()
    }
    
    func headerLevelForSelectedText() -> Header.HeaderType {
        var identifiers = Set<FormattingIdentifier>()
        if richTextView.selectedRange.length > 0 {
            identifiers = richTextView.formattingIdentifiersSpanningRange(richTextView.selectedRange)
        } else {
            identifiers = richTextView.formattingIdentifiersForTypingAttributes()
        }
        let mapping: [FormattingIdentifier: Header.HeaderType] = [
            .header1: .h1,
            .header2: .h2,
            .header3: .h3,
            .header4: .h4,
            .header5: .h5,
            .header6: .h6,
        ]
        for (key, value) in mapping {
            if identifiers.contains(key) {
                return value
            }
        }
        return .none
    }
    
    func listTypeForSelectedText() -> TextList.Style? {
        var identifiers = Set<FormattingIdentifier>()
        if richTextView.selectedRange.length > 0 {
            identifiers = richTextView.formattingIdentifiersSpanningRange(richTextView.selectedRange)
        } else {
            identifiers = richTextView.formattingIdentifiersForTypingAttributes()
        }
        let mapping: [FormattingIdentifier: TextList.Style] = [
            .orderedlist: .ordered,
            .unorderedlist: .unordered
        ]
        for (key, value) in mapping {
            if identifiers.contains(key) {
                return value
            }
        }
        return nil
    }
    
    @objc func toggleLink() {
        var linkTitle = ""
        var linkURL: URL? = nil
        var linkRange = richTextView.selectedRange
        if let expandedRange = richTextView.linkFullRange(forRange: richTextView.selectedRange) {
            linkRange = expandedRange
            linkURL = richTextView.linkURL(forRange: expandedRange)
        }
        let target = richTextView.linkTarget(forRange: richTextView.selectedRange)
        linkTitle = richTextView.attributedText.attributedSubstring(from: linkRange).string
        let allowTextEdit = !richTextView.attributedText.containsAttachments(in: linkRange)
        showLinkDialog(forURL: linkURL, text: linkTitle, target: target, range: linkRange, allowTextEdit: allowTextEdit)
    }
    
    func insertMoreAttachment() {
        richTextView.replace(richTextView.selectedRange, withComment: Constants.moreAttachmentText)
    }
    
    func showLinkDialog(forURL url: URL?, text: String?, target: String?, range: NSRange, allowTextEdit: Bool = true) {
        let isInsertingNewLink = (url == nil)
        var urlToUse = url
        
        if isInsertingNewLink {
            let pasteboard = UIPasteboard.general
            if let pastedURL = pasteboard.value(forPasteboardType: String(kUTTypeURL)) as? URL {
                urlToUse = pastedURL
            }
        }
        
        let insertButtonTitle = isInsertingNewLink ? NSLocalizedString("Insert Link", comment:"Label action for inserting a link on the editor") : NSLocalizedString("Update Link", comment:"Label action for updating a link on the editor")
        let removeButtonTitle = NSLocalizedString("Remove Link", comment:"Label action for removing a link from the editor")
        let cancelButtonTitle = NSLocalizedString("Cancel", comment:"Cancel button")
        
        let alertController = UIAlertController(title: insertButtonTitle,
                                                message: nil,
                                                preferredStyle: .alert)
        alertController.view.accessibilityIdentifier = "linkModal"
        
        alertController.addTextField { [weak self] textField in
            textField.clearButtonMode = .always
            textField.placeholder = NSLocalizedString("URL", comment:"URL text field placeholder")
            textField.keyboardType = .URL
            textField.textContentType = .URL
            textField.text = urlToUse?.absoluteString
            textField.addTarget(self, action: #selector(AztecEditorController.alertTextFieldDidChange), for: .editingChanged)
            textField.accessibilityIdentifier = "linkModalURL"
        }
        
        if allowTextEdit {
            alertController.addTextField { textField in
                textField.clearButtonMode = .always
                textField.placeholder = NSLocalizedString("Link Text", comment:"Link text field placeholder")
                textField.isSecureTextEntry = false
                textField.autocapitalizationType = .sentences
                textField.autocorrectionType = .default
                textField.spellCheckingType = .default
                textField.text = text
                textField.accessibilityIdentifier = "linkModalText"
            }
        }
        
        alertController.addTextField { textField in
            textField.clearButtonMode = .always
            textField.placeholder = NSLocalizedString("Target", comment:"Link text field placeholder")
            textField.isSecureTextEntry = false
            textField.autocapitalizationType = .sentences
            textField.autocorrectionType = .default
            textField.spellCheckingType = .default
            textField.text = target
            textField.accessibilityIdentifier = "linkModalTarget"
        }
        
        let insertAction = UIAlertAction(title: insertButtonTitle, style: .default) { [weak self] action in
            self?.richTextView.becomeFirstResponder()
            guard let textFields = alertController.textFields else { return }
            let linkURLField = textFields[0]
            let linkTextField = textFields[1]
            let linkTargetField = textFields[2]
            let linkURLString = linkURLField.text
            var linkTitle = linkTextField.text
            let target = linkTargetField.text
            
            if linkTitle == nil || linkTitle!.isEmpty {
                linkTitle = linkURLString
            }
            
            guard let urlString = linkURLString, let url = URL(string: urlString) else { return }
            if allowTextEdit, let title = linkTitle {
                self?.richTextView.setLink(url, title: title, target: target, inRange: range)
            } else {
                self?.richTextView.setLink(url, target: target, inRange: range)
            }
        }
        insertAction.accessibilityLabel = "insertLinkButton"
        
        let removeAction = UIAlertAction(title: removeButtonTitle, style: .destructive) { [weak self] action in
            self?.richTextView.becomeFirstResponder()
            self?.richTextView.removeLink(inRange: range)
        }
        
        let cancelAction = UIAlertAction(title: cancelButtonTitle, style: .cancel) { [weak self] action in
            self?.richTextView.becomeFirstResponder()
        }
        
        alertController.addAction(insertAction)
        if !isInsertingNewLink {
            alertController.addAction(removeAction)
        }
        alertController.addAction(cancelAction)
        
        if let text = alertController.textFields?.first?.text {
            insertAction.isEnabled = !text.isEmpty
        }
        
        present(alertController, animated: true, completion: nil)
    }
    
    @objc func alertTextFieldDidChange(_ textField: UITextField) {
        guard let alertController = presentedViewController as? UIAlertController,
              let urlFieldText = alertController.textFields?.first?.text,
              let insertAction = alertController.actions.first else { return }
        insertAction.isEnabled = !urlFieldText.isEmpty
    }
    
    @objc func showImagePicker() {
        view.endEditing(true)
        
        // First alert: Choose media type
        let mediaTypeAlert = UIAlertController(title: "Choose Media", message: nil, preferredStyle: .actionSheet)
        
        let photoAction = UIAlertAction(title: "Photo", style: .default) { _ in
            self.showSourcePicker(forMediaType: "public.image")
        }
        
        let videoAction = UIAlertAction(title: "Video", style: .default) { _ in
            self.showSourcePicker(forMediaType: "public.movie")
        }
        
        let cancelAction = UIAlertAction(title: "Cancel", style: .cancel, handler: nil)
        
        mediaTypeAlert.addAction(photoAction)
        mediaTypeAlert.addAction(videoAction)
        mediaTypeAlert.addAction(cancelAction)
        
        present(mediaTypeAlert, animated: true, completion: nil)
    }
    
    func showSourcePicker(forMediaType mediaType: String) {
        let sourceAlert = UIAlertController(title: "Select Source", message: nil, preferredStyle: .actionSheet)
        
        let cameraAction = UIAlertAction(title: "Camera", style: .default) { _ in
            self.showPicker(source: .camera, mediaType: mediaType)
        }
        
        let libraryAction = UIAlertAction(title: "Library", style: .default) { _ in
            self.showPicker(source: .photoLibrary, mediaType: mediaType)
        }
        
        let cancelAction = UIAlertAction(title: "Cancel", style: .cancel, handler: nil)
        
        sourceAlert.addAction(cameraAction)
        sourceAlert.addAction(libraryAction)
        sourceAlert.addAction(cancelAction)
        
        present(sourceAlert, animated: true, completion: nil)
    }
    
    func showPicker(source: UIImagePickerController.SourceType, mediaType: String) {
        guard UIImagePickerController.isSourceTypeAvailable(source) else {
            print("\(source == .camera ? "Camera" : "Photo Library") is not available on this device.")
            return
        }
        
        let picker = UIImagePickerController()
        picker.sourceType = source
        picker.mediaTypes = [mediaType]
        picker.delegate = self
        picker.allowsEditing = false
        picker.navigationBar.isTranslucent = false
        picker.modalPresentationStyle = .currentContext
        
        present(picker, animated: true, completion: nil)
    }
    
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        // Dismiss the picker if the user cancels.
        picker.dismiss(animated: true, completion: nil)
    }
    
    
    func makeToolbarButton(identifier: FormattingIdentifier) -> FormatBarItem {
        let button = FormatBarItem(image: identifier.iconImage, identifier: identifier.rawValue)
        button.accessibilityLabel = identifier.accessibilityLabel
        button.accessibilityIdentifier = identifier.accessibilityIdentifier
        return button
    }
    
    func createToolbar() -> Aztec.FormatBar {
        var toolbarOptions = Array(config.toolbarOptions ?? [])
        toolbarOptions.removeAll { option in
            option == .image || option == .video
        }
        
        
        let toolbar = CustomFormatBar()
        toolbar.backgroundColor = UIColor.systemBackground
        toolbar.tintColor = UIColor.label
        toolbar.highlightedTintColor = UIColor.systemBlue
        toolbar.selectedTintColor = UIColor.systemBlue
        toolbar.disabledTintColor = .systemGray4
        toolbar.dividerTintColor = UIColor.separator
        toolbar.overflowToggleIcon = UIImage(systemName: "ellipsis")!
        
        // Set the frame height to 45 to match intrinsicContentSize
        toolbar.frame = CGRect(x: 0, y: 0, width: view.frame.width, height: 45)
        toolbar.formatter = self
        toolbar.overflowToolbar(expand: true)
        toolbar.autoresizingMask = [.flexibleHeight]
        
        if config.toolbarOptions?.contains(.image) ?? false || config.toolbarOptions?.contains(.video) ?? false {
            let mediaItem = makeToolbarButton(identifier: .media)
            toolbar.leadingItem = mediaItem
        }
        
        let scrollableItems = toolbarOptions.map(aztecIdentifier(from:)).map(makeToolbarButton(identifier:))
        toolbar.setDefaultItems(scrollableItems)
        
        toolbar.barItemHandler = { [weak self] item in
            self?.handleAction(for: item)
        }
        
        toolbar.leadingItemHandler = { [weak self] item in
            self?.showImagePicker()
        }
        
        return toolbar
    }
    
    func aztecIdentifier(from toolbarOption: AztecToolbarOption) -> FormattingIdentifier {
        switch(toolbarOption) {
            case .heading: return .p
            case .bold: return .bold
            case .italic: return .italic
            case .underline: return .underline
            case .strikethrough: return .strikethrough
            case .unorderedList: return .unorderedlist
            case .orderedList: return .orderedlist
            case .quote : return .blockquote
            case .link : return .link
            case .code : return .code
            case .horizontalRule : return .horizontalruler
            case .image : return .media
            case .video : return .media
        }
    }
}

extension AztecEditorController: UINavigationControllerDelegate {
}

extension AztecEditorController: UIImagePickerControllerDelegate {
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        let info = convertFromUIImagePickerControllerInfoKeyDictionary(info)
        dismiss(animated: true, completion: nil)
        richTextView.becomeFirstResponder()
        guard let mediaType = info[convertFromUIImagePickerControllerInfoKey(UIImagePickerController.InfoKey.mediaType)] as? String else {
            return
        }
        
        let typeImage = UTType.image.identifier
        let typeMovie = UTType.movie.identifier
        
        switch mediaType {
            case typeImage:
                guard let image = info[convertFromUIImagePickerControllerInfoKey(UIImagePickerController.InfoKey.originalImage)] as? UIImage else {
                    return
                }
                
                picker.dismiss(animated: true) {
                    self.mediaInserter.insertImage(image)
                }
                
            case typeMovie:
                guard let videoURL = info[convertFromUIImagePickerControllerInfoKey(UIImagePickerController.InfoKey.mediaURL)] as? URL else {
                    return
                }
                
                picker.dismiss(animated: true) {
                    self.mediaInserter.insertVideo(videoURL)
                }
            default:
                print("Media type not supported: \(mediaType)")
        }
    }
}

extension AztecEditorController {
    static var tintedMissingImage: UIImage = {
        if #available(iOS 13.0, *) {
            return UIImage(systemName: "photo")!.withTintColor(.label)
        } else {
            return UIImage(systemName: "photo")!
        }
    }()
    
    struct Constants {
        static let defaultContentFont   = UIFont.systemFont(ofSize: 14)
        static let defaultHtmlFont      = UIFont.systemFont(ofSize: 24)
        static let defaultMissingImage  = tintedMissingImage
        static let formatBarIconSize    = CGSize(width: 24.0, height: 24.0)
        static let headers              = [Header.HeaderType.none, .h1, .h2, .h3, .h4, .h5, .h6]
        static let moreAttachmentText   = "more"
        static let titleInsets          = UIEdgeInsets(top: 5, left: 0, bottom: 5, right: 0)
        static var mediaMessageAttributes: [NSAttributedString.Key: Any] {
            let paragraphStyle = NSMutableParagraphStyle()
            paragraphStyle.alignment = .center
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 15, weight: .semibold),
                .paragraphStyle: paragraphStyle,
                .foregroundColor: UIColor.white
            ]
            return attributes
        }
    }
}

extension UIImage {
    static func systemImage(_ name: String) -> UIImage {
        guard let image = UIImage(systemName: name) else {
            assertionFailure("Missing system image: \(name)")
            return UIImage()
        }
        return image
    }
}

extension FormattingIdentifier {
    var iconImage: UIImage {
        switch self {
            case .media:
                return UIImage.systemImage("plus.circle")
            case .p:
                return UIImage.systemImage("textformat.size")
            case .bold:
                return UIImage.systemImage("bold")
            case .italic:
                return UIImage.systemImage("italic")
            case .underline:
                return UIImage.systemImage("underline")
            case .strikethrough:
                return UIImage.systemImage("strikethrough")
            case .blockquote:
                return UIImage.systemImage("text.quote")
            case .orderedlist:
                return UIImage.systemImage("list.number")
            case .unorderedlist:
                return UIImage.systemImage("list.bullet")
            case .link:
                return UIImage.systemImage("link")
            case .horizontalruler:
                return UIImage.systemImage("minus")
            case .sourcecode:
                return UIImage.systemImage("chevron.left.slash.chevron.right")
            case .more:
                return UIImage.systemImage("textformat.abc.dottedunderline")
            case .header1:
                return UIImage.systemImage("textformat.size")
            case .header2:
                return UIImage.systemImage("textformat.size")
            case .header3:
                return UIImage.systemImage("textformat.size")
            case .header4:
                return UIImage.systemImage("textformat.size")
            case .header5:
                return UIImage.systemImage("textformat.size")
            case .header6:
                return UIImage.systemImage("textformat.size")
            case .code:
                return UIImage.systemImage("textbox")
            default:
                return UIImage.systemImage("info")
        }
    }
    
    var accessibilityIdentifier: String {
        switch self {
            case .media:
                return "formatToolbarInsertMedia"
            case .p:
                return "formatToolbarSelectParagraphStyle"
            case .bold:
                return "formatToolbarToggleBold"
            case .italic:
                return "formatToolbarToggleItalic"
            case .underline:
                return "formatToolbarToggleUnderline"
            case .strikethrough:
                return "formatToolbarToggleStrikethrough"
            case .blockquote:
                return "formatToolbarToggleBlockquote"
            case .orderedlist:
                return "formatToolbarToggleListOrdered"
            case .unorderedlist:
                return "formatToolbarToggleListUnordered"
            case .link:
                return "formatToolbarInsertLink"
            case .horizontalruler:
                return "formatToolbarInsertHorizontalRuler"
            case .sourcecode:
                return "formatToolbarToggleHtmlView"
            case .more:
                return "formatToolbarInsertMore"
            case .header1:
                return "formatToolbarToggleH1"
            case .header2:
                return "formatToolbarToggleH2"
            case .header3:
                return "formatToolbarToggleH3"
            case .header4:
                return "formatToolbarToggleH4"
            case .header5:
                return "formatToolbarToggleH5"
            case .header6:
                return "formatToolbarToggleH6"
            case .code:
                return "formatToolbarCode"
            default:
                return ""
        }
    }
    
    var accessibilityLabel: String {
        switch self {
            case .media:
                return NSLocalizedString("Insert media", comment: "Accessibility label for insert media button on formatting toolbar.")
            case .p:
                return NSLocalizedString("Select paragraph style", comment: "Accessibility label for selecting paragraph style button on formatting toolbar.")
            case .bold:
                return NSLocalizedString("Bold", comment: "Accessibility label for bold button on formatting toolbar.")
            case .italic:
                return NSLocalizedString("Italic", comment: "Accessibility label for italic button on formatting toolbar.")
            case .underline:
                return NSLocalizedString("Underline", comment: "Accessibility label for underline button on formatting toolbar.")
            case .strikethrough:
                return NSLocalizedString("Strike Through", comment: "Accessibility label for strikethrough button on formatting toolbar.")
            case .blockquote:
                return NSLocalizedString("Block Quote", comment: "Accessibility label for block quote button on formatting toolbar.")
            case .orderedlist:
                return NSLocalizedString("Ordered List", comment: "Accessibility label for Ordered list button on formatting toolbar.")
            case .unorderedlist:
                return NSLocalizedString("Unordered List", comment: "Accessibility label for unordered list button on formatting toolbar.")
            case .link:
                return NSLocalizedString("Insert Link", comment: "Accessibility label for insert link button on formatting toolbar.")
            case .horizontalruler:
                return NSLocalizedString("Insert Horizontal Ruler", comment: "Accessibility label for insert horizontal ruler button on formatting toolbar.")
            case .sourcecode:
                return NSLocalizedString("HTML", comment:"Accessibility label for HTML button on formatting toolbar.")
            case .more:
                return NSLocalizedString("More", comment:"Accessibility label for the More button on formatting toolbar.")
            case .header1:
                return NSLocalizedString("Heading 1", comment: "Accessibility label for selecting h1 paragraph style button on the formatting toolbar.")
            case .header2:
                return NSLocalizedString("Heading 2", comment: "Accessibility label for selecting h2 paragraph style button on the formatting toolbar.")
            case .header3:
                return NSLocalizedString("Heading 3", comment: "Accessibility label for selecting h3 paragraph style button on the formatting toolbar.")
            case .header4:
                return NSLocalizedString("Heading 4", comment: "Accessibility label for selecting h4 paragraph style button on the formatting toolbar.")
            case .header5:
                return NSLocalizedString("Heading 5", comment: "Accessibility label for selecting h5 paragraph style button on the formatting toolbar.")
            case .header6:
                return NSLocalizedString("Heading 6", comment: "Accessibility label for selecting h6 paragraph style button on the formatting toolbar.")
            case .code:
                return NSLocalizedString("Code", comment: "Accessibility label for selecting code style button on the formatting toolbar.")
            default:
                return ""
        }
    }
}

private extension Header.HeaderType {
    var formattingIdentifier: FormattingIdentifier {
        switch self {
            case .none: return FormattingIdentifier.p
            case .h1:   return FormattingIdentifier.header1
            case .h2:   return FormattingIdentifier.header2
            case .h3:   return FormattingIdentifier.header3
            case .h4:   return FormattingIdentifier.header4
            case .h5:   return FormattingIdentifier.header5
            case .h6:   return FormattingIdentifier.header6
        }
    }
    
    var description: String {
        switch self {
            case .none: return NSLocalizedString("Default", comment: "Description of the default paragraph formatting style in the editor.")
            case .h1: return "Heading 1"
            case .h2: return "Heading 2"
            case .h3: return "Heading 3"
            case .h4: return "Heading 4"
            case .h5: return "Heading 5"
            case .h6: return "Heading 6"
        }
    }
    
    var iconImage: UIImage? {
        return formattingIdentifier.iconImage
    }
}

private extension TextList.Style {
    var formattingIdentifier: FormattingIdentifier {
        switch self {
            case .ordered:   return FormattingIdentifier.orderedlist
            case .unordered: return FormattingIdentifier.unorderedlist
        }
    }
    
    var description: String {
        switch self {
            case .ordered: return "Ordered List"
            case .unordered: return "Unordered List"
        }
    }
    
    var iconImage: UIImage? {
        return formattingIdentifier.iconImage
    }
}

fileprivate func convertFromUIImagePickerControllerInfoKeyDictionary(_ input: [UIImagePickerController.InfoKey: Any]) -> [String: Any] {
    return Dictionary(uniqueKeysWithValues: input.map { (key, value) in (key.rawValue, value) })
}

fileprivate func convertFromUIImagePickerControllerInfoKey(_ input: UIImagePickerController.InfoKey) -> String {
    return input.rawValue
}
