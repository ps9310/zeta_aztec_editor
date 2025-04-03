import Foundation
import UIKit
import Aztec
import AVFoundation

class MediaInserter {
    
    /// A callback to upload a file.
    /// - Parameters:
    ///   - fileURL: The local file URL (e.g. image or video file).
    ///   - completion: When the upload finishes, returns a Result containing an optional String URL.
    ///                 If the returned string is nil or empty, the media will be removed.
    var uploadCallback: ((URL, @escaping (Result<String?, PigeonError>) -> Void) -> Void)?
    
    /// Hardcoded editor token for demonstration.
    var editorToken: String = "myEditorToken"
    
    fileprivate var mediaErrorMode = false
    
    struct MediaProgressKey {
        static let mediaID = ProgressUserInfoKey("mediaID")
        static let videoURL = ProgressUserInfoKey("videoURL")
    }
    
    let richTextView: TextView
    var attachmentTextAttributes: [NSAttributedString.Key: Any]
    
    init(textView: TextView, attachmentTextAttributes: [NSAttributedString.Key: Any]) {
        self.richTextView = textView
        self.attachmentTextAttributes = attachmentTextAttributes
    }
    
    func insertImage(_ imageURL: URL, atRange range: NSRange) {
        if let uploadCallback = self.uploadCallback {
            // Load image from URL for placeholder
            guard let imageData = try? Data(contentsOf: imageURL),
                  let image = UIImage(data: imageData) else {
                print("Failed to load image from URL: \(imageURL)")
                return
            }
            
            let attachment = richTextView.replaceWithImage(at: range, sourceURL: imageURL, placeHolderImage: image)
            attachment.size = .full
            attachment.alignment = ImageAttachment.Alignment.none
            
            if let attachmentRange = richTextView.textStorage.ranges(forAttachment: attachment).first {
                richTextView.setLink(imageURL, inRange: attachmentRange)
            }
            
            showUploadAlert(
                for: imageURL,
                attachment: attachment,
                atRange: range,
                uploadCallback: uploadCallback
            )
        }
    }
    
    func insertVideo(_ videoURL: URL, atRange range: NSRange) {
        if let uploadCallback = self.uploadCallback {
            let asset = AVURLAsset(url: videoURL, options: nil)
            let imgGenerator = AVAssetImageGenerator(asset: asset)
            imgGenerator.appliesPreferredTrackTransform = true
            
            guard let cgImage = try? imgGenerator.copyCGImage(at: CMTimeMake(value: 0, timescale: 1), actualTime: nil) else {
                return
            }
            
            let posterImage = UIImage(cgImage: cgImage)
            let posterURL = posterImage.saveToTemporaryFile()
            let attachment = richTextView.replaceWithVideo(
                at: range,
                sourceURL: URL(string:"placeholder://")!,
                posterURL: posterURL,
                placeHolderImage: posterImage
            )
            
            showUploadAlert(for: videoURL, attachment: attachment, atRange: range, uploadCallback: uploadCallback)
        }
    }
    
    private func showUploadAlert(
        for fileURL: URL,
        attachment: MediaAttachment,
        atRange range: NSRange,
        uploadCallback: @escaping (URL, @escaping (Result<String?, PigeonError>) -> Void) -> Void
    ) {
        
        let alert = UIAlertController(title: nil, message: "Uploading...\n\n", preferredStyle: .alert)
        
        let indicator = UIActivityIndicatorView(style: .medium)
        indicator.translatesAutoresizingMaskIntoConstraints = false
        alert.view.addSubview(indicator)
        NSLayoutConstraint.activate([
            indicator.centerXAnchor.constraint(equalTo: alert.view.centerXAnchor),
            indicator.bottomAnchor.constraint(equalTo: alert.view.bottomAnchor, constant: -20)
        ])
        
        indicator.startAnimating()
        
        // Present the uploading alert.
        if let topVC = topViewController() {
            topVC.present(alert, animated: true, completion: nil)
        }
        
        // Call the provided upload callback.
        uploadCallback(fileURL) { [weak self] result in
            DispatchQueue.main.async {
                alert.dismiss(animated: true) {
                    switch result {
                        case .success(let uploadedURLString):
                            // Remove progress.
                            attachment.progress = nil
                            // Check for a valid URL.
                            if let urlStr = uploadedURLString, urlStr.starts(with: "http"), let newURL = URL(string: urlStr) {
                                // Update the anchor link.
                                self?.richTextView.setLink(newURL, inRange: range)
                                
                                // Update the source URL for images or videos.
                                if let imageAttachment = attachment as? ImageAttachment {
                                    imageAttachment.updateURL(newURL, refreshAsset: true)
                                } else if let videoAttachment = attachment as? VideoAttachment {
                                    videoAttachment.updateURL(newURL, refreshAsset: true)
                                }
                            } else {
                                self?.handleUploadFailure(withMessage: uploadedURLString, for: attachment)
                            }
                        case .failure(_):
                            self?.handleUploadFailure(for: attachment)
                    }
                    
                    self?.richTextView.refresh(attachment, overlayUpdateOnly: true)
                    self?.richTextView.becomeFirstResponder()
                    self?.richTextView.selectedRange = NSRange.init(location: range.location + 1, length: 0)
                }
            }
        }
    }
    
    /// Handles upload failures by showing an error alert and removing the attachment from the editor.
    private func handleUploadFailure(withMessage message: String? = nil, for attachment: MediaAttachment) {
        let providedMessage = message ?? ""
        let errorMessage = providedMessage.isEmpty ? "Upload failed. Please try again." : providedMessage
        
        if let topVC = topViewController() {
            let errorAlert = UIAlertController(title: "Upload Failed", message: errorMessage, preferredStyle: .alert)
            errorAlert.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))
            topVC.present(errorAlert, animated: true, completion: nil)
        }
        
        if let range = richTextView.textStorage.ranges(forAttachment: attachment).first {
            richTextView.textStorage.replaceCharacters(in: range, with: "")
        }
    }
    
    // Helper: Finds the top view controller from the key window.
    private func topViewController(base: UIViewController? = UIApplication.shared.connectedScenes
        .compactMap({ $0 as? UIWindowScene })
        .flatMap({ $0.windows })
        .first(where: { $0.isKeyWindow })?.rootViewController) -> UIViewController? {
            
            if let nav = base as? UINavigationController {
                return topViewController(base: nav.visibleViewController)
            }
            if let tab = base as? UITabBarController, let selected = tab.selectedViewController {
                return topViewController(base: selected)
            }
            if let presented = base?.presentedViewController {
                return topViewController(base: presented)
            }
            return base
        }
}
