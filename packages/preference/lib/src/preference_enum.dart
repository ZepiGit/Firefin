import 'preference_base.dart';

class EnumPreference<T extends Enum> extends Preference<T> {
  final List<T> values;

  const EnumPreference({
    required super.key,
    required super.defaultValue,
    required this.values,
  });
}
