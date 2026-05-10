import React, { useState, useEffect, useRef } from 'react';
import {
  View, Text, StyleSheet, StatusBar, ScrollView,
  TouchableOpacity, Animated,
} from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useReelsTracker } from './src/hooks/useReelsTracker';
import { StatsDashboard } from './src/components/StatsDashboard';
import { ReelHistory } from './src/components/ReelHistory';
import { FeatureToggles } from './src/components/FeatureToggles';

type Tab = 'dashboard' | 'history' | 'settings';

const TAB_CONFIG: { key: Tab; icon: string; label: string }[] = [
  { key: 'dashboard', icon: 'view-dashboard-outline', label: 'Stats' },
  { key: 'history', icon: 'history', label: 'History' },
  { key: 'settings', icon: 'tune-variant', label: 'Config' },
];

function AppContent() {
  const insets = useSafeAreaInsets();
  const tracker = useReelsTracker();
  const [tab, setTab] = useState<Tab>('dashboard');
  const [toggleValues, setToggleValues] = useState<Record<string, boolean>>({});
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const fadeAnim = useRef(new Animated.Value(0)).current;

  // Entry fade
  useEffect(() => {
    Animated.timing(fadeAnim, {
      toValue: 1, duration: 600, useNativeDriver: true,
    }).start();
  }, []);

  // Pulse for live indicator
  useEffect(() => {
    if (tracker.status.isInReelsView) {
      const loop = Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, { toValue: 0.3, duration: 900, useNativeDriver: true }),
          Animated.timing(pulseAnim, { toValue: 1, duration: 900, useNativeDriver: true }),
        ]),
      );
      loop.start();
      return () => loop.stop();
    } else {
      pulseAnim.setValue(1);
    }
  }, [tracker.status.isInReelsView]);

  const handleToggle = async (key: string, value: boolean) => {
    setToggleValues(prev => ({ ...prev, [key]: value }));
    await tracker.setToggle(key, value);
  };

  const statusColor = !tracker.isEnabled
    ? '#FF6B6B'
    : tracker.status.isInReelsView
      ? '#55EFC4'
      : '#FFEAA7';

  const statusLabel = !tracker.isEnabled
    ? 'Disabled'
    : tracker.status.isInReelsView
      ? 'TRACKING'
      : 'Standby';

  return (
    <Animated.View style={[styles.container, { paddingTop: insets.top, opacity: fadeAnim }]}>

      {/* ── Header ─────────────────────────────────────── */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <View style={styles.logoRow}>
            <Icon name="brain" size={24} color="#A29BFE" />
            <Text style={styles.logo}>ScrollMind</Text>
          </View>
          <Text style={styles.tagline}>Reels awareness engine</Text>
        </View>
        <TouchableOpacity
          style={[styles.statusPill, { borderColor: statusColor + '44' }]}
          onPress={!tracker.isEnabled ? tracker.openSettings : undefined}
          activeOpacity={!tracker.isEnabled ? 0.7 : 1}
        >
          <Animated.View style={[styles.statusDot, {
            backgroundColor: statusColor,
            opacity: tracker.status.isInReelsView ? pulseAnim : 1,
          }]} />
          <Text style={[styles.statusLabel, { color: statusColor }]}>{statusLabel}</Text>
        </TouchableOpacity>
      </View>

      {/* ── Service Warning ────────────────────────────── */}
      {!tracker.isEnabled && (
        <TouchableOpacity style={styles.warning} onPress={tracker.openSettings} activeOpacity={0.8}>
          <Icon name="shield-alert-outline" size={20} color="#FF6B6B" />
          <View style={styles.warningTextWrap}>
            <Text style={styles.warningTitle}>Enable Accessibility Service</Text>
            <Text style={styles.warningDesc}>Required to track Instagram Reels</Text>
          </View>
          <Icon name="chevron-right" size={20} color="#FF6B6B55" />
        </TouchableOpacity>
      )}

      {/* ── Live Event ─────────────────────────────────── */}
      {tracker.liveEvent && tracker.liveEvent.name === 'scroll' && (
        <View style={styles.liveBanner}>
          <Icon name="play-circle-outline" size={16} color="#55EFC4" />
          <Text style={styles.liveText} numberOfLines={1}>
            Reel #{tracker.liveEvent.data.reelNumber} — @{tracker.liveEvent.data.username}
          </Text>
        </View>
      )}

      {/* ── Tab Bar ────────────────────────────────────── */}
      <View style={styles.tabBar}>
        {TAB_CONFIG.map((t) => {
          const active = tab === t.key;
          return (
            <TouchableOpacity
              key={t.key}
              style={[styles.tab, active && styles.tabActive]}
              onPress={() => setTab(t.key)}
              activeOpacity={0.7}
            >
              <Icon
                name={t.icon}
                size={18}
                color={active ? '#F0F0F5' : '#55556E'}
              />
              <Text style={[styles.tabText, active && styles.tabTextActive]}>
                {t.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* ── Content ────────────────────────────────────── */}
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {tab === 'dashboard' && (
          <View style={styles.section}>
            <StatsDashboard stats={tracker.stats} />
          </View>
        )}

        {tab === 'history' && (
          <View style={styles.section}>
            <ReelHistory
              reels={tracker.recentReels}
              onClearHistory={tracker.clearHistory}
            />
          </View>
        )}

        {tab === 'settings' && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Feature Toggles</Text>
            <FeatureToggles values={toggleValues} onToggle={handleToggle} />

            <View style={{ height: 24 }} />

            <Text style={styles.sectionTitle}>About Service</Text>
            <View style={styles.infoCard}>
              <InfoRow icon="package-variant" label="Package" value="com.scrollmind.tracker" />
              <InfoRow icon="instagram" label="Target" value="com.instagram.android" />
              <InfoRow icon="antenna" label="Events" value="Scroll, Click, Window" />
              <InfoRow icon="database-outline" label="Storage" value="Local SQLite" />
            </View>
          </View>
        )}

        <View style={{ height: 80 }} />
      </ScrollView>
    </Animated.View>
  );
}

function InfoRow({ icon, label, value }: { icon: string; label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <Icon name={icon} size={16} color="#55556E" style={{ marginRight: 10 }} />
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" backgroundColor="#0B0B1A" />
      <AppContent />
    </SafeAreaProvider>
  );
}

// ══════════════════════════════════════════════════════════════════════════════
//  STYLES — Dark, minimal, intentional
// ══════════════════════════════════════════════════════════════════════════════

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0B0B1A',
  },

  // Header
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 16,
  },
  headerLeft: {},
  logoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  logo: {
    fontSize: 24,
    fontWeight: '900',
    color: '#F0F0F5',
    letterSpacing: -0.8,
  },
  tagline: {
    fontSize: 11,
    color: '#3D3D56',
    marginTop: 2,
    marginLeft: 32,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },

  // Status
  statusPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#141425',
    borderRadius: 20,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderWidth: 1,
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    marginRight: 6,
  },
  statusLabel: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.8,
  },

  // Warning
  warning: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1A1018',
    borderRadius: 14,
    marginHorizontal: 16,
    marginBottom: 10,
    padding: 14,
    borderWidth: 1,
    borderColor: '#FF6B6B22',
    gap: 12,
  },
  warningTextWrap: { flex: 1 },
  warningTitle: { color: '#FF6B6B', fontWeight: '700', fontSize: 13 },
  warningDesc: { color: '#6B4545', fontSize: 11, marginTop: 2 },

  // Live banner
  liveBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#0D1A15',
    borderRadius: 10,
    marginHorizontal: 16,
    marginBottom: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: '#55EFC422',
    gap: 8,
  },
  liveText: {
    color: '#55EFC4',
    fontSize: 12,
    fontWeight: '600',
    flex: 1,
  },

  // Tab bar
  tabBar: {
    flexDirection: 'row',
    marginHorizontal: 16,
    backgroundColor: '#0E0E20',
    borderRadius: 14,
    padding: 4,
    marginBottom: 6,
  },
  tab: {
    flex: 1,
    flexDirection: 'row',
    paddingVertical: 10,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 11,
    gap: 5,
  },
  tabActive: {
    backgroundColor: '#A29BFE',
  },
  tabText: {
    color: '#55556E',
    fontSize: 12,
    fontWeight: '700',
  },
  tabTextActive: {
    color: '#F0F0F5',
  },

  // Content
  scroll: { flex: 1 },
  scrollContent: { paddingTop: 12 },
  section: {},

  // Settings
  sectionTitle: {
    color: '#55556E',
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    paddingHorizontal: 16,
    marginBottom: 12,
  },
  infoCard: {
    backgroundColor: '#141425',
    borderRadius: 14,
    paddingVertical: 6,
    paddingHorizontal: 14,
    marginHorizontal: 16,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#1A1A30',
  },
  infoLabel: {
    color: '#55556E',
    fontSize: 12,
    fontWeight: '600',
    width: 70,
  },
  infoValue: {
    color: '#7B7B9E',
    fontSize: 12,
    flex: 1,
  },
});
