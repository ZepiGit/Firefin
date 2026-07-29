class Preference<T> {
  final String key;
  final T defaultValue;

  const Preference({
    required this.key,
    required this.defaultValue,
  });
}
