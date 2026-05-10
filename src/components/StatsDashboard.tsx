import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { formatDuration, formatNumber } from '../utils/format';

interface StatsCardProps {
  label: string;
  value: string | number;
  iconName: string;
  iconColor: string;
  accent: string;
  subtitle?: string;
  isLarge?: boolean;
}

function StatsCard({ label, value, iconName, iconColor, accent, subtitle, isLarge }: StatsCardProps) {
  return (
    <View style={[styles.card, isLarge && styles.cardLarge]}>
      <View style={[styles.glow, { backgroundColor: accent + '10' }]} />
      <View style={styles.cardHeader}>
        <View style={[styles.iconBox, { borderColor: accent + '33' }]}>
          <Icon name={iconName} size={18} color={iconColor} />
        </View>
        <Text style={styles.label}>{label}</Text>
      </View>
      <Text style={[styles.value, isLarge && styles.valueLarge]}>{value}</Text>
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      <View style={[styles.accentBar, { backgroundColor: accent }]} />
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
    <View style={styles.container}>
      <View style={styles.mainRow}>
        <StatsCard
          isLarge
          iconName="lightning-bolt"
          iconColor="#A29BFE"
          accent="#A29BFE"
          label="Sessions Today"
          value={formatNumber(stats.todayReels)}
          subtitle={formatDuration(stats.todayWatchTimeMs) + ' total focus'}
        />
      </View>
      
      <View style={styles.grid}>
        <StatsCard
          iconName="chart-box-outline"
          iconColor="#55EFC4"
          accent="#55EFC4"
          label="All Time"
          value={formatNumber(stats.totalReels)}
        />
        <StatsCard
          iconName="timer-sand"
          iconColor="#FFEAA7"
          accent="#FFEAA7"
          label="Avg Tempo"
          value={formatDuration(stats.avgWatchTimeMs)}
        />
        <StatsCard
          iconName="bullseye-arrow"
          iconColor="#81ECEC"
          accent="#81ECEC"
          label="Retention"
          value={Math.round(stats.avgCompletionPercent || 0) + '%'}
        />
        <StatsCard
          iconName="heart-flash"
          iconColor="#FD79A8"
          accent="#FD79A8"
          label="Engaged"
          value={formatNumber(stats.totalLikes)}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingBottom: 20,
  },
  mainRow: {
    marginBottom: 12,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 12,
  },
  card: {
    backgroundColor: '#141425',
    borderRadius: 20,
    padding: 16,
    width: '48.2%',
    minHeight: 120,
    borderWidth: 1,
    borderColor: '#1A1A30',
    overflow: 'hidden',
    position: 'relative',
  },
  cardLarge: {
    width: '100%',
    minHeight: 140,
    padding: 24,
  },
  glow: {
    position: 'absolute',
    top: -20,
    right: -20,
    width: 100,
    height: 100,
    borderRadius: 50,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 12,
  },
  iconBox: {
    width: 32,
    height: 32,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#0B0B1A',
  },
  value: {
    fontSize: 24,
    fontWeight: '900',
    color: '#F0F0F5',
    letterSpacing: -0.5,
  },
  valueLarge: {
    fontSize: 42,
    marginTop: -4,
  },
  label: {
    fontSize: 10,
    color: '#55556E',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    fontWeight: '800',
  },
  subtitle: {
    fontSize: 12,
    color: '#7B7B9E',
    marginTop: 6,
    fontWeight: '500',
  },
  accentBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 2,
    opacity: 0.6,
  },
});
