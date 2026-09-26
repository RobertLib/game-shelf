import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { colors, COVER_ASPECT_RATIO, radius } from '@/constants/theme';

type GameCoverProps = {
  url: string | null;
  /** Stable cache key (the storage path) so re-signed URLs hit the disk cache. */
  cacheKey?: string | null;
  platformLabel?: string;
  style?: StyleProp<ViewStyle>;
  compact?: boolean;
};

export function GameCover({
  url,
  cacheKey,
  platformLabel,
  style,
  compact = false,
}: GameCoverProps) {
  return (
    <View style={[styles.frame, style]}>
      {url ? (
        <Image
          source={{ uri: url, cacheKey: cacheKey ?? undefined }}
          style={StyleSheet.absoluteFill}
          contentFit="cover"
          transition={150}
          cachePolicy="disk"
          recyclingKey={cacheKey ?? url}
          accessibilityIgnoresInvertColors
        />
      ) : (
        <View style={styles.placeholder}>
          <Ionicons
            name="game-controller-outline"
            size={compact ? 20 : 32}
            color={colors.textMuted}
          />
          {!compact && platformLabel && (
            <AppText variant="caption" tone="muted" weight="700" numberOfLines={1}>
              {platformLabel}
            </AppText>
          )}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  frame: {
    aspectRatio: COVER_ASPECT_RATIO,
    borderRadius: radius.md,
    overflow: 'hidden',
    backgroundColor: colors.surfaceRaised,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  placeholder: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    padding: 8,
  },
});
