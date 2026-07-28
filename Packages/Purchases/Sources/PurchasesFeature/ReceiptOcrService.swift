#if os(iOS)
import UIKit
import Vision
import PDFKit
import UniformTypeIdentifiers

@MainActor
public enum ReceiptOcrService {
  public static func recognize(image: UIImage) async throws -> String {
    guard let cgImage = image.cgImage else {
      throw NSError(domain: "ReceiptOcr", code: 1, userInfo: [NSLocalizedDescriptionKey: "Imagem inválida"])
    }
    return try await withCheckedThrowingContinuation { continuation in
      let request = VNRecognizeTextRequest { request, error in
        if let error {
          continuation.resume(throwing: error)
          return
        }
        let text = (request.results as? [VNRecognizedTextObservation] ?? [])
          .compactMap { $0.topCandidates(1).first?.string }
          .joined(separator: "\n")
        continuation.resume(returning: text)
      }
      request.recognitionLevel = .accurate
      request.usesLanguageCorrection = true
      request.recognitionLanguages = ["pt-BR", "pt", "en"]
      let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
      do {
        try handler.perform([request])
      } catch {
        continuation.resume(throwing: error)
      }
    }
  }

  public static func loadImage(from url: URL) throws -> UIImage {
    if url.pathExtension.lowercased() == "pdf" || UTType(filenameExtension: url.pathExtension)?.conforms(to: .pdf) == true {
      guard let document = PDFDocument(url: url),
            let page = document.page(at: 0)
      else {
        throw NSError(domain: "ReceiptOcr", code: 2, userInfo: [NSLocalizedDescriptionKey: "PDF inválido"])
      }
      let rect = page.bounds(for: .mediaBox)
      let renderer = UIGraphicsImageRenderer(size: CGSize(width: rect.width * 2, height: rect.height * 2))
      return renderer.image { ctx in
        UIColor.white.setFill()
        ctx.fill(CGRect(origin: .zero, size: renderer.format.bounds.size))
        ctx.cgContext.translateBy(x: 0, y: renderer.format.bounds.height)
        ctx.cgContext.scaleBy(x: 2, y: -2)
        page.draw(with: .mediaBox, to: ctx.cgContext)
      }
    }
    let data = try Data(contentsOf: url)
    guard let image = UIImage(data: data) else {
      throw NSError(domain: "ReceiptOcr", code: 3, userInfo: [NSLocalizedDescriptionKey: "Não foi possível ler a imagem"])
    }
    return image
  }
}
#endif
