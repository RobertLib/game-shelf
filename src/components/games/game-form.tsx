import { zodResolver } from '@hookform/resolvers/zod';
import { Stack, useNavigation } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { OptionChips } from '@/components/ui/chip';
import { DateField } from '@/components/ui/date-field';
import { HeaderTextButton } from '@/components/ui/header-text-button';
import { RatingStars } from '@/components/ui/rating-stars';
import { Field, Section } from '@/components/ui/section';
import { TextField } from '@/components/ui/text-field';
import { ToggleRow } from '@/components/ui/toggle-row';
import {
  COMPLETENESS,
  CONDITIONS,
  CURRENCIES,
  FORMATS,
  GENRES,
  MAX_GENRES_PER_GAME,
  PLAY_STATUSES,
  REGIONS,
} from '@/constants/game-options';
import { colors, MAX_CONTENT_WIDTH, spacing } from '@/constants/theme';
import { gameFormSchema, type GameFormValues } from '@/features/games/game-form-schema';
import type { ProgressCallback } from '@/features/games/types';
import { confirm, errorMessage, showMessage } from '@/lib/dialogs';

import { ImagesField } from './images-field';
import { PlatformField } from './platform-picker';

const GENRE_OPTIONS = GENRES.map((genre) => ({ value: genre, label: genre }));
const CURRENCY_OPTIONS = CURRENCIES.map((code) => ({ value: code, label: code }));

type GameFormProps = {
  defaultValues: GameFormValues;
  submitLabel: string;
  /** Throw to keep the user on the form; the error message is shown to them. */
  onSubmit: (values: GameFormValues, onProgress: ProgressCallback) => Promise<void>;
  /** Called after a successful save, once leaving the form is allowed. */
  onSuccess: () => void;
};

export function GameForm({ defaultValues, submitLabel, onSubmit, onSuccess }: GameFormProps) {
  const insets = useSafeAreaInsets();
  const navigation = useNavigation();
  const [progress, setProgress] = useState<string | null>(null);
  const allowLeave = useRef(false);

  const {
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<GameFormValues>({
    resolver: zodResolver(gameFormSchema),
    defaultValues,
  });

  const format = useWatch({ control, name: 'format' });

  // Ask before throwing away unsaved changes.
  useEffect(() => {
    return navigation.addListener('beforeRemove', (event) => {
      if (allowLeave.current || !isDirty || isSubmitting) return;
      event.preventDefault();
      void confirm({
        title: 'Discard changes?',
        message: 'Your changes to this game have not been saved.',
        confirmLabel: 'Discard',
        destructive: true,
      }).then((discard) => {
        if (discard) navigation.dispatch(event.data.action);
      });
    });
  }, [navigation, isDirty, isSubmitting]);

  const save = async (values: GameFormValues) => {
    try {
      await onSubmit(values, setProgress);
    } catch (error) {
      showMessage('Could not save the game', errorMessage(error));
      return;
    } finally {
      setProgress(null);
    }
    allowLeave.current = true;
    onSuccess();
  };
  const submit = () => handleSubmit(save)();

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'android' ? 'height' : undefined}>
      <Stack.Screen
        options={{
          headerRight: () => (
            <HeaderTextButton title="Save" onPress={() => void submit()} disabled={isSubmitting} />
          ),
        }}
      />
      <ScrollView
        style={styles.flex}
        contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + spacing.xxl }]}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="interactive"
        automaticallyAdjustKeyboardInsets>
        <Section title="Photos">
          <Controller
            control={control}
            name="images"
            render={({ field }) => <ImagesField value={field.value} onChange={field.onChange} />}
          />
          {errors.images && (
            <AppText variant="caption" tone="danger">
              {errors.images.message}
            </AppText>
          )}
        </Section>

        <Section title="Game">
          <Controller
            control={control}
            name="title"
            render={({ field }) => (
              <TextField
                label="Title *"
                placeholder="e.g. The Legend of Zelda: Ocarina of Time"
                value={field.value}
                onChangeText={field.onChange}
                onBlur={field.onBlur}
                error={errors.title?.message}
                autoCapitalize="words"
                returnKeyType="next"
              />
            )}
          />
          <Field label="Platform *">
            <Controller
              control={control}
              name="platformId"
              render={({ field }) => (
                <PlatformField
                  value={field.value || null}
                  onChange={field.onChange}
                  error={errors.platformId?.message}
                />
              )}
            />
          </Field>
          <Controller
            control={control}
            name="edition"
            render={({ field }) => (
              <TextField
                label="Edition"
                placeholder="e.g. Collector's Edition, Platinum, Player's Choice"
                value={field.value}
                onChangeText={field.onChange}
                onBlur={field.onBlur}
                error={errors.edition?.message}
              />
            )}
          />
          <Field label="Format">
            <Controller
              control={control}
              name="format"
              render={({ field }) => (
                <OptionChips
                  options={FORMATS}
                  value={field.value}
                  onChange={(value) => value && field.onChange(value)}
                  allowDeselect={false}
                />
              )}
            />
          </Field>
        </Section>

        <Section title="Your copy">
          <Field label="Region">
            <Controller
              control={control}
              name="region"
              render={({ field }) => (
                <OptionChips options={REGIONS} value={field.value} onChange={field.onChange} />
              )}
            />
          </Field>
          {format === 'physical' && (
            <>
              <Field label="Condition">
                <Controller
                  control={control}
                  name="condition"
                  render={({ field }) => (
                    <OptionChips
                      options={CONDITIONS}
                      value={field.value}
                      onChange={field.onChange}
                    />
                  )}
                />
              </Field>
              <Field label="Completeness">
                <Controller
                  control={control}
                  name="completeness"
                  render={({ field }) => (
                    <OptionChips
                      options={COMPLETENESS}
                      value={field.value}
                      onChange={field.onChange}
                    />
                  )}
                />
              </Field>
            </>
          )}
          <Controller
            control={control}
            name="serialNumber"
            render={({ field }) => (
              <TextField
                label="Serial / product code"
                placeholder="e.g. SLES-02965 or barcode"
                value={field.value}
                onChangeText={field.onChange}
                onBlur={field.onBlur}
                error={errors.serialNumber?.message}
                autoCapitalize="characters"
                autoCorrect={false}
              />
            )}
          />
        </Section>

        <Section title="Details">
          <Field label="Genres" hint={`Pick up to ${MAX_GENRES_PER_GAME}.`}>
            <Controller
              control={control}
              name="genres"
              render={({ field }) => (
                <OptionChips
                  multiple
                  options={GENRE_OPTIONS}
                  value={field.value}
                  onChange={field.onChange}
                />
              )}
            />
            {errors.genres && (
              <AppText variant="caption" tone="danger">
                {errors.genres.message}
              </AppText>
            )}
          </Field>
          <View style={styles.row}>
            <View style={styles.flex}>
              <Controller
                control={control}
                name="developer"
                render={({ field }) => (
                  <TextField
                    label="Developer"
                    value={field.value}
                    onChangeText={field.onChange}
                    onBlur={field.onBlur}
                    error={errors.developer?.message}
                  />
                )}
              />
            </View>
            <View style={styles.flex}>
              <Controller
                control={control}
                name="publisher"
                render={({ field }) => (
                  <TextField
                    label="Publisher"
                    value={field.value}
                    onChangeText={field.onChange}
                    onBlur={field.onBlur}
                    error={errors.publisher?.message}
                  />
                )}
              />
            </View>
          </View>
          <Controller
            control={control}
            name="releaseYear"
            render={({ field }) => (
              <TextField
                label="Release year"
                placeholder="e.g. 1998"
                value={field.value}
                onChangeText={field.onChange}
                onBlur={field.onBlur}
                error={errors.releaseYear?.message}
                keyboardType="number-pad"
                maxLength={4}
              />
            )}
          />
        </Section>

        <Section title="Purchase">
          <Field label="Purchase date">
            <Controller
              control={control}
              name="purchaseDate"
              render={({ field }) => (
                <DateField
                  value={field.value}
                  onChange={field.onChange}
                  error={errors.purchaseDate?.message}
                />
              )}
            />
          </Field>
          <Controller
            control={control}
            name="currency"
            render={({ field: currency }) => (
              <>
                <View style={styles.row}>
                  <View style={styles.flex}>
                    <Controller
                      control={control}
                      name="purchasePrice"
                      render={({ field }) => (
                        <TextField
                          label="Price paid"
                          placeholder="0.00"
                          value={field.value}
                          onChangeText={field.onChange}
                          onBlur={field.onBlur}
                          error={errors.purchasePrice?.message}
                          keyboardType="decimal-pad"
                          suffix={currency.value}
                        />
                      )}
                    />
                  </View>
                  <View style={styles.flex}>
                    <Controller
                      control={control}
                      name="currentValue"
                      render={({ field }) => (
                        <TextField
                          label="Current value"
                          placeholder="0.00"
                          value={field.value}
                          onChangeText={field.onChange}
                          onBlur={field.onBlur}
                          error={errors.currentValue?.message}
                          keyboardType="decimal-pad"
                          suffix={currency.value}
                        />
                      )}
                    />
                  </View>
                </View>
                <Field label="Currency">
                  <OptionChips
                    options={CURRENCY_OPTIONS}
                    value={currency.value}
                    onChange={(value) => value && currency.onChange(value)}
                    allowDeselect={false}
                  />
                </Field>
              </>
            )}
          />
          <Controller
            control={control}
            name="purchasedFrom"
            render={({ field }) => (
              <TextField
                label="Bought from"
                placeholder="e.g. eBay, local retro shop, flea market"
                value={field.value}
                onChangeText={field.onChange}
                onBlur={field.onBlur}
                error={errors.purchasedFrom?.message}
              />
            )}
          />
        </Section>

        <Section title="Personal">
          <Field label="Play status">
            <Controller
              control={control}
              name="playStatus"
              render={({ field }) => (
                <OptionChips
                  options={PLAY_STATUSES}
                  value={field.value}
                  onChange={(value) => value && field.onChange(value)}
                  allowDeselect={false}
                />
              )}
            />
          </Field>
          <Field label="My rating">
            <Controller
              control={control}
              name="rating"
              render={({ field }) => (
                <RatingStars value={field.value} onChange={field.onChange} size={30} />
              )}
            />
          </Field>
          <Controller
            control={control}
            name="isFavorite"
            render={({ field }) => (
              <ToggleRow
                label="Favorite"
                description="A highlight of your collection"
                value={field.value}
                onChange={field.onChange}
              />
            )}
          />
          <Controller
            control={control}
            name="notes"
            render={({ field }) => (
              <TextField
                label="Notes"
                placeholder="Anything worth remembering: inserts, save file, flaws…"
                value={field.value}
                onChangeText={field.onChange}
                onBlur={field.onBlur}
                error={errors.notes?.message}
                multiline
              />
            )}
          />
        </Section>

        {Object.keys(errors).length > 0 && (
          <AppText tone="danger" align="center">
            Please fix the highlighted fields.
          </AppText>
        )}
        <Button
          title={progress ?? submitLabel}
          loading={isSubmitting && !progress}
          disabled={isSubmitting}
          onPress={() => void submit()}
        />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: {
    padding: spacing.lg,
    gap: spacing.xl,
    width: '100%',
    maxWidth: MAX_CONTENT_WIDTH,
    alignSelf: 'center',
    backgroundColor: colors.background,
  },
  row: { flexDirection: 'row', gap: spacing.md },
});
