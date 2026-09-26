import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { memo } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { GameCover } from '@/components/games/game-cover';
import { AppText } from '@/components/ui/app-text';
import { regionLabel } from '@/constants/game-options';
import { colors, spacing } from '@/constants/theme';
import type { GameListItem, Platform } from '@/features/games/types';

type Props = {
  game: GameListItem;
  platform: Platform | undefined;
  width: number;
};

export const GameGridItem = memo(function GameGridItem({ game, platform, width }: Props) {
  const meta = [platform?.short_name, regionLabel(game.region)].filter(Boolean).join(' · ');

  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={`${game.title}${platform ? `, ${platform.name}` : ''}`}
      onPress={() => router.push({ pathname: '/games/[id]', params: { id: game.id } })}
      style={({ pressed }) => [{ width }, pressed && styles.pressed]}>
      <View>
        <GameCover
          url={game.coverUrl}
          cacheKey={game.coverPath}
          platformLabel={platform?.short_name}
        />
        {game.is_favorite && (
          <View style={styles.favorite}>
            <Ionicons name="heart" size={12} color={colors.favorite} />
          </View>
        )}
      </View>
      <View style={styles.text}>
        <AppText variant="label" numberOfLines={2}>
          {game.title}
        </AppText>
        {meta ? (
          <AppText variant="caption" tone="muted" numberOfLines={1}>
            {meta}
          </AppText>
        ) : null}
      </View>
    </Pressable>
  );
});

const styles = StyleSheet.create({
  pressed: { opacity: 0.75 },
  text: { gap: 2, paddingTop: spacing.sm, paddingHorizontal: 2 },
  favorite: {
    position: 'absolute',
    top: 6,
    right: 6,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: colors.overlay,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
