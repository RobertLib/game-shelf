import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import { useState } from 'react';
import {
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  useWindowDimensions,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/ui/app-text';
import { colors, spacing } from '@/constants/theme';
import type { GameImage } from '@/features/games/types';

type ImageGalleryProps = {
  images: GameImage[];
  height: number;
};

function pageFromEvent(event: NativeSyntheticEvent<NativeScrollEvent>, pageWidth: number) {
  return Math.round(event.nativeEvent.contentOffset.x / pageWidth);
}

export function ImageGallery({ images, height }: ImageGalleryProps) {
  const { width } = useWindowDimensions();
  const [page, setPage] = useState(0);
  const [viewerIndex, setViewerIndex] = useState<number | null>(null);

  return (
    <View style={{ height }}>
      <FlatList
        data={images}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        keyExtractor={(image) => image.id}
        onMomentumScrollEnd={(event) => setPage(pageFromEvent(event, width))}
        getItemLayout={(_, index) => ({ length: width, offset: width * index, index })}
        renderItem={({ item, index }) => (
          <Pressable
            accessibilityRole="imagebutton"
            accessibilityLabel={`Photo ${index + 1} of ${images.length}. Open full screen.`}
            onPress={() => setViewerIndex(index)}
            style={{ width, height }}>
            <Image
              source={{ uri: item.url ?? undefined, cacheKey: item.storage_path }}
              placeholder={
                item.thumbUrl ? { uri: item.thumbUrl, cacheKey: item.thumb_path } : undefined
              }
              placeholderContentFit="contain"
              style={StyleSheet.absoluteFill}
              contentFit="contain"
              transition={200}
              cachePolicy="disk"
            />
          </Pressable>
        )}
      />
      {images.length > 1 && (
        <View style={styles.dots} pointerEvents="none">
          {images.map((image, index) => (
            <View key={image.id} style={[styles.dot, index === page && styles.dotActive]} />
          ))}
        </View>
      )}
      <FullscreenViewer
        images={images}
        initialIndex={viewerIndex}
        onClose={() => setViewerIndex(null)}
      />
    </View>
  );
}

function FullscreenViewer({
  images,
  initialIndex,
  onClose,
}: {
  images: GameImage[];
  initialIndex: number | null;
  onClose: () => void;
}) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const [page, setPage] = useState(0);

  return (
    <Modal
      visible={initialIndex != null}
      animationType="fade"
      onRequestClose={onClose}
      onShow={() => setPage(initialIndex ?? 0)}
      supportedOrientations={['portrait', 'landscape']}>
      <View style={styles.viewer}>
        {initialIndex != null && (
          <FlatList
            data={images}
            horizontal
            pagingEnabled
            initialScrollIndex={initialIndex}
            getItemLayout={(_, index) => ({ length: width, offset: width * index, index })}
            showsHorizontalScrollIndicator={false}
            keyExtractor={(image) => image.id}
            onMomentumScrollEnd={(event) => setPage(pageFromEvent(event, width))}
            renderItem={({ item }) => (
              <Image
                source={{ uri: item.url ?? undefined, cacheKey: item.storage_path }}
                style={{ width, height }}
                contentFit="contain"
                cachePolicy="disk"
              />
            )}
          />
        )}
        <View style={[styles.viewerBar, { top: insets.top + spacing.sm }]}>
          <AppText variant="label" tone="secondary">
            {images.length > 1 ? `${page + 1} / ${images.length}` : ''}
          </AppText>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Close"
            onPress={onClose}
            hitSlop={10}
            style={styles.close}>
            <Ionicons name="close" size={24} color={colors.text} />
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  dots: {
    position: 'absolute',
    bottom: spacing.md,
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 6,
  },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.35)' },
  dotActive: { width: 18, backgroundColor: colors.text },
  viewer: { flex: 1, backgroundColor: '#000' },
  viewerBar: {
    position: 'absolute',
    left: spacing.lg,
    right: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  close: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
});
