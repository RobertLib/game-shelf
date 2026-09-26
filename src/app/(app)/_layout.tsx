import { Stack } from 'expo-router';

import { colors } from '@/constants/theme';

export default function AppLayout() {
  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: colors.background },
        headerTintColor: colors.text,
        headerTitleStyle: { fontWeight: '700' },
        headerShadowVisible: false,
        headerBackButtonDisplayMode: 'minimal',
        contentStyle: { backgroundColor: colors.background },
      }}>
      <Stack.Screen name="index" options={{ title: 'Library' }} />
      <Stack.Screen name="filters" options={{ title: 'Filters', presentation: 'modal' }} />
      <Stack.Screen name="account" options={{ title: 'Account' }} />
      <Stack.Screen name="games/new" options={{ title: 'Add game' }} />
      <Stack.Screen name="games/[id]/index" options={{ title: '' }} />
      <Stack.Screen name="games/[id]/edit" options={{ title: 'Edit game' }} />
    </Stack>
  );
}
