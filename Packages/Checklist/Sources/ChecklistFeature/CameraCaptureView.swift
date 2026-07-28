import SwiftUI
import UIKit

struct CameraCaptureView: UIViewControllerRepresentable {
  let onCapture: (String?) -> Void

  func makeUIViewController(context: Context) -> UIImagePickerController {
    let picker = UIImagePickerController()
    if UIImagePickerController.isSourceTypeAvailable(.camera) {
      picker.sourceType = .camera
    } else if UIImagePickerController.isSourceTypeAvailable(.photoLibrary) {
      picker.sourceType = .photoLibrary
    }
    picker.delegate = context.coordinator
    return picker
  }

  func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

  func makeCoordinator() -> Coordinator {
    Coordinator(onCapture: onCapture)
  }

  final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    let onCapture: (String?) -> Void

    init(onCapture: @escaping (String?) -> Void) {
      self.onCapture = onCapture
    }

    func imagePickerController(
      _ picker: UIImagePickerController,
      didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
      picker.dismiss(animated: true)
      guard let image = info[.originalImage] as? UIImage,
            let data = image.jpegData(compressionQuality: 0.85)
      else {
        onCapture(nil)
        return
      }
      let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("completion-\(UUID().uuidString).jpg")
      try? data.write(to: url)
      onCapture(url.path)
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
      picker.dismiss(animated: true)
      onCapture(nil)
    }
  }
}
