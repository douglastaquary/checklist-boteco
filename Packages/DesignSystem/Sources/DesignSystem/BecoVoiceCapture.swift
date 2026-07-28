import Foundation
import AVFoundation
import Speech
import Combine
import CoreGraphics
import SwiftUI

public enum BecoVoicePhase: Equatable {
  case idle
  case recording
  case transcribing
}

public enum BecoVoiceError: LocalizedError {
  case microphoneDenied
  case speechDenied
  case speechUnavailable

  public var errorDescription: String? {
    switch self {
    case .microphoneDenied:
      return "Permita o microfone para entrada por voz."
    case .speechDenied:
      return "Permita o reconhecimento de fala para dictar."
    case .speechUnavailable:
      return "Reconhecimento de fala indisponível neste aparelho."
    }
  }
}

/// On-device mic + Speech capture with recording / transcribing phases.
public final class BecoVoiceCaptureController: ObservableObject {
  @Published public private(set) var phase: BecoVoicePhase = .idle
  @Published public private(set) var transcript = ""
  @Published public private(set) var audioLevel: CGFloat = 0
  @Published public private(set) var errorMessage: String?
  /// Set when transcription finishes; host should copy into composer text and clear.
  @Published public var readyText: String?

  private let audioEngine = AVAudioEngine()
  private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
  private var recognitionTask: SFSpeechRecognitionTask?
  private var speechRecognizer: SFSpeechRecognizer?
  private var isStarting = false
  private var transcribeTimeoutWork: DispatchWorkItem?
  private var didFinishTranscribing = false

  public init() {}

  public var isActive: Bool {
    phase == .recording || phase == .transcribing
  }

  public func start() {
    DispatchQueue.main.async {
      guard self.phase == .idle, !self.isStarting else { return }
      self.isStarting = true
      self.errorMessage = nil
      self.transcript = ""
      self.readyText = nil
      self.audioLevel = 0
      self.didFinishTranscribing = false

      self.requestPermissions { [weak self] result in
        guard let self else { return }
        DispatchQueue.main.async {
          switch result {
          case .failure(let error):
            self.isStarting = false
            self.phase = .idle
            self.errorMessage = error.localizedDescription
          case .success:
            do {
              try self.beginEngine()
              self.phase = .recording
              self.isStarting = false
            } catch {
              self.teardownEngine(cancelTask: true)
              self.isStarting = false
              self.phase = .idle
              self.errorMessage = error.localizedDescription
            }
          }
        }
      }
    }
  }

  /// Stop recording and wait for final transcription.
  public func stop() {
    DispatchQueue.main.async {
      guard self.phase == .recording else { return }
      self.phase = .transcribing
      self.didFinishTranscribing = false
      self.recognitionRequest?.endAudio()
      self.stopAudioEngineKeepingTask()
      self.scheduleTranscribeTimeout()
    }
  }

  /// Same as stop while recording (review happens in composer after transcript).
  public func sendWhileRecording() {
    stop()
  }

  public func cancel() {
    DispatchQueue.main.async {
      self.cancelTranscribeTimeout()
      self.teardownEngine(cancelTask: true)
      self.transcript = ""
      self.readyText = nil
      self.audioLevel = 0
      self.didFinishTranscribing = true
      self.phase = .idle
    }
  }

  public func consumeReadyText() -> String? {
    let value = readyText
    readyText = nil
    return value
  }

  private func requestPermissions(completion: @escaping (Result<Void, Error>) -> Void) {
    AVAudioSession.sharedInstance().requestRecordPermission { granted in
      guard granted else {
        completion(.failure(BecoVoiceError.microphoneDenied))
        return
      }
      SFSpeechRecognizer.requestAuthorization { status in
        guard status == .authorized else {
          completion(.failure(BecoVoiceError.speechDenied))
          return
        }
        completion(.success(()))
      }
    }
  }

  private func beginEngine() throws {
    let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "pt-BR"))
    speechRecognizer = recognizer
    guard let recognizer, recognizer.isAvailable else {
      throw BecoVoiceError.speechUnavailable
    }

    let session = AVAudioSession.sharedInstance()
    try session.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
    try session.setActive(true, options: .notifyOthersOnDeactivation)

    let request = SFSpeechAudioBufferRecognitionRequest()
    request.shouldReportPartialResults = true
    recognitionRequest = request

    let inputNode = audioEngine.inputNode
    let format = inputNode.outputFormat(forBus: 0)
    inputNode.removeTap(onBus: 0)
    inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
      request.append(buffer)
      self?.publishLevel(from: buffer)
    }

    recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
      guard let self else { return }
      if let result {
        let text = result.bestTranscription.formattedString
        DispatchQueue.main.async {
          self.transcript = text
          if result.isFinal {
            self.completeTranscription(with: text)
          }
        }
      }
      if error != nil {
        DispatchQueue.main.async {
          if self.phase == .transcribing {
            self.completeTranscription(with: self.transcript)
          }
        }
      }
    }

    audioEngine.prepare()
    try audioEngine.start()
  }

  private func publishLevel(from buffer: AVAudioPCMBuffer) {
    guard let channel = buffer.floatChannelData?[0] else { return }
    let frameLength = Int(buffer.frameLength)
    guard frameLength > 0 else { return }
    var sum: Float = 0
    for i in 0..<frameLength {
      let sample = channel[i]
      sum += sample * sample
    }
    let rms = sqrt(sum / Float(frameLength))
    let normalized = min(CGFloat(rms) * 8, 1)
    DispatchQueue.main.async {
      self.audioLevel = normalized
    }
  }

  private func scheduleTranscribeTimeout() {
    cancelTranscribeTimeout()
    let work = DispatchWorkItem { [weak self] in
      guard let self else { return }
      if self.phase == .transcribing {
        self.completeTranscription(with: self.transcript)
      }
    }
    transcribeTimeoutWork = work
    DispatchQueue.main.asyncAfter(deadline: .now() + 2.5, execute: work)
  }

  private func cancelTranscribeTimeout() {
    transcribeTimeoutWork?.cancel()
    transcribeTimeoutWork = nil
  }

  private func completeTranscription(with text: String) {
    guard !didFinishTranscribing else { return }
    didFinishTranscribing = true
    cancelTranscribeTimeout()
    teardownEngine(cancelTask: true)
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    transcript = trimmed
    readyText = trimmed.isEmpty ? nil : trimmed
    audioLevel = 0
    phase = .idle
  }

  private func stopAudioEngineKeepingTask() {
    if audioEngine.isRunning {
      audioEngine.stop()
    }
    audioEngine.inputNode.removeTap(onBus: 0)
  }

  private func teardownEngine(cancelTask: Bool) {
    if cancelTask {
      recognitionTask?.cancel()
    }
    recognitionTask = nil
    recognitionRequest = nil
    if audioEngine.isRunning {
      audioEngine.stop()
    }
    audioEngine.inputNode.removeTap(onBus: 0)
    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
  }
}

// MARK: - Feedback bar (recording / transcribing)

public struct BecoVoiceFeedbackBar: View {
  @ObservedObject private var controller: BecoVoiceCaptureController
  private let onCancel: (() -> Void)?

  private let sendBlue = Color(red: 10 / 255, green: 132 / 255, blue: 255 / 255)

  public init(controller: BecoVoiceCaptureController, onCancel: (() -> Void)? = nil) {
    self.controller = controller
    self.onCancel = onCancel
  }

  public var body: some View {
    Group {
      switch controller.phase {
      case .recording:
        recordingBar
      case .transcribing:
        transcribingBar
      case .idle:
        EmptyView()
      }
    }
  }

  private var recordingBar: some View {
    HStack(spacing: 12) {
      circleButton(systemName: "xmark", accessibility: "Cancelar gravação") {
        controller.cancel()
        onCancel?()
      }

      BecoVoiceDotWaveform(level: controller.audioLevel)
        .frame(maxWidth: .infinity)
        .frame(height: 22)

      circleButton(systemName: "stop.fill", accessibility: "Parar gravação") {
        controller.stop()
      }

      sendButton(enabled: true) {
        controller.sendWhileRecording()
      }
    }
    .padding(.horizontal, 12)
    .padding(.vertical, 10)
    .background(capsuleBackground)
  }

  private var transcribingBar: some View {
    HStack(spacing: 12) {
      circleButton(systemName: "xmark", accessibility: "Cancelar transcrição") {
        controller.cancel()
        onCancel?()
      }

      HStack(spacing: 8) {
        Text("Transcrevendo")
          .font(.subheadline.weight(.medium))
          .foregroundStyle(Color.white.opacity(0.72))
        ProgressView()
          .progressViewStyle(.circular)
          .tint(Color.white.opacity(0.72))
          .scaleEffect(0.85)
      }
      .frame(maxWidth: .infinity)

      sendButton(enabled: false) {}
    }
    .padding(.horizontal, 12)
    .padding(.vertical, 10)
    .background(capsuleBackground)
  }

  private var capsuleBackground: some View {
    Capsule(style: .continuous)
      .fill(Color(white: 0.16))
      .shadow(color: .black.opacity(0.28), radius: 14, y: 6)
  }

  private func circleButton(systemName: String, accessibility: String, action: @escaping () -> Void) -> some View {
    Button(action: action) {
      Image(systemName: systemName)
        .font(.system(size: 14, weight: .semibold))
        .foregroundStyle(.white)
        .frame(width: 36, height: 36)
        .background(Circle().fill(Color.white.opacity(0.12)))
    }
    .buttonStyle(.plain)
    .accessibilityLabel(accessibility)
  }

  private func sendButton(enabled: Bool, action: @escaping () -> Void) -> some View {
    Button(action: action) {
      Image(systemName: "arrow.up")
        .font(.system(size: 15, weight: .bold))
        .foregroundStyle(.white)
        .frame(width: 36, height: 36)
        .background(Circle().fill(enabled ? sendBlue : sendBlue.opacity(0.35)))
    }
    .buttonStyle(.plain)
    .disabled(!enabled)
    .accessibilityLabel("Enviar áudio")
  }
}

private struct BecoVoiceDotWaveform: View {
  let level: CGFloat

  var body: some View {
    HStack(spacing: 4) {
      ForEach(0..<22, id: \.self) { index in
        Circle()
          .fill(Color.white.opacity(dotOpacity(for: index)))
          .frame(width: 4, height: 4)
          .scaleEffect(dotScale(for: index))
      }
    }
  }

  private func dotOpacity(for index: Int) -> Double {
    let wave = abs(sin(Double(index) * 0.45 + Double(level) * 5))
    return 0.25 + wave * (0.35 + Double(level) * 0.4)
  }

  private func dotScale(for index: Int) -> CGFloat {
    let wave = abs(sin(Double(index) * 0.4 + Double(level) * 4.5))
    return 0.75 + CGFloat(wave) * (0.35 + level * 0.55)
  }
}
