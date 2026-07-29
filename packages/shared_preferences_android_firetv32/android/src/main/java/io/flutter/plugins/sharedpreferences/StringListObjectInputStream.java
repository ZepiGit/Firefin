// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.sharedpreferences;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * ObjectInputStream restricted to the classes used by legacy string lists.
 *
 * <p>This Java equivalent keeps the Fire TV build independent of Kotlin and
 * AndroidX DataStore while preserving the upstream deserialization allow-list.
 */
final class StringListObjectInputStream extends ObjectInputStream {
  private static final Set<String> ALLOWED_CLASSES =
      new HashSet<>(
          Arrays.asList(
              "java.util.Arrays$ArrayList",
              "java.util.ArrayList",
              "java.lang.String",
              "[Ljava.lang.String;"));

  StringListObjectInputStream(InputStream input) throws IOException {
    super(input);
  }

  @Override
  protected Class<?> resolveClass(ObjectStreamClass descriptor)
      throws IOException, ClassNotFoundException {
    if (!ALLOWED_CLASSES.contains(descriptor.getName())) {
      throw new ClassNotFoundException(descriptor.getName());
    }
    return super.resolveClass(descriptor);
  }
}
