import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { memo } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { GameCover } from '@/components/games/game-cover';
import { AppText } from '@/components/ui/app-text';
import { RatingStars } from '@/components/ui/rating-stars';
import { completenessLabel, conditionLabel, regionLabel } from '@/constants/game-options';
import { colors, radius, spacing } from '@/constants/theme';
import type { GameListItem, Platform } from '@/features/games/types';

type Props = {
  game: GameListItem;
  platform: Platform | undefined;
};

export const GameListRow = memo(function GameListRow({ game, platform }: Props) {
  const details = [
    regionLabel(game.region),
    game.format === 'digital' ? 'Digital' : completenessLabel(game.completeness, 'short'),
    conditionLabel(game.condition),
    game.release_year,
  ].filter(Boolean);

  return (
    <Pressable
      accessibilityRole="link"
      onPress={() => router.push({ pathname: '/games/[id]', params: { id: game.id } })}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
      <GameCover url={game.coverUrl} cacheKey={game.coverPath} style={styles.cover} compact />
      <View style={styles.body}>
        <View style={styles.titleRow}>
          <AppText weight="700" numberOfLines={2} style={styles.title}>
            {game.title}
          </AppText>
          {game.is_favorite && <Ionicons name="heart" size={16} color={colors.favorite} />}
        </View>
        {game.edition && (
          <AppText variant="caption" tone="secondary" numberOfLines={1}>
            {game.edition}
          </AppText>
        )}
        <View style={styles.metaRow}>
          {platform && (
            <View style={styles.platformBadge}>
              <AppText variant="caption" weight="700" tone="primary">
                {platform.short_name}
              </AppText>
            </View>
          )}
          <AppText variant="caption" tone="muted" numberOfLines={1} style={styles.flex}>
            {details.join(' · ')}
          </AppText>
        </View>
        {game.rating ? <RatingStars value={game.rating} size={12} /> : null}
      </View>
      <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
    </Pressable>
  );
});

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    padding: spacing.sm,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  pressed: { backgroundColor: colors.surfaceRaised },
  cover: { width: 58, borderRadius: radius.sm },
  body: { flex: 1, gap: 4 },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.sm },
  title: { flex: 1 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  platformBadge: {
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: radius.sm,
    backgroundColor: colors.primarySoft,
  },
  flex: { flex: 1 },
});
