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
    
    func insertImage(_ image: UIImage) {
        let fileURL = image.saveToTemporaryFile()
        
        let attachment = richTextView.replaceWithImage(at: richTextView.selectedRange, sourceURL: fileURL, placeHolderImage: image)
        attachment.size = .full
        attachment.alignment = ImageAttachment.Alignment.none
        if let attachmentRange = richTextView.textStorage.ranges(forAttachment: attachment).first {
            richTextView.setLink(fileURL, inRange: attachmentRange)
        }
        
        if let uploadCallback = self.uploadCallback {
            showUploadAlert(for: fileURL, attachment: attachment, uploadCallback: uploadCallback)
        } else {
            // Fallback: simulate progress with a timer.
            let imageID = attachment.identifier
            let progress = Progress(parent: nil, userInfo: [MediaProgressKey.mediaID: imageID])
            progress.totalUnitCount = 100
            Timer.scheduledTimer(timeInterval: 0.1, target: self, selector: #selector(MediaInserter.timerFireMethod(_:)), userInfo: progress, repeats: true)
        }
    }
    
    func insertVideo(_ videoURL: URL) {
        let asset = AVURLAsset(url: videoURL, options: nil)
        let imgGenerator = AVAssetImageGenerator(asset: asset)
        imgGenerator.appliesPreferredTrackTransform = true
        guard let cgImage = try? imgGenerator.copyCGImage(at: CMTimeMake(value: 0, timescale: 1), actualTime: nil) else {
            return
        }
        let posterImage = UIImage(cgImage: cgImage)
        let posterURL = posterImage.saveToTemporaryFile()
        let attachment = richTextView.replaceWithVideo(at: richTextView.selectedRange, sourceURL: URL(string:"placeholder://")!, posterURL: posterURL, placeHolderImage: posterImage)
        
        if let uploadCallback = self.uploadCallback {
            showUploadAlert(for: videoURL, attachment: attachment, uploadCallback: uploadCallback)
        } else {
            let mediaID = attachment.identifier
            let progress = Progress(parent: nil, userInfo: [MediaProgressKey.mediaID: mediaID, MediaProgressKey.videoURL: videoURL])
            progress.totalUnitCount = 100
            Timer.scheduledTimer(timeInterval: 0.1, target: self, selector: #selector(MediaInserter.timerFireMethod(_:)), userInfo: progress, repeats: true)
        }
    }
    
    private func showUploadAlert(for fileURL: URL,
                                 attachment: MediaAttachment,
                                 uploadCallback: @escaping (URL, @escaping (Result<String?, PigeonError>) -> Void) -> Void) {
        
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
                                if let attachmentRange = self?.richTextView.textStorage.ranges(forAttachment: attachment).first {
                                    self?.richTextView.setLink(newURL, inRange: attachmentRange)
                                }
                                
                                // Update the source URL for images or videos.
                                if let imageAttachment = attachment as? ImageAttachment {
                                    imageAttachment.updateURL(newURL)
                                } else if let videoAttachment = attachment as? VideoAttachment {
                                    videoAttachment.updateURL(newURL, refreshAsset: false)
                                }
                            } else {
                                self?.handleUploadFailure(withMessage: uploadedURLString, for: attachment)
                            }
                        case .failure(_):
                            self?.handleUploadFailure(for: attachment)
                    }
                    self?.richTextView.refresh(attachment, overlayUpdateOnly: true)
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
    
    @objc func timerFireMethod(_ timer: Timer) {
        guard let progress = timer.userInfo as? Progress,
              let imageId = progress.userInfo[MediaProgressKey.mediaID] as? String,
              let attachment = richTextView.attachment(withId: imageId)
        else {
            timer.invalidate()
            return
        }
        progress.completedUnitCount += 1
        
        attachment.progress = progress.fractionCompleted
        
        if mediaErrorMode && progress.fractionCompleted >= 0.25 {
            timer.invalidate()
            let message = NSAttributedString(string: "Upload failed!", attributes: attachmentTextAttributes)
            attachment.message = message
            attachment.overlayImage = UIImage.systemImage("arrow.clockwise")
        }
        if progress.fractionCompleted >= 1 {
            timer.invalidate()
            attachment.progress = nil
            if let videoAttachment = attachment as? VideoAttachment,
               let videoURL = progress.userInfo[MediaProgressKey.videoURL] as? URL {
                videoAttachment.updateURL(videoURL, refreshAsset: false)
            }
        }
        richTextView.refresh(attachment, overlayUpdateOnly: true)
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
