import 'package:flutter/foundation.dart';

import '../repositories/seerr_repository.dart';
import '../services/seerr/seerr_api_models.dart';

class SeerrRequestsState {
  final bool isLoading;
  final bool isRefreshing;
  final String? error;
  final List<SeerrRequest> requests;
  final SeerrUser? currentUser;
  final int? actioningRequestId;

  const SeerrRequestsState({
    this.isLoading = false,
    this.isRefreshing = false,
    this.error,
    this.requests = const [],
    this.currentUser,
    this.actioningRequestId,
  });

  bool get canManageRequests =>
      currentUser?.hasPermission(SeerrPermission.manageRequests) ?? false;

  SeerrRequestsState copyWith({
    bool? isLoading,
    bool? isRefreshing,
    String? error,
    List<SeerrRequest>? requests,
    SeerrUser? currentUser,
    Object? actioningRequestId = _sentinel,
  }) => SeerrRequestsState(
    isLoading: isLoading ?? this.isLoading,
    isRefreshing: isRefreshing ?? this.isRefreshing,
    error: error,
    requests: requests ?? this.requests,
    currentUser: currentUser ?? this.currentUser,
    actioningRequestId: actioningRequestId == _sentinel
        ? this.actioningRequestId
        : actioningRequestId as int?,
  );
}

const _sentinel = Object();

class SeerrRequestsViewModel extends ChangeNotifier {
  final SeerrRepository _repo;

  SeerrRequestsState _state = const SeerrRequestsState();
  SeerrRequestsState get state => _state;

  SeerrRequestsViewModel(this._repo);

  Future<void> load({bool isRefresh = false}) async {
    _state = SeerrRequestsState(
      isLoading: !isRefresh,
      isRefreshing: isRefresh,
      requests: isRefresh ? _state.requests : const [],
      currentUser: _state.currentUser,
    );
    notifyListeners();

    try {
      await _repo.ensureInitialized();
      final user = await _repo.getCurrentUser();
      final response = await _repo.getRequests(
        requestedBy: user.canViewAllRequests ? null : user.id,
      );

      final now = DateTime.now();
      final filtered = response.results.where((r) {
        if (r.status == SeerrRequest.statusDeclined) {
          final updated = r.updatedAt != null
              ? DateTime.tryParse(r.updatedAt!)
              : null;
          if (updated != null && now.difference(updated).inDays > 3) {
            return false;
          }
        }
        return true;
      }).toList();

      _state = SeerrRequestsState(requests: filtered, currentUser: user);
    } catch (e) {
      _state = SeerrRequestsState(error: e.toString());
    }
    notifyListeners();
  }

  Future<void> refresh() => load(isRefresh: true);

  Future<void> approveRequest(int requestId) =>
      _runAction(requestId, () => _repo.approveRequest(requestId));

  Future<void> declineRequest(int requestId) =>
      _runAction(requestId, () => _repo.declineRequest(requestId));

  Future<void> _runAction(int requestId, Future<void> Function() action) async {
    _state = _state.copyWith(actioningRequestId: requestId);
    notifyListeners();

    try {
      await action();
      await load(isRefresh: true);
    } catch (_) {
      _state = _state.copyWith(actioningRequestId: null);
      notifyListeners();
    }
  }
}
