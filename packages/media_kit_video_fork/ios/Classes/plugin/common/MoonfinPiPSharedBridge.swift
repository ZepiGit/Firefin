import Foundation
import CoreVideo

final class MoonfinPiPSharedBridge {
  static let shared = MoonfinPiPSharedBridge()

  private let channel = "org.moonfin.ios/pip_shared_frames"
  private let queue = DispatchQueue(label: "org.moonfin.pip.shared-bridge")

  private var isActive = false
  private var activeTextureId: Int64?
  private var emittedFrameCount: Int = 0

  private var startObserver: NSObjectProtocol?
  private var stopObserver: NSObjectProtocol?

  private init() {
    installObservers()
  }

  deinit {
    removeObservers()
  }

  func shouldEmitFrame(textureId: Int64) -> Bool {
    queue.sync {
      guard isActive else { return false }
      guard let activeTextureId else { return true }
      return activeTextureId == textureId
    }
  }

  func emitFrame(texture: ResizableTextureProtocol, textureId: Int64) {
    guard let pixelBufferRef = texture.copyPixelBuffer() else {
      return
    }

    let pixelBuffer = pixelBufferRef.takeRetainedValue()
    NotificationCenter.default.post(
      name: Notification.Name("\(channel).frame"),
      object: nil,
      userInfo: [
        "pixelBuffer": pixelBuffer,
        "textureId": textureId,
      ]
    )

    queue.sync {
      emittedFrameCount += 1
    }
  }

  private func installObservers() {
    startObserver = NotificationCenter.default.addObserver(
      forName: Notification.Name("\(channel).start"),
      object: nil,
      queue: nil
    ) { [weak self] notification in
      self?.handleStart(notification)
    }

    stopObserver = NotificationCenter.default.addObserver(
      forName: Notification.Name("\(channel).stop"),
      object: nil,
      queue: nil
    ) { [weak self] _ in
      self?.handleStop()
    }
  }

  private func removeObservers() {
    if let startObserver {
      NotificationCenter.default.removeObserver(startObserver)
      self.startObserver = nil
    }
    if let stopObserver {
      NotificationCenter.default.removeObserver(stopObserver)
      self.stopObserver = nil
    }
  }

  private func handleStart(_ notification: Notification) {
    let requestedTextureId = parseTextureId(notification.userInfo?["textureId"])
    queue.sync {
      isActive = true
      activeTextureId = requestedTextureId
      emittedFrameCount = 0
    }
  }

  private func handleStop() {
    queue.sync {
      isActive = false
      activeTextureId = nil
      emittedFrameCount = 0
    }
  }

  private func parseTextureId(_ value: Any?) -> Int64? {
    if let number = value as? NSNumber {
      return number.int64Value
    }
    if let value = value as? Int64 {
      return value
    }
    if let value = value as? Int {
      return Int64(value)
    }
    return nil
  }
}
