sealed class QuickConnectState {
  const QuickConnectState();
}

class QuickConnectUnknown extends QuickConnectState {
  const QuickConnectUnknown();
}

class QuickConnectUnavailable extends QuickConnectState {
  const QuickConnectUnavailable();
}

class QuickConnectPending extends QuickConnectState {
  final String code;
  const QuickConnectPending({required this.code});
}

class QuickConnectConnected extends QuickConnectState {
  const QuickConnectConnected();
}
