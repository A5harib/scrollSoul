import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { formatDuration, formatNumber } from '../utils/format';

interface StatsCardProps {
  label: string;
  value: string | number;
  iconName: string;
  iconColor: string;
  accent: string;
  subtitle?: string;
}

function StatsCard({ label, value, iconName, iconColor, accent, subtitle }: StatsCardProps) {
  return (
    <View style={[styles.card, { borderLeftColor: accent }]}>
      <View style={[styles.iconCircle, { backgroundColor: accent + '18' }]}>
        <Icon name={iconName} size={18} color={iconColor} />
      </View>
      <Text style={styles.value}>{value}</Text>
      <Text style={styles.label}>{label}</Text>
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
    </View>
  );
}

interface StatsDashboardProps {
  stats: {
    totalReels: number;
    totalWatchTimeMs: number;
    avgWatchTimeMs: number;
    avgCompletionPercent: number;
    totalLikes: number;
    totalComments: number;
    todayReels: number;
    todayWatchTimeMs: number;
  };
}

export function StatsDashboard({ stats }: StatsDashboardProps) {
  return (
    <View style={styles.grid}>
      <StatsCard
        iconName="cellphone-play"
        iconColor="#FF6B6B"
        accent="#FF6B6B"
        label="Today"
        value={formatNumber(stats.todayReels)}
        subtitle={formatDuration(stats.todayWatchTimeMs) + ' watched'}
      />
      <StatsCard
        iconName="movie-open-outline"
        iconColor="#A29BFE"
        accent="#A29BFE"
        label="All Reels"
        value={formatNumber(stats.totalReels)}
      />
      <StatsCard
        iconName="timer-outline"
        iconColor="#55EFC4"
        accent="#55EFC4"
        label="Avg Watch"
        value={formatDuration(stats.avgWatchTimeMs)}
      />
      <StatsCard
        iconName="chart-arc"
        iconColor="#FFEAA7"
        accent="#FFEAA7"
        label="Completion"
        value={Math.round(stats.avgCompletionPercent || 0) + '%'}
      />
      <StatsCard
        iconName="heart-outline"
        iconColor="#FD79A8"
        accent="#FD79A8"
        label="Liked Reels"
        value={formatNumber(stats.totalLikes)}
      />
      <StatsCard
        iconName="comment-text-outline"
        iconColor="#74B9FF"
        accent="#74B9FF"
        label="Comment Taps"
        value={formatNumber(stats.totalComments)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    gap: 10,
  },
  card: {
    backgroundColor: '#141425',
    borderRadius: 16,
    padding: 16,
    width: '47.5%',
    borderLeftWidth: 3,
    minHeight: 110,
    justifyContent: 'center',
  },
  iconCircle: {
    width: 32,
    height: 32,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 10,
  },
  value: {
    fontSize: 28,
    fontWeight: '800',
    color: '#F0F0F5',
    letterSpacing: -0.5,
  },
  label: {
    fontSize: 11,
    color: '#7B7B9E',
    marginTop: 3,
    textTransform: 'uppercase',
    letterSpacing: 1.2,
    fontWeight: '600',
  },
  subtitle: {
    fontSize: 11,
    color: '#55556E',
    marginTop: 4,
  },
});
