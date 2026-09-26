import Ionicons from '@expo/vector-icons/Ionicons';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, router } from 'expo-router';
import { useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { StyleSheet, View, type TextInput } from 'react-native';
import { z } from 'zod';

import { AuthScreen } from '@/components/auth/auth-screen';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { TextField } from '@/components/ui/text-field';
import { colors, spacing } from '@/constants/theme';
import { authErrorMessage } from '@/lib/auth-errors';
import { supabase } from '@/lib/supabase';

const schema = z
  .object({
    email: z.string().trim().email('Enter a valid email address'),
    password: z.string().min(8, 'Use at least 8 characters'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type Values = z.infer<typeof schema>;

export default function SignUpScreen() {
  const passwordRef = useRef<TextInput>(null);
  const confirmRef = useRef<TextInput>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [confirmationSentTo, setConfirmationSentTo] = useState<string | null>(null);
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(null);
    const { data, error } = await supabase.auth.signUp({ email, password });
    if (error) {
      setFormError(authErrorMessage(error));
      return;
    }
    // With email confirmation on, Supabase hides existing accounts behind an empty identity list.
    if (data.user && data.user.identities?.length === 0) {
      setFormError('An account with this email already exists. Sign in instead.');
      return;
    }
    // No session means the project requires email confirmation first.
    if (!data.session) setConfirmationSentTo(email);
  });

  if (confirmationSentTo) {
    return (
      <AuthScreen
        title="Check your inbox"
        subtitle="One more step before you can start your library.">
        <View style={styles.confirmation}>
          <Ionicons name="mail-open-outline" size={40} color={colors.primary} />
          <AppText align="center">
            We sent a confirmation link to <AppText weight="700">{confirmationSentTo}</AppText>.
            Open it, then come back and sign in.
          </AppText>
        </View>
        <Button title="Go to sign in" onPress={() => router.replace('/sign-in')} />
      </AuthScreen>
    );
  }

  return (
    <AuthScreen
      title="Create your account"
      subtitle="Catalog every cartridge, disc and box you own.">
      <Controller
        control={control}
        name="email"
        render={({ field }) => (
          <TextField
            label="Email"
            placeholder="you@example.com"
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={errors.email?.message}
            autoCapitalize="none"
            autoComplete="email"
            keyboardType="email-address"
            textContentType="emailAddress"
            returnKeyType="next"
            onSubmitEditing={() => passwordRef.current?.focus()}
            submitBehavior="submit"
          />
        )}
      />
      <Controller
        control={control}
        name="password"
        render={({ field }) => (
          <TextField
            ref={passwordRef}
            label="Password"
            placeholder="At least 8 characters"
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={errors.password?.message}
            secureTextEntry
            autoComplete="new-password"
            textContentType="newPassword"
            returnKeyType="next"
            onSubmitEditing={() => confirmRef.current?.focus()}
            submitBehavior="submit"
          />
        )}
      />
      <Controller
        control={control}
        name="confirmPassword"
        render={({ field }) => (
          <TextField
            ref={confirmRef}
            label="Confirm password"
            placeholder="Repeat your password"
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={errors.confirmPassword?.message}
            secureTextEntry
            autoComplete="new-password"
            textContentType="newPassword"
            returnKeyType="go"
            onSubmitEditing={() => void onSubmit()}
          />
        )}
      />
      {formError && (
        <AppText tone="danger" variant="label">
          {formError}
        </AppText>
      )}
      <Button title="Create account" loading={isSubmitting} onPress={() => void onSubmit()} />
      <View style={styles.footer}>
        <AppText tone="secondary">Already have an account?</AppText>
        <Link href="/sign-in" replace>
          <AppText tone="primary" weight="700">
            Sign in
          </AppText>
        </Link>
      </View>
    </AuthScreen>
  );
}

const styles = StyleSheet.create({
  footer: { flexDirection: 'row', justifyContent: 'center', flexWrap: 'wrap', gap: spacing.xs },
  confirmation: { alignItems: 'center', gap: spacing.md },
});
