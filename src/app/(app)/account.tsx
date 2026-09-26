import Constants from 'expo-constants';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Section } from '@/components/ui/section';
import { colors, MAX_CONTENT_WIDTH, radius, spacing } from '@/constants/theme';
import { useCollectionStats, usePlatforms } from '@/features/games/queries';
import { confirm, errorMessage, showMessage } from '@/lib/dialogs';
import { formatDate, formatMoney } from '@/lib/format';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/providers/auth-provider';

const TOP_PLATFORMS = 8;

export default function AccountScreen() {
  const user = useAuth().session?.user;
  const insets = useSafeAreaInsets();
  const stats = useCollectionStats();
  const { byId } = usePlatforms();

  const signOut = async () => {
    const confirmed = await confirm({ title: 'Sign out?', confirmLabel: 'Sign out' });
    if (!confirmed) return;
    const { error } = await supabase.auth.signOut();
    if (error) showMessage('Could not sign out', errorMessage(error));
  };

  const data = stats.data;
  const topPlatforms = data?.platforms.slice(0, TOP_PLATFORMS) ?? [];
  const maxCount = topPlatforms[0]?.count ?? 1;

  // Briefly null while signing out, before the navigator leaves this screen.
  if (!user) return null;

  return (
    <ScrollView
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing.xxl }]}>
      <View style={styles.profile}>
        <View style={styles.avatar}>
          <AppText variant="title" tone="onPrimary">
            {(user.email ?? '?').charAt(0).toUpperCase()}
          </AppText>
        </View>
        <View style={styles.profileText}>
          <AppText weight="700" numberOfLines={1}>
            {user.email}
          </AppText>
          <AppText variant="caption" tone="muted">
            Collector since {formatDate(user.created_at.slice(0, 10))}
          </AppText>
        </View>
      </View>

      {stats.isPending ? (
        <ActivityIndicator color={colors.primary} />
      ) : stats.isError ? (
        <AppText tone="danger">Could not load statistics: {errorMessage(stats.error)}</AppText>
      ) : data ? (
        <>
          <View style={styles.statGrid}>
            <Stat label="Games" value={data.total_games} />
            <Stat label="Platforms" value={data.platforms.length} />
            <Stat label="Favorites" value={data.favorites} />
            <Stat label="Completed" value={data.play_status.completed ?? 0} />
          </View>

          {data.totals.length > 0 && (
            <Section title="Collection value" dense>
              {data.totals.map((total, index) => (
                <View
                  key={total.currency}
                  style={[styles.valueRow, index < data.totals.length - 1 && styles.divider]}>
                  <AppText weight="700">{total.currency}</AppText>
                  <View style={styles.valueColumns}>
                    <View style={styles.valueColumn}>
                      <AppText variant="caption" tone="muted">
                        Spent
                      </AppText>
                      <AppText weight="600">{formatMoney(total.spent, total.currency)}</AppText>
                    </View>
                    <View style={styles.valueColumn}>
                      <AppText variant="caption" tone="muted">
                        Est. value
                      </AppText>
                      <AppText weight="700" tone="primary">
                        {formatMoney(total.value, total.currency)}
                      </AppText>
                    </View>
                  </View>
                </View>
              ))}
            </Section>
          )}

          {topPlatforms.length > 0 && (
            <Section title="Top platforms">
              {topPlatforms.map((entry) => (
                <View key={entry.platform_id} style={styles.barRow}>
                  <View style={styles.barLabel}>
                    <AppText variant="label" numberOfLines={1} style={styles.flex}>
                      {byId.get(entry.platform_id)?.name ?? entry.platform_id}
                    </AppText>
                    <AppText variant="label" tone="muted">
                      {entry.count}
                    </AppText>
                  </View>
                  <View style={styles.barTrack}>
                    <View
                      style={[styles.barFill, { width: `${(entry.count / maxCount) * 100}%` }]}
                    />
                  </View>
                </View>
              ))}
            </Section>
          )}
        </>
      ) : null}

      <Button
        title="Sign out"
        variant="secondary"
        icon="log-out-outline"
        onPress={() => void signOut()}
      />
      <AppText variant="caption" tone="muted" align="center">
        Game Shelf {Constants.expoConfig?.version ?? ''}
      </AppText>
    </ScrollView>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <View style={styles.stat}>
      <AppText variant="title">{value}</AppText>
      <AppText variant="caption" tone="muted">
        {label}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  content: {
    padding: spacing.lg,
    gap: spacing.xl,
    width: '100%',
    maxWidth: MAX_CONTENT_WIDTH,
    alignSelf: 'center',
  },
  profile: { flexDirection: 'row', alignItems: 'center', gap: spacing.lg },
  avatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  profileText: { flex: 1, gap: 2 },
  statGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  stat: {
    flexGrow: 1,
    flexBasis: '40%',
    padding: spacing.lg,
    gap: 2,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  valueRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: spacing.md,
  },
  divider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border },
  valueColumns: { flexDirection: 'row', gap: spacing.xl },
  valueColumn: { alignItems: 'flex-end' },
  barRow: { gap: 6 },
  barLabel: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  flex: { flex: 1 },
  barTrack: {
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.surfaceRaised,
    overflow: 'hidden',
  },
  barFill: { height: '100%', borderRadius: 3, backgroundColor: colors.primary },
});
