import React, { useState, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Alert, Image, LayoutAnimation, Platform, UIManager, FlatList } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { ReelData } from '../hooks/useReelsTracker';
import { formatDuration, timeAgo } from '../utils/format';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

interface ReelHistoryProps {
  reels: ReelData[];
  onClearHistory: () => void;
}

const AVATAR_COLORS = [
  '#A67C6D', '#D9C5B2', '#333333', '#A67C6D',
  '#D9C5B2', '#333333', '#A67C6D', '#D9C5B2',
];

function getAvatarColor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

// Optimized ReelItem with Memo
const ReelItem = React.memo(({ item, isExpanded, onToggle }: { item: ReelData; isExpanded: boolean; onToggle: () => void }) => {
  const color = useMemo(() => getAvatarColor(item.username || 'x'), [item.username]);

  return (
    <TouchableOpacity 
      style={[styles.row, isExpanded && styles.rowExpanded]} 
      onPress={onToggle}
      activeOpacity={0.9}
    >
      <View style={styles.rowMain}>
        {item.thumbnailPath ? (
          <Image source={{ uri: item.thumbnailPath }} style={styles.thumbnail} />
        ) : (
          <View style={[styles.avatar, { backgroundColor: color + '22' }]}>
            <Text style={[styles.avatarText, { color }]}>
              {(item.username || '?')[0].toUpperCase()}
            </Text>
          </View>
        )}
        
        <View style={styles.content}>
          <View style={styles.userRow}>
            <Text style={styles.username} numberOfLines={1}>
              @{item.username || 'unknown'}
            </Text>
            <Text style={styles.time}>{timeAgo(item.timestamp)}</Text>
          </View>
          
          <Text style={styles.captionPreview} numberOfLines={isExpanded ? 0 : 1}>
            {item.caption || 'No caption captured'}
          </Text>

          <View style={styles.meta}>
            <View style={styles.metaItem}>
              <Icon name="timer-outline" size={12} color="#55556E" />
              <Text style={styles.metaText}>{formatDuration(item.watchTimeMs)}</Text>
            </View>
            <View style={styles.metaItem}>
              <Icon name="chart-arc" size={12} color="#55556E" />
              <Text style={styles.metaText}>{Math.round(item.completionPercent)}%</Text>
            </View>
            {item.liked && <Icon name="heart" size={12} color="#FD79A8" />}
            {item.commented && <Icon name="comment-text" size={12} color="#74B9FF" />}
            {item.shared && <Icon name="share" size={12} color="#55EFC4" />}
          </View>
        </View>

        <Icon 
          name={isExpanded ? 'chevron-up' : 'chevron-down'} 
          size={20} 
          color="#A67C6D" 
          style={styles.chevron}
        />
      </View>

      {isExpanded && (
        <View style={styles.expandedContent}>
          <View style={styles.divider} />
          <Text style={styles.fullCaption}>
            {item.caption || 'No metadata description was available for this reel.'}
          </Text>
          {item.ocrText && (
            <View style={styles.ocrSection}>
              <Text style={styles.ocrLabel}>RAW SCAN</Text>
              <Text style={styles.ocrText} numberOfLines={4}>{item.ocrText}</Text>
            </View>
          )}
        </View>
      )}
    </TouchableOpacity>
  );
});

export function ReelHistory({ reels, onClearHistory }: ReelHistoryProps) {
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const toggleExpand = useCallback((id: number) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpandedId(prev => prev === id ? null : id);
  }, []);

  const handleClear = () => {
    Alert.alert(
      'Purge Soul',
      'This will permanently delete all tracked reels from your history. Continue?',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Purge All', style: 'destructive', onPress: onClearHistory },
      ],
    );
  };

  const renderHeader = () => (
    <View style={styles.historyHeader}>
      <Text style={styles.historyCount}>{reels.length} captured items</Text>
      <TouchableOpacity style={styles.clearBtn} onPress={handleClear}>
        <Icon name="trash-can-outline" size={14} color="#FF6B6B" />
        <Text style={styles.clearText}>Purge</Text>
      </TouchableOpacity>
    </View>
  );

  const renderItem = ({ item }: { item: ReelData }) => (
    <ReelItem 
      item={item} 
      isExpanded={expandedId === item.id}
      onToggle={() => toggleExpand(item.id)}
    />
  );

  if (reels.length === 0) {
    return (
      <View style={styles.empty}>
        <View style={styles.emptyIconCircle}>
          <Icon name="ghost" size={40} color="#A29BFE" />
        </View>
        <Text style={styles.emptyText}>Nothing in the void</Text>
        <Text style={styles.emptyHint}>Scroll some reels to populate your history</Text>
      </View>
    );
  }

  return (
    <FlatList
      data={reels}
      keyExtractor={(item) => String(item.id)}
      renderItem={renderItem}
      ListHeaderComponent={renderHeader}
      contentContainerStyle={styles.listContent}
      initialNumToRender={10}
      maxToRenderPerBatch={5}
      windowSize={5}
      removeClippedSubviews={true}
      showsVerticalScrollIndicator={false}
    />
  );
}

const styles = StyleSheet.create({
  listContent: { paddingHorizontal: 16, paddingBottom: 120 },
  historyHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
    marginTop: 8,
  },
  historyCount: {
    color: '#333333',
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  clearBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FDFCF9',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    gap: 4,
    borderWidth: 1.5,
    borderColor: '#333333',
  },
  clearText: {
    color: '#333333',
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  row: {
    backgroundColor: '#FDFCF9',
    borderRadius: 0,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#333333',
    overflow: 'hidden',
  },
  rowExpanded: {
    borderColor: '#A67C6D',
    borderWidth: 1.5,
  },
  rowMain: {
    flexDirection: 'row',
    padding: 12,
    alignItems: 'center',
  },
  thumbnail: {
    width: 48,
    height: 48,
    borderRadius: 0,
    marginRight: 12,
    backgroundColor: '#F5F0E6',
    borderWidth: 1,
    borderColor: '#333333',
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 0,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
    borderWidth: 1,
    borderColor: '#333333',
  },
  avatarText: { fontWeight: '900', fontSize: 18 },
  content: { flex: 1 },
  userRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  username: { 
    color: '#333333', 
    fontWeight: '800', 
    fontSize: 14,
    letterSpacing: -0.3,
    fontFamily: 'serif',
  },
  captionPreview: { 
    color: '#A67C6D', 
    fontSize: 12, 
    marginTop: 2, 
    lineHeight: 16,
  },
  meta: { 
    flexDirection: 'row', 
    gap: 12, 
    marginTop: 8, 
    alignItems: 'center' 
  },
  metaItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  metaText: { color: '#333333', fontSize: 11, fontWeight: '700' },
  time: { color: '#A67C6D', fontSize: 10, fontWeight: '700' },
  chevron: { marginLeft: 8 },
  
  expandedContent: {
    paddingHorizontal: 12,
    paddingBottom: 16,
    paddingTop: 4,
  },
  divider: {
    height: 1,
    backgroundColor: '#33333322',
    marginBottom: 12,
  },
  fullCaption: {
    color: '#333333',
    fontSize: 13,
    lineHeight: 20,
    fontWeight: '400',
  },
  ocrSection: {
    marginTop: 12,
    backgroundColor: '#F5F0E6',
    padding: 10,
    borderRadius: 0,
    borderWidth: 1,
    borderColor: '#333333',
  },
  ocrLabel: {
    color: '#A67C6D',
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1,
    marginBottom: 4,
  },
  ocrText: {
    color: '#333333',
    fontSize: 11,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  
  empty: { 
    alignItems: 'center', 
    paddingVertical: 80,
    opacity: 0.8,
  },
  emptyIconCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#A67C6D10',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#A67C6D33',
  },
  emptyText: { color: '#333333', fontSize: 18, fontWeight: '800', marginTop: 12, fontFamily: 'serif' },
  emptyHint: { color: '#A67C6D', fontSize: 13, marginTop: 6, textAlign: 'center', paddingHorizontal: 40 },
});
