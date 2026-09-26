import Ionicons from '@expo/vector-icons/Ionicons';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  useWindowDimensions,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GameCover } from '@/components/games/game-cover';
import { ImageGallery } from '@/components/games/image-gallery';
import { InfoRow } from '@/components/games/info-row';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { HeaderTextButton } from '@/components/ui/header-text-button';
import { RatingStars } from '@/components/ui/rating-stars';
import { Section } from '@/components/ui/section';
import {
  completenessLabel,
  conditionLabel,
  formatLabel,
  playStatusLabel,
  REGIONS,
  regionLabel,
} from '@/constants/game-options';
import { colors, MAX_CONTENT_WIDTH, radius, spacing } from '@/constants/theme';
import { useDeleteGame, useGame, usePlatforms, useToggleFavorite } from '@/features/games/queries';
import type { GameDetail } from '@/features/games/types';
import { confirm, errorMessage, showMessage } from '@/lib/dialogs';
import { formatDate, formatMoney } from '@/lib/format';

export default function GameDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const game = useGame(id);

  if (game.isPending) {
    return <ActivityIndicator color={colors.primary} style={styles.loading} />;
  }
  if (game.isError) {
    return (
      <EmptyState
        icon="cloud-offline-outline"
        title="Could not load this game"
        message={errorMessage(game.error)}
        action={
          <Button title="Try again" variant="secondary" onPress={() => void game.refetch()} />
        }
      />
    );
  }
  if (!game.data) {
    return (
      <EmptyState
        icon="help-circle-outline"
        title="Game not found"
        message="It may have been deleted."
        action={
          <Button title="Back to library" variant="secondary" onPress={() => router.back()} />
        }
      />
    );
  }
  return <GameDetailContent game={game.data} />;
}

type Row = { label: string; value: ReactNode };

function rows(entries: [string, ReactNode | null | undefined][]): Row[] {
  return entries
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([label, value]) => ({ label, value }));
}

function InfoSection({ title, items }: { title: string; items: Row[] }) {
  if (items.length === 0) return null;
  return (
    <Section title={title} dense>
      {items.map((item, index) => (
        <InfoRow
          key={item.label}
          label={item.label}
          value={item.value}
          last={index === items.length - 1}
        />
      ))}
    </Section>
  );
}

function GameDetailContent({ game }: { game: GameDetail }) {
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const { byId } = usePlatforms();
  const platform = byId.get(game.platform_id);
  const toggleFavorite = useToggleFavorite();
  const deleteGame = useDeleteGame();

  const onDelete = async () => {
    const confirmed = await confirm({
      title: `Delete "${game.title}"?`,
      message:
        'The game and all its photos will be removed from your library. This cannot be undone.',
      confirmLabel: 'Delete',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await deleteGame.mutateAsync(game.id);
      router.back();
    } catch (error) {
      showMessage('Could not delete the game', errorMessage(error));
    }
  };

  const region = REGIONS.find((r) => r.value === game.region);
  const isPhysical = game.format === 'physical';

  const copyRows = rows([
    [
      'Region',
      region ? `${region.label}${region.description ? ` · ${region.description}` : ''}` : null,
    ],
    ['Format', formatLabel(game.format)],
    ['Condition', isPhysical ? conditionLabel(game.condition) : null],
    ['Completeness', isPhysical ? completenessLabel(game.completeness) : null],
    ['Serial / code', game.serial_number],
  ]);
  const detailRows = rows([
    ['Genres', game.genres.length ? game.genres.join(', ') : null],
    ['Developer', game.developer],
    ['Publisher', game.publisher],
    ['Released', game.release_year],
  ]);
  const purchaseRows = rows([
    ['Purchased', formatDate(game.purchase_date)],
    ['Price paid', formatMoney(game.purchase_price, game.currency)],
    ['Current value', formatMoney(game.current_value, game.currency)],
    ['Bought from', game.purchased_from],
  ]);
  const personalRows = rows([
    ['Play status', playStatusLabel(game.play_status)],
    ['My rating', game.rating ? <RatingStars value={game.rating} size={16} /> : null],
  ]);

  const galleryHeight = Math.min(width * 1.05, 520);

  return (
    <ScrollView contentContainerStyle={{ paddingBottom: insets.bottom + spacing.xxl }}>
      <Stack.Screen
        options={{
          title: '',
          headerRight: () => (
            <HeaderTextButton
              title="Edit"
              onPress={() => router.push({ pathname: '/games/[id]/edit', params: { id: game.id } })}
            />
          ),
        }}
      />

      {game.images.length > 0 ? (
        <View style={styles.galleryWrap}>
          <ImageGallery images={game.images} height={galleryHeight} />
        </View>
      ) : (
        <View style={styles.noPhotos}>
          <GameCover
            url={null}
            platformLabel={platform?.short_name}
            style={styles.placeholderCover}
          />
        </View>
      )}

      <View style={styles.body}>
        <View style={styles.titleRow}>
          <View style={styles.titleText}>
            <AppText variant="title" selectable>
              {game.title}
            </AppText>
            {game.edition && (
              <AppText tone="secondary" weight="600">
                {game.edition}
              </AppText>
            )}
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={game.is_favorite ? 'Remove from favorites' : 'Add to favorites'}
            accessibilityState={{ selected: game.is_favorite }}
            onPress={() => toggleFavorite.mutate({ id: game.id, isFavorite: !game.is_favorite })}
            hitSlop={8}
            style={({ pressed }) => [styles.favorite, pressed && styles.pressed]}>
            <Ionicons
              name={game.is_favorite ? 'heart' : 'heart-outline'}
              size={24}
              color={game.is_favorite ? colors.favorite : colors.textSecondary}
            />
          </Pressable>
        </View>

        <View style={styles.badges}>
          {platform && (
            <View style={[styles.badge, styles.platformBadge]}>
              <Ionicons name="hardware-chip-outline" size={14} color={colors.primary} />
              <AppText variant="label" tone="primary">
                {platform.name}
              </AppText>
            </View>
          )}
          {[
            regionLabel(game.region),
            isPhysical ? completenessLabel(game.completeness, 'short') : 'Digital',
          ]
            .filter(Boolean)
            .map((label) => (
              <View key={label} style={styles.badge}>
                <AppText variant="label" tone="secondary">
                  {label}
                </AppText>
              </View>
            ))}
        </View>

        <InfoSection title="Your copy" items={copyRows} />
        <InfoSection title="Details" items={detailRows} />
        <InfoSection title="Purchase" items={purchaseRows} />
        <InfoSection title="Personal" items={personalRows} />

        {game.notes && (
          <Section title="Notes">
            <AppText selectable>{game.notes}</AppText>
          </Section>
        )}

        <AppText variant="caption" tone="muted" align="center">
          Added {formatDate(game.created_at.slice(0, 10))}
          {game.updated_at.slice(0, 10) !== game.created_at.slice(0, 10)
            ? ` · Updated ${formatDate(game.updated_at.slice(0, 10))}`
            : ''}
        </AppText>

        <Button
          title="Delete game"
          variant="danger"
          icon="trash-outline"
          loading={deleteGame.isPending}
          onPress={() => void onDelete()}
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  loading: { marginTop: spacing.xxxl },
  galleryWrap: { backgroundColor: colors.surface },
  noPhotos: { alignItems: 'center', paddingVertical: spacing.xl, backgroundColor: colors.surface },
  placeholderCover: { width: 150 },
  body: {
    padding: spacing.lg,
    gap: spacing.xl,
    width: '100%',
    maxWidth: MAX_CONTENT_WIDTH,
    alignSelf: 'center',
  },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.md },
  titleText: { flex: 1, gap: spacing.xs },
  favorite: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceRaised,
    borderWidth: 1,
    borderColor: colors.border,
  },
  pressed: { opacity: 0.7 },
  badges: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginTop: -spacing.sm },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceRaised,
    borderWidth: 1,
    borderColor: colors.border,
  },
  platformBadge: { backgroundColor: colors.primarySoft, borderColor: 'transparent' },
});
