import Foundation
import UIKit

extension UIImage {

    func saveToTemporaryFile() -> URL {
        let fileName = "\(ProcessInfo.processInfo.globallyUniqueString)_file.jpg"

        guard let data = self.jpegData(compressionQuality: 0.9) else {
            fatalError("Could not conert image to JPEG.")
        }

        let fileURL = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent(fileName)

        guard (try? data.write(to: fileURL, options: [.atomic])) != nil else {
            fatalError("Could not write the image to disk.")
        }

        return fileURL
    }
    
    /// Scales the image to the specified width while maintaining its aspect ratio.
    func scaledToFitWidth(_ targetWidth: CGFloat) -> UIImage {
        let scaleFactor = targetWidth / self.size.width
        let newHeight = self.size.height * scaleFactor
        let newSize = CGSize(width: targetWidth, height: newHeight)
        
        UIGraphicsBeginImageContextWithOptions(newSize, false, 0.0)
        self.draw(in: CGRect(origin: .zero, size: newSize))
        let scaledImage = UIGraphicsGetImageFromCurrentImageContext() ?? self
        UIGraphicsEndImageContext()
        return scaledImage
    }
    
    /// Returns a new image that is placed in a square canvas with the given padding and background color.
    func imageWithPaddingAndSquare(squareSize: CGFloat, padding: CGFloat, backgroundColor: UIColor) -> UIImage {
        // Create a square canvas.
        let rect = CGRect(origin: .zero, size: CGSize(width: squareSize, height: squareSize))
        UIGraphicsBeginImageContextWithOptions(rect.size, false, 0.0)
        // Fill the canvas with the background color.
        backgroundColor.setFill()
        UIRectFill(rect)
        
        // Determine the maximum drawing area available after applying padding.
        let availableSize = squareSize - 2 * padding
        // Scale the image to fit within the available area while maintaining its aspect ratio.
        let scaleFactor = min(availableSize / self.size.width, availableSize / self.size.height)
        let scaledSize = CGSize(width: self.size.width * scaleFactor,
                                height: self.size.height * scaleFactor)
        // Center the image in the square canvas.
        let imageRect = CGRect(x: (squareSize - scaledSize.width) / 2,
                               y: (squareSize - scaledSize.height) / 2,
                               width: scaledSize.width,
                               height: scaledSize.height)
        self.draw(in: imageRect)
        
        let paddedImage = UIGraphicsGetImageFromCurrentImageContext() ?? self
        UIGraphicsEndImageContext()
        return paddedImage
    }
}
