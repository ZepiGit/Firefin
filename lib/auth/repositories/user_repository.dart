import 'dart:async';

import '../models/user.dart';

class UserRepository {
  User? _currentUser;
  final _controller = StreamController<User?>.broadcast();

  User? get currentUser => _currentUser;
  Stream<User?> get currentUserStream => _controller.stream;

  void setCurrentUser(User? user) {
    _currentUser = user;
    _controller.add(user);
  }

  void dispose() {
    _controller.close();
  }
}
