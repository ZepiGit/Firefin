import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

import 'sync_service.dart';
import '../../util/platform_detection.dart';

class ConnectivityService extends ChangeNotifier {
  final Connectivity _connectivity = Connectivity();
  StreamSubscription<List<ConnectivityResult>>? _subscription;
  Timer? _recheckDebounce;
  Timer? _startupRecheck;
  Timer? _serverRetryTimer;

  bool _isOnline = true;
  bool get isOnline => _isOnline;

  bool _serverReachable = true;
  bool get serverReachable => _serverReachable;
  int _consecutiveServerFailures = 0;

  bool get canReachServer => _isOnline && _serverReachable;

  /// Whether the initial connectivity check has completed.
  /// Stream events are ignored until this is true to prevent
  /// a false "offline" flash at boot.
  bool _initialCheckDone = false;

  void initialize() {
    _subscription = _connectivity.onConnectivityChanged.listen(
      _onConnectivityChanged,
    );
    _checkInitialState();
    _startupRecheck = Timer(const Duration(seconds: 8), recheckNow);
    _serverRetryTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      if (_isOnline && !_serverReachable) {
        recheckNow();
      }
    });
  }

  Future<void> _checkInitialState() async {
    final results = await _connectivity.checkConnectivity();
    _isOnline = results.any((r) => r != ConnectivityResult.none);
    if (_isOnline) {
      await _checkServerReachability();
    } else {
      _serverReachable = false;
    }
    _initialCheckDone = true;
    notifyListeners();
  }

  void _onConnectivityChanged(List<ConnectivityResult> results) {
    if (!_initialCheckDone) return;

    final wasOnline = _isOnline;
    final wasReachable = _serverReachable;
    _isOnline = results.any((r) => r != ConnectivityResult.none);

    if (!_isOnline) {
      if (wasOnline) {
        _serverReachable = false;
        notifyListeners();
      }
      return;
    }

    if (!wasOnline) {
      notifyListeners();
    }

    _recheckDebounce?.cancel();
    _recheckDebounce = Timer(const Duration(seconds: 2), () {
      _checkServerReachability().then((_) {
        if (_serverReachable && !wasReachable) {
          _triggerSync();
        }
      });
    });
  }

  void _triggerSync() {
    final getIt = GetIt.instance;
    if (!getIt.isRegistered<SyncService>() ||
        !getIt.isRegistered<MediaServerClient>()) {
      return;
    }
    final syncService = getIt<SyncService>();
    final client = getIt<MediaServerClient>();
    syncService.syncPlaybackProgress(client);
  }

  Future<void> _checkServerReachability() async {
    // The dedicated Fire OS 5 build already proves server reachability through
    // the authenticated content requests that populate each screen. The old
    // TLS stack can nevertheless fail Jellyfin's separate /System/Ping call
    // and cause a false orange warning while artwork is visibly loading.
    if (PlatformDetection.isAndroid && PlatformDetection.isTV) {
      _consecutiveServerFailures = 0;
      _serverReachable = true;
      notifyListeners();
      return;
    }
    if (!GetIt.instance.isRegistered<MediaServerClient>()) return;
    final client = GetIt.instance<MediaServerClient>();
    try {
      final reachable = await client.systemApi.ping().timeout(
        const Duration(seconds: 6),
      );
      if (!reachable) {
        throw StateError('Server ping failed');
      }
      _consecutiveServerFailures = 0;
      _serverReachable = true;
    } catch (_) {
      // Fire OS 5 occasionally drops a single TLS request while artwork is
      // loading. Require two consecutive failed pings before showing the
      // disruptive server-unavailable banner.
      _consecutiveServerFailures++;
      if (_consecutiveServerFailures >= 2) {
        _serverReachable = false;
      }
    }
    notifyListeners();
  }

  Future<void> recheckNow() async {
    final results = await _connectivity.checkConnectivity();
    _isOnline = results.any((r) => r != ConnectivityResult.none);
    if (_isOnline) {
      await _checkServerReachability();
    } else {
      _consecutiveServerFailures = 0;
      _serverReachable = false;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _subscription?.cancel();
    _recheckDebounce?.cancel();
    _startupRecheck?.cancel();
    _serverRetryTimer?.cancel();
    super.dispose();
  }
}
