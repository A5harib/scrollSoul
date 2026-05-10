import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Alert } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { ReelData } from '../hooks/useReelsTracker';
import { formatDuration, timeAgo } from '../utils/format';

interface ReelHistoryProps {
  reels: ReelData[];
  onClearHistory: () => void;
}

const AVATAR_COLORS = [
  '#A29BFE', '#FD79A8', '#55EFC4', '#74B9FF',
  '#FF6B6B', '#FFEAA7', '#DFE6E9', '#81ECEC',
];

function getAvatarColor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

function ReelItem({ item }: { item: ReelData }) {
  const color = getAvatarColor(item.username || 'x');

  return (
    <View style={styles.row}>
      <View style={[styles.avatar, { backgroundColor: color + '22' }]}>
        <Text style={[styles.avatarText, { color }]}>
          {(item.username || '?')[0].toUpperCase()}
        </Text>
      </View>
      <View style={styles.content}>
        <Text style={styles.username} numberOfLines={1}>
          @{item.username || 'unknown'}
        </Text>
        {item.caption ? (
          <Text style={styles.caption} numberOfLines={2}>{item.caption}</Text>
        ) : (
          <Text style={styles.captionEmpty}>No caption captured</Text>
        )}
        <View style={styles.meta}>
          <View style={styles.metaItem}>
            <Icon name="timer-outline" size={12} color="#55556E" />
            <Text style={styles.metaText}>{formatDuration(item.watchTimeMs)}</Text>
          </View>
          <View style={styles.metaItem}>
            <Icon name="chart-arc" size={12} color="#55556E" />
            <Text style={styles.metaText}>{Math.round(item.completionPercent)}%</Text>
          </View>
          {item.liked && (
            <View style={styles.metaItem}>
              <Icon name="heart" size={12} color="#FD79A8" />
            </View>
          )}
          {item.commented && (
            <View style={styles.metaItem}>
              <Icon name="comment-text" size={12} color="#74B9FF" />
            </View>
          )}
          {item.shared && (
            <View style={styles.metaItem}>
              <Icon name="share" size={12} color="#55EFC4" />
            </View>
          )}
        </View>
      </View>
      <Text style={styles.time}>{timeAgo(item.timestamp)}</Text>
    </View>
  );
}

export function ReelHistory({ reels, onClearHistory }: ReelHistoryProps) {
  const handleClear = () => {
    Alert.alert(
      'Clear History',
      'This will permanently delete all tracked reels. Continue?',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete All', style: 'destructive', onPress: onClearHistory },
      ],
    );
  };

  if (reels.length === 0) {
    return (
      <View style={styles.empty}>
        <Icon name="inbox-outline" size={48} color="#35354E" />
        <Text style={styles.emptyText}>No reels tracked yet</Text>
        <Text style={styles.emptyHint}>Open Instagram Reels to start</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Header with delete button */}
      <View style={styles.historyHeader}>
        <Text style={styles.historyCount}>{reels.length} reels tracked</Text>
        <TouchableOpacity style={styles.clearBtn} onPress={handleClear}>
          <Icon name="delete-outline" size={16} color="#FF6B6B" />
          <Text style={styles.clearText}>Clear</Text>
        </TouchableOpacity>
      </View>

      {/* Render items as plain Views — no FlatList to avoid nesting crash */}
      {reels.map((item) => (
        <ReelItem key={String(item.id)} item={item} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingHorizontal: 16, paddingBottom: 40 },
  historyHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  historyCount: {
    color: '#55556E',
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  clearBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FF6B6B14',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
    gap: 4,
  },
  clearText: {
    color: '#FF6B6B',
    fontSize: 12,
    fontWeight: '700',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#141425',
    borderRadius: 14,
    padding: 14,
    marginBottom: 8,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  avatarText: { fontWeight: '800', fontSize: 16 },
  content: { flex: 1 },
  username: { color: '#F0F0F5', fontWeight: '700', fontSize: 14 },
  caption: { color: '#7B7B9E', fontSize: 12, marginTop: 3, lineHeight: 16 },
  captionEmpty: { color: '#3D3D56', fontSize: 12, marginTop: 3, fontStyle: 'italic' },
  meta: { flexDirection: 'row', gap: 10, marginTop: 6, alignItems: 'center' },
  metaItem: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  metaText: { color: '#55556E', fontSize: 11 },
  time: { color: '#3D3D56', fontSize: 11, marginLeft: 8 },
  empty: { alignItems: 'center', paddingVertical: 60 },
  emptyText: { color: '#55556E', fontSize: 16, fontWeight: '600', marginTop: 12 },
  emptyHint: { color: '#3D3D56', fontSize: 13, marginTop: 4 },
});
