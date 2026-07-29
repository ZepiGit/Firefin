import AVKit
import CoreMedia
import CoreVideo

@available(iOS 15.0, *)
final class PiPController: NSObject {

  private static let isSimulator: Bool = {
    #if targetEnvironment(simulator)
      return true
    #else
      return false
    #endif
  }()

  static var isSupported: Bool {
    !isSimulator && AVPictureInPictureController.isPictureInPictureSupported()
  }

  static var unsupportedReason: String {
    if isSimulator {
      return "iOS Simulator does not support Picture in Picture for this player"
    }
    if !AVPictureInPictureController.isPictureInPictureSupported() {
      return "Picture in Picture is not supported on this device"
    }
    return "Picture in Picture is unavailable"
  }

  static func sharedFrameEventName(channel: String) -> Notification.Name {
    Notification.Name("\(channel).frame")
  }

  static func sharedPlaybackEventName(channel: String) -> Notification.Name {
    Notification.Name("\(channel).playback")
  }

  static func sharedControlEventName(channel: String, event: String) -> Notification.Name {
    Notification.Name("\(channel).\(event)")
  }

  // MARK: - Callbacks (set by AppDelegate)

  var onPiPStatusChanged: ((Bool) -> Void)?
  var onPiPAction: ((String) -> Void)?
  private var sharedFrameEventChannel: String?
  private var sharedFrameObserver: NSObjectProtocol?
  private var sharedPlaybackObserver: NSObjectProtocol?
  private var sharedFrameCount: Int = 0

  // MARK: - Private state

  private var displayLayer: AVSampleBufferDisplayLayer?
  private var layerHostView: UIView?
  private var pipController: AVPictureInPictureController?

  private var isActive = false
  private var isInPiP = false
  private var isPlaybackPaused = false
  private var isWarmedUp = false
  private(set) var lastErrorMessage: String?

  private var pendingPiPStart = false
  private var pipStartTimeoutItem: DispatchWorkItem?

  // MARK: - Initialization

  @discardableResult
  func configureSharedContextBridge(arguments: [String: Any]) -> Bool {
    guard let frameChannel = arguments["frameEventChannel"] as? String,
          !frameChannel.isEmpty else {
      setError("Missing frameEventChannel for sharedContextFork")
      return false
    }

    sharedFrameEventChannel = frameChannel
    installSharedContextObservers()
    lastErrorMessage = nil
    return true
  }

  /// Initialize the PiP controller and warm up the shared-context PiP pipeline.
  @discardableResult
  func initialize(mpvHandleAddress: Int64, viewController: UIViewController? = nil) -> Bool {
    guard Self.isSupported else {
      setError(Self.unsupportedReason)
      return false
    }
    guard sharedFrameEventChannel != nil else {
      setError("sharedContextFork requires configureSharedContextBridge before initialize")
      return false
    }
    lastErrorMessage = nil
    if let vc = viewController {
      warmUpPiPPipeline(on: vc)
    }
    return true
  }

  // MARK: - PiP lifecycle

  @discardableResult
  func startPiP(on viewController: UIViewController) -> Bool {
    guard Self.isSupported else {
      setError(Self.unsupportedReason)
      return false
    }
    return startSharedContextPiP(on: viewController)
  }

  @discardableResult
  private func startSharedContextPiP(on viewController: UIViewController) -> Bool {
    guard sharedFrameEventChannel != nil else {
      setError("sharedContextFork requires configureSharedContextBridge before startPiP")
      return false
    }

    installSharedContextObservers()
    guard setupPiPLayer(on: viewController) else {
      return false
    }

    if isWarmedUp && sharedFrameCount > 0 {
      isActive = true
      pipController?.startPictureInPicture()
      lastErrorMessage = nil
      return true
    }

    displayLayer?.flush()
    sharedFrameCount = 0
    isActive = true
    pendingPiPStart = true

    pipStartTimeoutItem?.cancel()
    let timeoutItem = DispatchWorkItem { [weak self] in
      guard let self, self.pendingPiPStart else { return }
      self.pendingPiPStart = false
      self.pipController?.startPictureInPicture()
    }
    pipStartTimeoutItem = timeoutItem
    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: timeoutItem)

    notifySharedBridge(event: "start")
    lastErrorMessage = nil
    return true
  }

  private func warmUpPiPPipeline(on viewController: UIViewController) {
    guard setupPiPLayer(on: viewController) else { return }
    isActive = true
    isWarmedUp = true
    notifySharedBridge(event: "start")
  }

  @discardableResult
  private func setupPiPLayer(on viewController: UIViewController) -> Bool {
    if let layer = displayLayer, layer.status == .failed {
      displayLayer?.removeFromSuperlayer()
      layerHostView?.removeFromSuperview()
      layerHostView = nil
      displayLayer = nil
      pipController = nil
    }

    if layerHostView != nil, displayLayer != nil, pipController != nil {
      if layerHostView?.window == nil {
        viewController.view.addSubview(layerHostView!)
      }
      return true
    }

    let layer = AVSampleBufferDisplayLayer()
    layer.videoGravity = .resizeAspect
    displayLayer = layer

    // AVSampleBufferDisplayLayer must live in a view hierarchy for PiP.
    let hostView = UIView(frame: CGRect(x: 0, y: 0, width: 2, height: 2))
    hostView.alpha = 0
    layer.frame = hostView.bounds
    hostView.layer.addSublayer(layer)
    viewController.view.addSubview(hostView)
    layerHostView = hostView

    let contentSource = AVPictureInPictureController.ContentSource(
      sampleBufferDisplayLayer: layer,
      playbackDelegate: self
    )
    let pip = AVPictureInPictureController(contentSource: contentSource)
    pip.delegate = self
    pip.canStartPictureInPictureAutomaticallyFromInline = true
    pipController = pip
    return true
  }

  private func installSharedContextObservers() {
    removeSharedContextObservers()
    guard let channel = sharedFrameEventChannel else { return }

    let frameName = Self.sharedFrameEventName(channel: channel)
    sharedFrameObserver = NotificationCenter.default.addObserver(
      forName: frameName,
      object: nil,
      queue: .main
    ) { [weak self] notification in
      self?.handleSharedFrameNotification(notification)
    }

    let playbackName = Self.sharedPlaybackEventName(channel: channel)
    sharedPlaybackObserver = NotificationCenter.default.addObserver(
      forName: playbackName,
      object: nil,
      queue: .main
    ) { [weak self] notification in
      self?.handleSharedPlaybackNotification(notification)
    }
  }

  private func removeSharedContextObservers() {
    if let observer = sharedFrameObserver {
      NotificationCenter.default.removeObserver(observer)
      sharedFrameObserver = nil
    }
    if let observer = sharedPlaybackObserver {
      NotificationCenter.default.removeObserver(observer)
      sharedPlaybackObserver = nil
    }
  }

  private func handleSharedFrameNotification(_ notification: Notification) {
    guard isActive else { return }

    let buffer: CMSampleBuffer?
    if let sample = notification.userInfo?["sampleBuffer"] {
      let cf = sample as CFTypeRef
      buffer = CFGetTypeID(cf) == CMSampleBufferGetTypeID()
        ? unsafeBitCast(cf, to: CMSampleBuffer.self)
        : nil
    } else if let pixel = notification.userInfo?["pixelBuffer"] {
      let cf = pixel as CFTypeRef
      if CFGetTypeID(cf) == CVPixelBufferGetTypeID() {
        let pixelBuffer = unsafeBitCast(cf, to: CVPixelBuffer.self)
        buffer = wrapInSampleBuffer(pixelBuffer)
      } else {
        buffer = nil
      }
    } else {
      buffer = nil
    }

    guard let sampleBuffer = buffer else { return }
    displayLayer?.enqueue(sampleBuffer)

    sharedFrameCount += 1

    if pendingPiPStart && sharedFrameCount >= 2 {
      resetPendingPiPStartState()
      pipController?.startPictureInPicture()
    }
  }

  private func handleSharedPlaybackNotification(_ notification: Notification) {
    if let isPlaying = notification.userInfo?["isPlaying"] as? Bool {
      updatePlaybackState(isPlaying: isPlaying)
    }
  }

  private func notifySharedBridge(event: String) {
    guard let channel = sharedFrameEventChannel else { return }
    NotificationCenter.default.post(
      name: Self.sharedControlEventName(channel: channel, event: event),
      object: self,
      userInfo: nil
    )
  }

  func stopPiP() {
    isActive = false
    isWarmedUp = false
    pipController?.stopPictureInPicture()
    notifySharedBridge(event: "stop")
    teardown()
  }

  func dismissPiP() {
    guard isInPiP else { return }
    resetPendingPiPStartState()
    displayLayer?.flushAndRemoveImage()
    pipController?.stopPictureInPicture()
  }

  func updatePlaybackState(isPlaying: Bool) {
    isPlaybackPaused = !isPlaying
    pipController?.invalidatePlaybackState()
  }

  private func teardown() {
    pendingPiPStart = false
    pipStartTimeoutItem?.cancel()
    pipStartTimeoutItem = nil
    sharedFrameCount = 0
    removeSharedContextObservers()
    layerHostView?.removeFromSuperview()
    layerHostView = nil
    displayLayer = nil
    pipController = nil
  }

  // MARK: - Helpers

  private func wrapInSampleBuffer(_ pixelBuffer: CVPixelBuffer) -> CMSampleBuffer? {
    var formatDesc: CMVideoFormatDescription?
    CMVideoFormatDescriptionCreateForImageBuffer(
      allocator: nil,
      imageBuffer: pixelBuffer,
      formatDescriptionOut: &formatDesc
    )
    guard let fd = formatDesc else { return nil }

    let now = CMClockGetTime(CMClockGetHostTimeClock())
    var timing = CMSampleTimingInfo(
      duration:               .invalid,
      presentationTimeStamp:  now,
      decodeTimeStamp:        .invalid
    )

    var sb: CMSampleBuffer?
    CMSampleBufferCreateForImageBuffer(
      allocator:             nil,
      imageBuffer:           pixelBuffer,
      dataReady:             true,
      makeDataReadyCallback: nil,
      refcon:                nil,
      formatDescription:     fd,
      sampleTiming:          &timing,
      sampleBufferOut:       &sb
    )
    if let sb {
      if let attachments = CMSampleBufferGetSampleAttachmentsArray(sb, createIfNecessary: true) {
        let attachment = unsafeBitCast(CFArrayGetValueAtIndex(attachments, 0), to: CFMutableDictionary.self)
        CFDictionarySetValue(
          attachment,
          Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(),
          Unmanaged.passUnretained(kCFBooleanTrue).toOpaque()
        )
      }
    }
    return sb
  }

  deinit {
    removeSharedContextObservers()
  }

  private func setError(_ message: String) {
    lastErrorMessage = message
  }

  private func resetPendingPiPStartState() {
    pendingPiPStart = false
    pipStartTimeoutItem?.cancel()
    pipStartTimeoutItem = nil
  }

  private func handlePiPTermination(notifyStopWhenNotWarmedUp: Bool) {
    onPiPStatusChanged?(false)
    sharedFrameCount = 0
    if isWarmedUp {
      isActive = true
      notifySharedBridge(event: "start")
    } else {
      isActive = false
      if notifyStopWhenNotWarmedUp {
        notifySharedBridge(event: "stop")
      }
    }
  }
}

// MARK: - AVPictureInPictureControllerDelegate

@available(iOS 15.0, *)
extension PiPController: AVPictureInPictureControllerDelegate {

  func pictureInPictureControllerDidStartPictureInPicture(
    _ controller: AVPictureInPictureController
  ) {
    isInPiP = true
    onPiPStatusChanged?(true)
  }

  func pictureInPictureControllerDidStopPictureInPicture(
    _ controller: AVPictureInPictureController
  ) {
    isInPiP = false
    resetPendingPiPStartState()
    handlePiPTermination(notifyStopWhenNotWarmedUp: true)
  }

  func pictureInPictureController(
    _ controller: AVPictureInPictureController,
    failedToStartPictureInPictureWithError error: Error
  ) {
    resetPendingPiPStartState()
    handlePiPTermination(notifyStopWhenNotWarmedUp: false)
  }

  func pictureInPictureController(
    _ controller: AVPictureInPictureController,
    restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
  ) {
    completionHandler(true)
  }
}

// MARK: - AVPictureInPictureSampleBufferPlaybackDelegate

@available(iOS 15.0, *)
extension PiPController: AVPictureInPictureSampleBufferPlaybackDelegate {

  func pictureInPictureController(
    _ pictureInPictureController: AVPictureInPictureController,
    setPlaying playing: Bool
  ) {
    onPiPAction?(playing ? "play" : "pause")
  }

  func pictureInPictureControllerTimeRangeForPlayback(
    _ pictureInPictureController: AVPictureInPictureController
  ) -> CMTimeRange {
    CMTimeRange(start: .negativeInfinity, duration: .positiveInfinity)
  }

  func pictureInPictureControllerIsPlaybackPaused(
    _ pictureInPictureController: AVPictureInPictureController
  ) -> Bool {
    isPlaybackPaused
  }

  func pictureInPictureController(
    _ pictureInPictureController: AVPictureInPictureController,
    didTransitionToRenderSize newRenderSize: CMVideoDimensions
  ) {}

  func pictureInPictureController(
    _ pictureInPictureController: AVPictureInPictureController,
    skipByInterval skipInterval: CMTime,
    completion completionHandler: @escaping () -> Void
  ) {
    completionHandler()
  }
}
