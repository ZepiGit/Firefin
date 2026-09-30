import 'package:flutter_test/flutter_test.dart';
import 'package:moonfin/preference/user_preferences.dart';

void main() {
  group('UserPreferences.leanTvEffective', () {
    test('forces focus expansion / media bar / previews off', () {
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.cardFocusExpansion,
          true,
        ),
        isFalse,
      );
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.mediaBarEnabled,
          true,
        ),
        isFalse,
      );
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.episodePreviewEnabled,
          true,
        ),
        isFalse,
      );
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.mediaBarTrailerPreview,
          true,
        ),
        isFalse,
      );
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.previewAudioEnabled,
          true,
        ),
        isFalse,
      );
    });

    test('forces blur amounts to 0', () {
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.detailsBackgroundBlurAmount,
          25,
        ),
        0,
      );
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.browsingBackgroundBlurAmount,
          10,
        ),
        0,
      );
    });

    test('does not hard-force backdropEnabled', () {
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.backdropEnabled,
          false,
        ),
        isFalse,
      );
      expect(
        UserPreferences.leanTvEffective(
          UserPreferences.backdropEnabled,
          true,
        ),
        isTrue,
      );
    });
  });
}
