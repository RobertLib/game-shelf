import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, StyleSheet } from 'react-native';

import { GameForm } from '@/components/games/game-form';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { colors, spacing } from '@/constants/theme';
import { PartialSaveError } from '@/features/games/api';
import { formValuesToGameInput, gameToFormValues } from '@/features/games/game-form-schema';
import { useGame, useUpdateGame } from '@/features/games/queries';
import type { GameDetail } from '@/features/games/types';
import { errorMessage, showMessage } from '@/lib/dialogs';

export default function EditGameScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const game = useGame(id);

  if (game.isPending) return <ActivityIndicator color={colors.primary} style={styles.loading} />;
  if (game.isError || !game.data) {
    return (
      <EmptyState
        icon="alert-circle-outline"
        title="Could not load this game"
        message={game.isError ? errorMessage(game.error) : 'It may have been deleted.'}
        action={<Button title="Go back" variant="secondary" onPress={() => router.back()} />}
      />
    );
  }
  return <EditGameForm game={game.data} />;
}

function EditGameForm({ game }: { game: GameDetail }) {
  const updateGame = useUpdateGame(game);
  // Snapshot once: later refetches must not reset what the user is typing.
  const [defaultValues] = useState(() => gameToFormValues(game));

  return (
    <GameForm
      defaultValues={defaultValues}
      submitLabel="Save changes"
      onSubmit={async (values, onProgress) => {
        try {
          await updateGame.mutateAsync({
            input: formValuesToGameInput(values),
            images: values.images,
            onProgress,
          });
        } catch (error) {
          if (!(error instanceof PartialSaveError)) throw error;
          showMessage('Some photos were not saved', error.message);
        }
      }}
      onSuccess={() => router.back()}
    />
  );
}

const styles = StyleSheet.create({
  loading: { marginTop: spacing.xxxl },
});
