import { router } from 'expo-router';
import { useRef, useState } from 'react';

import { GameForm } from '@/components/games/game-form';
import { PartialSaveError } from '@/features/games/api';
import { emptyGameForm, formValuesToGameInput } from '@/features/games/game-form-schema';
import { useCreateGame } from '@/features/games/queries';
import { useLibraryStore } from '@/features/library/library-store';
import { showMessage } from '@/lib/dialogs';

export default function NewGameScreen() {
  const createGame = useCreateGame();
  const preferredCurrency = useLibraryStore((state) => state.preferredCurrency);
  const setPreferredCurrency = useLibraryStore((state) => state.setPreferredCurrency);
  const [defaultValues] = useState(() => emptyGameForm({ currency: preferredCurrency }));
  const createdId = useRef<string | null>(null);

  return (
    <GameForm
      defaultValues={defaultValues}
      submitLabel="Add to library"
      onSubmit={async (values, onProgress) => {
        setPreferredCurrency(values.currency);
        try {
          createdId.current = await createGame.mutateAsync({
            input: formValuesToGameInput(values),
            images: values.images,
            onProgress,
          });
        } catch (error) {
          if (!(error instanceof PartialSaveError)) throw error;
          createdId.current = error.gameId;
          showMessage('Some photos were not saved', error.message);
        }
      }}
      onSuccess={() => {
        if (createdId.current) {
          router.replace({ pathname: '/games/[id]', params: { id: createdId.current } });
        } else {
          router.back();
        }
      }}
    />
  );
}
