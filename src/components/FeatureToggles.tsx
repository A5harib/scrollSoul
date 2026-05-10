import React from 'react';
import { View, Text, Switch, StyleSheet } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

interface Toggle {
  key: string;
  label: string;
  description: string;
  iconName: string;
  iconColor: string;
}

const TOGGLES: Toggle[] = [
  { key: 'toggle_metadata', label: 'Metadata Scraper', description: 'Grab username and caption from UI', iconName: 'tag-text-outline', iconColor: '#A29BFE' },
  { key: 'toggle_watchtime', label: 'Watch Time Tracker', description: 'Timer between scroll events', iconName: 'timer-outline', iconColor: '#55EFC4' },
  { key: 'toggle_completion', label: 'Completion Rate', description: 'Progress bar width measurement', iconName: 'chart-arc', iconColor: '#FFEAA7' },
  { key: 'toggle_engagement', label: 'Engagement Logger', description: 'Detect likes, comment taps, shares', iconName: 'lightning-bolt-outline', iconColor: '#FF6B6B' },
  { key: 'toggle_ocr', label: 'OCR Content Log', description: 'Extract on-screen text via ML Kit', iconName: 'eye-outline', iconColor: '#74B9FF' },
];

interface FeatureTogglesProps {
  values: Record<string, boolean>;
  onToggle: (key: string, value: boolean) => void;
}

export function FeatureToggles({ values, onToggle }: FeatureTogglesProps) {
  return (
    <View style={styles.container}>
      {TOGGLES.map((t) => (
        <View key={t.key} style={styles.row}>
          <View style={[styles.iconCircle, { backgroundColor: t.iconColor + '18' }]}>
            <Icon name={t.iconName} size={18} color={t.iconColor} />
          </View>
          <View style={styles.textWrap}>
            <Text style={styles.label}>{t.label}</Text>
            <Text style={styles.desc}>{t.description}</Text>
          </View>
          <Switch
            value={values[t.key] !== false}
            onValueChange={(v) => onToggle(t.key, v)}
            trackColor={{ false: '#1E1E35', true: '#A29BFE55' }}
            thumbColor={values[t.key] !== false ? '#A29BFE' : '#55556E'}
          />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingHorizontal: 16 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#141425',
    borderRadius: 14,
    padding: 14,
    marginBottom: 8,
  },
  iconCircle: {
    width: 36,
    height: 36,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  textWrap: { flex: 1 },
  label: { color: '#F0F0F5', fontWeight: '600', fontSize: 14 },
  desc: { color: '#55556E', fontSize: 11, marginTop: 2 },
});
