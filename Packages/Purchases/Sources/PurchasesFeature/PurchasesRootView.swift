#if os(iOS)
import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import Network
import DesignSystem
import UIKit

public struct PurchasesRootView: View {
  private let purchaseClient: PurchaseClient?
  private let token: String?

  @Environment(\.colorScheme) private var colorScheme
  @State private var session = ReceiptSession()
  @State private var groups: [CategoryGroup] = []
  @State private var isProcessing = false
  @State private var isUploading = false
  @State private var uploadProgress: Double = 0
  @State private var successMessage: String?
  @State private var errorMessage: String?
  @State private var showSourceSheet = false
  @State private var showCamera = false
  @State private var showPhotos = false
  @State private var showFileImporter = false
  @State private var showCsvImporter = false
  @State private var photoItem: PhotosPickerItem?

  public init(purchaseClient: PurchaseClient?, token: String?) {
    self.purchaseClient = purchaseClient
    self.token = token
  }

  private var isDark: Bool { colorScheme == .dark }
  private var foregroundColor: Color { isDark ? .white : .black }
  private var mutedForegroundColor: Color { isDark ? .white.opacity(0.62) : .secondary }

  public var body: some View {
    ZStack {
      PurchasesBackground(isDark: isDark)

      VStack(spacing: 0) {
        PurchasesHeader(
          foregroundColor: foregroundColor,
          mutedForegroundColor: mutedForegroundColor,
          isDark: isDark,
          onScanTap: { showSourceSheet = true },
          onSendCsv: { showCsvImporter = true }
        )
        .padding(.horizontal, 20)
        .padding(.top, 10)

        if isUploading {
          ProgressView(value: uploadProgress)
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }

        if let successMessage {
          Text(successMessage)
            .font(.footnote.weight(.medium))
            .foregroundStyle(.green)
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }
        if let errorMessage {
          Text(errorMessage)
            .font(.footnote)
            .foregroundStyle(.red)
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }

        PurchasesContent(
          isProcessing: isProcessing,
          groups: groups,
          sessionTotalLabel: ReceiptProcessor.formatBrl(session.totalInCents),
          foregroundColor: foregroundColor,
          mutedForegroundColor: mutedForegroundColor,
          isDark: isDark
        )
      }
    }
    .navigationTitle("")
    .safeAreaInset(edge: .bottom) {
      BecoButton(
        isUploading ? "Enviando..." : "Salvar dados",
        isLoading: isUploading
      ) {
        Task { await saveSession() }
      }
      .disabled(session.isEmpty || isUploading)
      .padding(.horizontal, 20)
      .padding(.vertical, BecoTokens.Spacing.sm)
    }
    .confirmationDialog("Adicionar comprovante", isPresented: $showSourceSheet, titleVisibility: .visible) {
      Button("Câmera") { showCamera = true }
      Button("Fotos") { showPhotos = true }
      Button("Arquivos") { showFileImporter = true }
      Button("Cancelar", role: .cancel) {}
    }
    .fullScreenCover(isPresented: $showCamera) {
      ReceiptImagePicker(
        onImage: { image in
          showCamera = false
          Task { await process(image: image) }
        },
        onCancel: { showCamera = false }
      )
      .ignoresSafeArea()
    }
    .photosPicker(isPresented: $showPhotos, selection: $photoItem, matching: .images)
    .onChange(of: photoItem) { item in
      guard let item else { return }
      Task {
        if let data = try? await item.loadTransferable(type: Data.self),
           let image = UIImage(data: data) {
          await process(image: image)
        }
        photoItem = nil
      }
    }
    .fileImporter(
      isPresented: $showFileImporter,
      allowedContentTypes: [.image, .pdf],
      allowsMultipleSelection: false
    ) { result in
      Task { await handlePickedFile(result) }
    }
    .fileImporter(
      isPresented: $showCsvImporter,
      allowedContentTypes: [.commaSeparatedText, .plainText, UTType(filenameExtension: "csv")].compactMap { $0 },
      allowsMultipleSelection: false
    ) { result in
      Task { await handleCsv(result) }
    }
  }

  private func process(image: UIImage) async {
    isProcessing = true
    errorMessage = nil
    successMessage = nil
    defer { isProcessing = false }
    do {
      let text = try await ReceiptOcrService.recognize(image: image)
      let scan = ReceiptProcessor.parseReceipt(text)
      guard !scan.items.isEmpty else {
        errorMessage = "Não foi possível extrair itens do comprovante."
        return
      }
      session = ReceiptProcessor.merge(session: session, scan: scan)
      groups = ReceiptProcessor.buildGroups(session: session)
    } catch {
      errorMessage = error.localizedDescription
    }
  }

  private func handlePickedFile(_ result: Result<[URL], Error>) async {
    do {
      guard let url = try result.get().first else { return }
      let accessed = url.startAccessingSecurityScopedResource()
      defer { if accessed { url.stopAccessingSecurityScopedResource() } }
      let image = try await ReceiptOcrService.loadImage(from: url)
      await process(image: image)
    } catch {
      errorMessage = error.localizedDescription
    }
  }

  private func handleCsv(_ result: Result<[URL], Error>) async {
    guard let client = purchaseClient, let token, !token.isEmpty else {
      errorMessage = "Faça login novamente"
      return
    }
    do {
      guard let url = try result.get().first else { return }
      let accessed = url.startAccessingSecurityScopedResource()
      defer { if accessed { url.stopAccessingSecurityScopedResource() } }
      let csv = try String(contentsOf: url, encoding: .utf8)
      isUploading = true
      uploadProgress = 0.25
      errorMessage = nil
      successMessage = nil
      let preview = try await client.previewImport(token: token, fileName: url.lastPathComponent, csv: csv)
      uploadProgress = 0.65
      let committed = try await client.commitImport(
        token: token,
        importId: preview.id,
        mapping: preview.suggestedMapping,
        preserveColumns: preview.headers
      )
      uploadProgress = 1
      successMessage = "Dados enviados com sucesso (\(committed.importedRows) linhas)."
    } catch {
      errorMessage = AppErrorMapper.toUserMessage(error)
    }
    isUploading = false
    uploadProgress = 0
  }

  private func saveSession() async {
    guard let client = purchaseClient, let token, !token.isEmpty else {
      errorMessage = "Faça login novamente"
      return
    }
    isUploading = true
    uploadProgress = 0.3
    errorMessage = nil
    successMessage = nil
    do {
      let response = try await client.submitReceiptSession(token: token, session: session)
      uploadProgress = 1
      session = ReceiptSession()
      groups = []
      successMessage = "Dados enviados com sucesso (\(response.importedRows) itens)."
    } catch {
      errorMessage = AppErrorMapper.toUserMessage(error)
    }
    isUploading = false
    uploadProgress = 0
  }
}

// MARK: - Subviews (MV)

private struct PurchasesBackground: View {
  let isDark: Bool

  var body: some View {
    ZStack {
      LinearGradient(
        colors: isDark
          ? [Color.black, Color(red: 0.03, green: 0.03, blue: 0.04), Color.black]
          : [Color(.systemBackground), Color(.systemGroupedBackground), Color(.secondarySystemGroupedBackground)],
        startPoint: .top,
        endPoint: .bottom
      )
      Circle()
        .fill((isDark ? Color.white.opacity(0.08) : Color.black.opacity(0.035)))
        .frame(width: 240, height: 240)
        .blur(radius: 70)
        .offset(x: -150, y: -240)
    }
    .ignoresSafeArea()
  }
}

private struct PurchasesHeader: View {
  let foregroundColor: Color
  let mutedForegroundColor: Color
  let isDark: Bool
  let onScanTap: () -> Void
  let onSendCsv: () -> Void

  var body: some View {
    HStack(spacing: 12) {
      Image(systemName: "cart")
        .font(.system(size: 18, weight: .semibold))
        .foregroundStyle(foregroundColor)
        .frame(width: 44, height: 44)
        .background(glassCircle)

      VStack(alignment: .leading, spacing: 2) {
        Text("Compras")
          .font(.headline.weight(.semibold))
          .foregroundStyle(foregroundColor)
        Text("Comprovantes e CSV")
          .font(.caption)
          .foregroundStyle(mutedForegroundColor)
      }

      Spacer(minLength: 8)

      HStack(spacing: BecoTokens.Spacing.sm) {
        Button(action: onScanTap) {
          Image(systemName: "camera")
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(foregroundColor)
            .frame(width: 44, height: 44)
            .background(glassCircle)
        }
        .accessibilityLabel("Escanear comprovante")

        Menu {
          Button("Enviar CSV", action: onSendCsv)
        } label: {
          Image(systemName: "ellipsis")
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(foregroundColor)
            .frame(width: 44, height: 44)
            .background(glassCircle)
        }
        .accessibilityLabel("Mais opções")
      }
    }
  }

  private var glassCircle: some View {
    Circle()
      .fill(.ultraThinMaterial)
      .overlay(Circle().stroke(isDark ? Color.white.opacity(0.14) : Color.black.opacity(0.08)))
  }
}

private struct PurchasesContent: View {
  let isProcessing: Bool
  let groups: [CategoryGroup]
  let sessionTotalLabel: String
  let foregroundColor: Color
  let mutedForegroundColor: Color
  let isDark: Bool

  var body: some View {
    if isProcessing {
      ProgressView("Processando comprovante...")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    } else if groups.isEmpty {
      PurchasesEmptyState(
        foregroundColor: foregroundColor,
        mutedForegroundColor: mutedForegroundColor
      )
    } else {
      ScrollView {
        LazyVStack(spacing: 14, pinnedViews: [.sectionHeaders]) {
          Text("Total da sessão: \(sessionTotalLabel)")
            .font(.headline)
            .foregroundStyle(foregroundColor)
            .frame(maxWidth: .infinity, alignment: .leading)

          ForEach(groups) { group in
            Section {
              ForEach(group.items) { item in
                PurchasesItemRow(
                  item: item,
                  foregroundColor: foregroundColor,
                  mutedForegroundColor: mutedForegroundColor,
                  isDark: isDark
                )
              }
            } header: {
              PurchasesCategoryHeader(
                group: group,
                foregroundColor: foregroundColor,
                isDark: isDark
              )
            }
          }
        }
        .padding(.horizontal, 18)
        .padding(.top, 18)
        .padding(.bottom, 34)
        .tabBarScrollAnchor()
      }
      .tracksTabBarOnScroll()
    }
  }
}

private struct PurchasesEmptyState: View {
  let foregroundColor: Color
  let mutedForegroundColor: Color

  var body: some View {
    VStack(spacing: 14) {
      Image(systemName: "doc.text.viewfinder")
        .font(.system(size: 34, weight: .semibold))
        .foregroundStyle(mutedForegroundColor)
      Text("Nenhuma sessão de comprovantes de compras iniciada")
        .font(.title3.weight(.semibold))
        .multilineTextAlignment(.center)
        .foregroundStyle(foregroundColor)
      Text("Toque no ícone de câmera para escanear ou use os três pontos para enviar um CSV.")
        .font(.body)
        .multilineTextAlignment(.center)
        .foregroundStyle(mutedForegroundColor)
    }
    .padding(28)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
  }
}

private struct PurchasesCategoryHeader: View {
  let group: CategoryGroup
  let foregroundColor: Color
  let isDark: Bool

  var body: some View {
    HStack {
      Text(group.category)
        .font(.subheadline.weight(.semibold))
      if group.isTopSpend {
        Text("maior gasto")
          .font(.caption2.weight(.medium))
          .padding(.horizontal, 8)
          .padding(.vertical, 4)
          .background(Capsule().fill(foregroundColor.opacity(0.08)))
      }
      Spacer()
      Text(ReceiptProcessor.formatBrl(group.subtotalInCents))
        .font(.caption.weight(.semibold))
    }
    .foregroundStyle(foregroundColor)
    .padding(.horizontal, 14)
    .padding(.vertical, 10)
    .background(glassRoundedRectangle(radius: 16, isDark: isDark))
  }
}

private struct PurchasesItemRow: View {
  let item: ReceiptLineItem
  let foregroundColor: Color
  let mutedForegroundColor: Color
  let isDark: Bool

  var body: some View {
    HStack(alignment: .top) {
      VStack(alignment: .leading, spacing: 4) {
        Text(item.description)
          .font(.body.weight(.medium))
          .foregroundStyle(foregroundColor)
        Text("\(formatQty(item.quantity)) × \(ReceiptProcessor.formatBrl(item.unitPriceInCents))")
          .font(.caption)
          .foregroundStyle(mutedForegroundColor)
      }
      Spacer()
      Text(ReceiptProcessor.formatBrl(item.totalInCents))
        .font(.subheadline.weight(.medium))
        .foregroundStyle(foregroundColor)
    }
    .padding(14)
    .background(glassRoundedRectangle(radius: 18, isDark: isDark))
  }

  private func formatQty(_ value: Double) -> String {
    value.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(value)) : String(format: "%.2f", value)
  }
}

private func glassRoundedRectangle(radius: CGFloat, isDark: Bool) -> some View {
  RoundedRectangle(cornerRadius: radius, style: .continuous)
    .fill(.ultraThinMaterial)
    .overlay(
      RoundedRectangle(cornerRadius: radius, style: .continuous)
        .stroke(isDark ? Color.white.opacity(0.14) : Color.black.opacity(0.08))
    )
}
#endif
