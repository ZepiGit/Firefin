import 'package:flutter/foundation.dart';

final ValueNotifier<int> homeRefreshBus = ValueNotifier<int>(0);
bool _pendingHomeRefresh = false;

void requestHomeRefresh() {
  homeRefreshBus.value = homeRefreshBus.value + 1;
}

void requestHomeRefreshAfterNavigation() {
  _pendingHomeRefresh = true;
}

bool consumePendingHomeRefresh() {
  if (!_pendingHomeRefresh) {
    return false;
  }
  _pendingHomeRefresh = false;
  return true;
}
