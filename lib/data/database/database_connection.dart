import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';

LazyDatabase openConnection(Future<File> Function() getDbFile) {
  return LazyDatabase(() async {
    final file = await getDbFile();
    if (!file.parent.existsSync()) {
      await file.parent.create(recursive: true);
    }
    return NativeDatabase.createInBackground(file);
  });
}
