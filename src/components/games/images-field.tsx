import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { MAX_IMAGES_PER_GAME } from '@/constants/game-options';
import { colors, COVER_ASPECT_RATIO, radius, spacing } from '@/constants/theme';
import type { FormImage } from '@/features/games/types';
import { showMessage } from '@/lib/dialogs';

const TILE_WIDTH = 96;

type ImagesFieldProps = {
  value: FormImage[];
  onChange: (images: FormImage[]) => void;
};

let keyCounter = 0;

function toFormImages(assets: ImagePicker.ImagePickerAsset[]): FormImage[] {
  return assets.map((asset) => ({
    key: `new-${Date.now()}-${keyCounter++}`,
    kind: 'new',
    uri: asset.uri,
    width: asset.width,
    height: asset.height,
  }));
}

export function ImagesField({ value, onChange }: ImagesFieldProps) {
  const remaining = MAX_IMAGES_PER_GAME - value.length;

  const pickFromLibrary = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsMultipleSelection: true,
      selectionLimit: remaining,
      quality: 1,
    });
    if (!result.canceled) onChange([...value, ...toFormImages(result.assets.slice(0, remaining))]);
  };

  const takePhoto = async () => {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) {
      showMessage(
        'Camera access needed',
        'Allow camera access in Settings to photograph your games.',
      );
      return;
    }
    const result = await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 1 });
    if (!result.canceled) onChange([...value, ...toFormImages(result.assets)]);
  };

  const addPhotos = () => {
    if (Platform.OS === 'web') {
      void pickFromLibrary();
      return;
    }
    Alert.alert('Add photos', undefined, [
      { text: 'Take photo', onPress: () => void takePhoto() },
      { text: 'Choose from library', onPress: () => void pickFromLibrary() },
      { text: 'Cancel', style: 'cancel' },
    ]);
  };

  const remove = (key: string) => onChange(value.filter((img) => img.key !== key));

  const makeCover = (key: string) => {
    const image = value.find((img) => img.key === key);
    if (!image) return;
    onChange([image, ...value.filter((img) => img.key !== key)]);
  };

  return (
    <View style={styles.container}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.row}>
        {value.map((image, index) => {
          const uri = image.kind === 'new' ? image.uri : image.previewUrl;
          return (
            <View key={image.key} style={styles.tile}>
              {uri ? (
                <Image source={{ uri }} style={StyleSheet.absoluteFill} contentFit="cover" />
              ) : (
                <View style={styles.missing}>
                  <Ionicons name="image-outline" size={22} color={colors.textMuted} />
                </View>
              )}
              {index === 0 ? (
                <View style={styles.coverBadge}>
                  <AppText variant="caption" weight="800" tone="onPrimary">
                    Cover
                  </AppText>
                </View>
              ) : (
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="Use as cover"
                  onPress={() => makeCover(image.key)}
                  style={styles.makeCover}>
                  <AppText variant="caption" weight="700">
                    Set cover
                  </AppText>
                </Pressable>
              )}
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Remove photo"
                hitSlop={8}
                onPress={() => remove(image.key)}
                style={styles.remove}>
                <Ionicons name="close" size={14} color={colors.text} />
              </Pressable>
            </View>
          );
        })}
        {remaining > 0 && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Add photos"
            onPress={addPhotos}
            style={({ pressed }) => [styles.tile, styles.addTile, pressed && styles.pressed]}>
            <Ionicons name="camera-outline" size={26} color={colors.primary} />
            <AppText variant="caption" tone="primary" weight="700">
              Add photo
            </AppText>
          </Pressable>
        )}
      </ScrollView>
      <AppText variant="caption" tone="muted">
        {value.length === 0
          ? 'Add the cover, back, cartridge or disc, manual… The first photo is the cover.'
          : `${value.length} of ${MAX_IMAGES_PER_GAME} photos. The first photo is used as the cover.`}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.sm },
  row: { gap: spacing.sm, paddingVertical: spacing.xs },
  tile: {
    width: TILE_WIDTH,
    aspectRatio: COVER_ASPECT_RATIO,
    borderRadius: radius.md,
    overflow: 'hidden',
    backgroundColor: colors.surfaceRaised,
    borderWidth: 1,
    borderColor: colors.border,
  },
  missing: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  addTile: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
    borderStyle: 'dashed',
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  pressed: { opacity: 0.7 },
  coverBadge: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    paddingVertical: 3,
    backgroundColor: colors.primary,
  },
  makeCover: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    paddingVertical: 3,
    backgroundColor: colors.overlay,
  },
  remove: {
    position: 'absolute',
    top: 4,
    right: 4,
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.overlay,
  },
});
