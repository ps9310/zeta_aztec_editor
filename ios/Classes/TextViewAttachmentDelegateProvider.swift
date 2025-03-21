import AVFoundation
import AVKit
import Aztec
import Foundation
import UIKit

class TextViewAttachmentDelegateProvider: NSObject, TextViewAttachmentDelegate {
    fileprivate var currentSelectedAttachment: MediaAttachment?
    let baseController: UIViewController
    let attachmentTextAttributes: [NSAttributedString.Key: Any]

    /// Optional headers that will be injected into media download requests.
    let authHeaders: [String: String]?

    /// In-memory cache for downloaded images.
    private let imageCache = NSCache<NSURL, UIImage>()

    init(baseController: UIViewController, attachmentTextAttributes: [NSAttributedString.Key: Any], authHeaders: [String: String]? = nil) {
        self.baseController = baseController
        self.attachmentTextAttributes = attachmentTextAttributes
        self.authHeaders = authHeaders
    }

    func textView(_ textView: TextView, attachment: NSTextAttachment, imageAt url: URL, onSuccess success: @escaping (UIImage) -> Void, onFailure failure: @escaping () -> Void) {
        switch attachment {
        case let videoAttachment as VideoAttachment:
            guard let posterURL = videoAttachment.posterURL else {
                // If no poster URL exists, attempt to generate one using the video URL.
                if let videoURL = videoAttachment.mediaURL {
                    exportPreviewImageForVideo(atURL: videoURL, onCompletion: success, onError: failure)
                } else {
                    exportPreviewImageForVideo(atURL: url, onCompletion: success, onError: failure)
                }
                return
            }
            downloadImage(from: posterURL, success: success, onFailure: failure)
        case let imageAttachment as ImageAttachment:
            if let imageURL = imageAttachment.url {
                downloadImage(from: imageURL, success: success, onFailure: failure)
            } else {
                failure()
            }
        default:
            failure()
        }
    }

    func textView(_ textView: TextView, placeholderFor attachment: NSTextAttachment) -> UIImage {
        return placeholderImage(for: attachment)
    }

    func placeholderImage(for attachment: NSTextAttachment) -> UIImage {
        var placeholderImage: UIImage
        switch attachment {
        case _ as ImageAttachment:
            placeholderImage = UIImage.systemImage("photo")
        case _ as VideoAttachment:
            placeholderImage = UIImage.systemImage("video")
        default:
            placeholderImage = UIImage.systemImage("paperclip")
        }
        
        if #available(iOS 13.0, *) {
            placeholderImage = placeholderImage.withTintColor(
                UIColor(red: 189/255.0, green: 189/255.0, blue: 189/255.0, alpha: 1.0)
            )
        }
        
        // Define square size as the screen's width, and set padding (adjust as needed).
        let squareSize = UIScreen.main.bounds.width
        let padding: CGFloat = squareSize * 0.25
        placeholderImage = placeholderImage.imageWithPaddingAndSquare(
            squareSize: squareSize,
            padding: padding,
            backgroundColor: UIColor(red: 189/255.0, green: 189/255.0, blue: 189/255.0, alpha: 0.3)
        
        )
        
        return placeholderImage
    }

    func textView(_ textView: TextView, urlFor imageAttachment: ImageAttachment) -> URL? {
        guard let image = imageAttachment.image else {
            return nil
        }
        return image.saveToTemporaryFile()
    }

    func textView(_ textView: TextView, deletedAttachment attachment: MediaAttachment) {
        print("Attachment \(attachment.identifier) removed.\n")
    }

    func textView(_ textView: TextView, selected attachment: NSTextAttachment, atPosition position: CGPoint) {
        switch attachment {
        case let attachment as HTMLAttachment:
            displayUnknownHtmlEditor(for: attachment, in: textView)
        case let attachment as MediaAttachment:
            selected(in: textView, textAttachment: attachment, atPosition: position)
        default:
            break
        }
    }

    func textView(_ textView: TextView, deselected attachment: NSTextAttachment, atPosition position: CGPoint) {
        deselected(in: textView, textAttachment: attachment, atPosition: position)
    }

    fileprivate func resetMediaAttachmentOverlay(_ mediaAttachment: MediaAttachment) {
        mediaAttachment.overlayImage = nil
        mediaAttachment.message = nil
    }

    func selected(in textView: TextView, textAttachment attachment: MediaAttachment, atPosition position: CGPoint) {
        displayActions(in: textView, forAttachment: attachment, position: position)
    }

    func deselected(in textView: TextView, textAttachment attachment: NSTextAttachment, atPosition position: CGPoint) {
        currentSelectedAttachment = nil
        if let mediaAttachment = attachment as? MediaAttachment {
            resetMediaAttachmentOverlay(mediaAttachment)
            textView.refresh(mediaAttachment)
        }
    }
}

// MARK: - Media Fetch Methods

private extension TextViewAttachmentDelegateProvider {
    func exportPreviewImageForVideo(atURL url: URL, onCompletion: @escaping (UIImage) -> Void, onError: @escaping () -> Void) {
        DispatchQueue.global(qos: .background).async {
            // Use auth headers if available.
            var assetOptions: [String: Any]?
            if let headers = self.authHeaders, headers.isEmpty == false {
                assetOptions = ["AVURLAssetHTTPHeaderFieldsKey": headers]
            }

            let asset = AVURLAsset(url: url, options: assetOptions)
            guard asset.isExportable else {
                DispatchQueue.main.async {
                    onError()
                }
                return
            }

            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            generator.generateCGImagesAsynchronously(
                forTimes: [NSValue(time: CMTimeMake(value: 2, timescale: 1))],
                completionHandler: { _, cgImage, _, _, _ in
                    guard let cgImage = cgImage else {
                        DispatchQueue.main.async {
                            onError()
                        }
                        return
                    }
                    let image = UIImage(cgImage: cgImage)
                    DispatchQueue.main.async {
                        onCompletion(image)
                    }
                })
        }
    }

    func downloadImage(from url: URL, success: @escaping (UIImage) -> Void, onFailure failure: @escaping () -> Void) {
        // Check the memory cache first.
        if let cachedImage = imageCache.object(forKey: url as NSURL) {
            success(cachedImage)
            return
        }

        // Next, check the file cache.
        if let diskImage = loadImageFromDiskCache(for: url) {
            imageCache.setObject(diskImage, forKey: url as NSURL)
            success(diskImage)
            return
        }

        // Not cached? Download from network.
        var request = URLRequest(url: url)
        // Inject auth headers if available.
        if let headers = authHeaders {
            for (key, value) in headers {
                request.addValue(value, forHTTPHeaderField: key)
            }
        }
        let task = URLSession.shared.dataTask(with: request) { [weak self] data, _, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                guard error == nil, let data = data, let image = UIImage(data: data, scale: UIScreen.main.scale) else {
                    failure()
                    return
                }
                // Cache image in memory.
                self.imageCache.setObject(image, forKey: url as NSURL)
                // Save image to disk.
                self.saveImageToDiskCache(data: data, for: url)
                success(image)
            }
        }
        task.resume()
    }

    /// Returns a file URL in the caches directory based on the given URL.
    private func fileCacheURL(for url: URL) -> URL? {
        guard let cachesDirectory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            return nil
        }
        // Create a safe file name from the URL string.
        let fileName = url.absoluteString.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? UUID().uuidString
        return cachesDirectory.appendingPathComponent(fileName)
    }

    /// Tries to load an image from the file cache if it exists and is not expired.
    private func loadImageFromDiskCache(for url: URL) -> UIImage? {
        guard let fileURL = fileCacheURL(for: url),
              FileManager.default.fileExists(atPath: fileURL.path) else {
            return nil
        }
        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: fileURL.path)
            if let modificationDate = attributes[.modificationDate] as? Date {
                let maxAge: TimeInterval = 7 * 24 * 60 * 60 // 7 days
                if Date().timeIntervalSince(modificationDate) > maxAge {
                    // Cached file is too old; remove it.
                    try FileManager.default.removeItem(at: fileURL)
                    return nil
                }
            }
            let data = try Data(contentsOf: fileURL)
            return UIImage(data: data)
        } catch {
            print("Failed to load cached image from disk: \(error)")
            return nil
        }
    }

    /// Saves downloaded image data to disk for caching.
    private func saveImageToDiskCache(data: Data, for url: URL) {
        guard let fileURL = fileCacheURL(for: url) else { return }
        do {
            try data.write(to: fileURL)
        } catch {
            print("Failed to write image to disk: \(error)")
        }
    }
}

// MARK: - Media Attachments Actions

extension TextViewAttachmentDelegateProvider {
    func displayActions(in textView: TextView, forAttachment attachment: MediaAttachment, position: CGPoint) {
        let mediaID = attachment.identifier
        let title = NSLocalizedString("Media Options", comment: "Title for media options")
        let alertController = UIAlertController(title: title, message: nil, preferredStyle: .actionSheet)
        
        // Dismiss action
        let dismissAction = UIAlertAction(title: NSLocalizedString("Dismiss", comment: "Dismiss options"), style: .cancel) { _ in
            self.resetMediaAttachmentOverlay(attachment)
            textView.refresh(attachment)
        }
        alertController.addAction(dismissAction)
        
        // "Add new line above" action
        let addNewLineAbove = UIAlertAction(title: NSLocalizedString("Add new line above", comment: "Insert a new line above"), style: .default) { [weak self] _ in
            self?.addNewLine(above: true, for: attachment, in: textView)
        }
        alertController.addAction(addNewLineAbove)
        
        // "Add new line below" action
        let addNewLineBelow = UIAlertAction(title: NSLocalizedString("Add new line below", comment: "Insert a new line below"), style: .default) { [weak self] _ in
            self?.addNewLine(above: false, for: attachment, in: textView)
        }
        alertController.addAction(addNewLineBelow)
        
        // "Remove Media" action with confirmation
        let deleteAction = UIAlertAction(title: NSLocalizedString("Remove Media", comment: "Delete media"), style: .destructive) { [weak self] _ in
            guard let self = self else { return }
            let confirmAlert = UIAlertController(title: NSLocalizedString("Confirm Deletion", comment: ""),
                                                 message: NSLocalizedString("Are you sure you want to delete this media?", comment: ""),
                                                 preferredStyle: .alert)
            confirmAlert.addAction(UIAlertAction(title: NSLocalizedString("Delete", comment: ""),
                                                 style: .destructive, handler: { _ in
                textView.remove(attachmentID: mediaID)
            }))
            confirmAlert.addAction(UIAlertAction(title: NSLocalizedString("Cancel", comment: ""),
                                                 style: .cancel, handler: nil))
            self.baseController.present(confirmAlert, animated: true, completion: nil)
        }
        alertController.addAction(deleteAction)
        
        alertController.popoverPresentationController?.sourceView = textView
        alertController.popoverPresentationController?.sourceRect = CGRect(origin: position, size: CGSize(width: 1, height: 1))
        alertController.popoverPresentationController?.permittedArrowDirections = .any
        baseController.present(alertController, animated: true, completion: nil)
    }

    private func addNewLine(above: Bool, for attachment: MediaAttachment, in textView: TextView) {
        // Obtain a mutable copy of the text view's attributed text.
        guard let mutableAttributedText = textView.attributedText.mutableCopy() as? NSMutableAttributedString else { return }
        let fullRange = NSRange(location: 0, length: mutableAttributedText.length)
        var attachmentRange: NSRange?
        
        // Enumerate the text to find the range where this media attachment is located.
        mutableAttributedText.enumerateAttribute(.attachment, in: fullRange, options: []) { (value, range, stop) in
            if let mediaAttachment = value as? MediaAttachment,
               mediaAttachment.identifier == attachment.identifier {
                attachmentRange = range
                stop.pointee = true
            }
        }
        
        // If we found the attachment's range, insert a newline.
        guard let range = attachmentRange else { return }
        let newline = NSAttributedString(string: "\n")
        if above {
            mutableAttributedText.insert(newline, at: range.location)
        } else {
            mutableAttributedText.insert(newline, at: range.location + range.length)
        }
        
        // Update the text view with the new content.
        textView.attributedText = mutableAttributedText
        textView.refresh(attachment)
    }
}

// MARK: - Unknown HTML

private extension TextViewAttachmentDelegateProvider {
    func displayUnknownHtmlEditor(for attachment: HTMLAttachment, in textView: TextView) {
        // Implementation for unknown HTML editor. (Your code here.)
    }

    func displayAsPopover(viewController: UIViewController) {
        viewController.preferredContentSize = baseController.view.frame.size
        let presentationController = viewController.popoverPresentationController
        presentationController?.sourceView = baseController.view
        presentationController?.delegate = self
        baseController.present(viewController, animated: true, completion: nil)
    }
}

// MARK: - UIPopoverPresentationControllerDelegate

extension TextViewAttachmentDelegateProvider: UIPopoverPresentationControllerDelegate {
    func adaptivePresentationStyle(for controller: UIPresentationController, traitCollection: UITraitCollection) -> UIModalPresentationStyle {
        return .none
    }

    func popoverPresentationControllerDidDismissPopover(_ popoverPresentationController: UIPopoverPresentationController) {
    }
}
