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
  { key: 'toggle_metadata', label: 'Metadata Scraper', description: 'Grab username and caption from UI', iconName: 'tag-text-outline', iconColor: '#A67C6D' },
  { key: 'toggle_watchtime', label: 'Watch Time Tracker', description: 'Timer between scroll events', iconName: 'timer-outline', iconColor: '#A67C6D' },
  { key: 'toggle_completion', label: 'Completion Rate', description: 'Progress bar width measurement', iconName: 'chart-arc', iconColor: '#A67C6D' },
  { key: 'toggle_engagement', label: 'Engagement Logger', description: 'Detect likes, comment taps, shares', iconName: 'lightning-bolt-outline', iconColor: '#A67C6D' },
  { key: 'toggle_ocr', label: 'OCR Content Log', description: 'Extract on-screen text via ML Kit', iconName: 'eye-outline', iconColor: '#A67C6D' },
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
            trackColor={{ false: '#D9C5B2', true: '#A67C6D55' }}
            thumbColor={values[t.key] !== false ? '#A67C6D' : '#333333'}
          />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingHorizontal: 0 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FDFCF9',
    borderRadius: 0,
    padding: 14,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#333333',
  },
  iconCircle: {
    width: 36,
    height: 36,
    borderRadius: 0,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
    borderWidth: 1,
    borderColor: '#A67C6D33',
  },
  textWrap: { flex: 1 },
  label: { color: '#333333', fontWeight: '800', fontSize: 14, fontFamily: 'serif' },
  desc: { color: '#A67C6D', fontSize: 11, marginTop: 2 },
});
