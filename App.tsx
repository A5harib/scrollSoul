import React, { useState, useEffect, useRef } from 'react';
import {
  View, Text, StyleSheet, StatusBar, ScrollView,
  TouchableOpacity, Animated, Easing
} from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useReelsTracker } from './src/hooks/useReelsTracker';
import { StatsDashboard } from './src/components/StatsDashboard';
import { ReelHistory } from './src/components/ReelHistory';
import { FeatureToggles } from './src/components/FeatureToggles';

type Tab = 'dashboard' | 'history' | 'settings';

const TAB_CONFIG: { key: Tab; icon: string; label: string }[] = [
  { key: 'dashboard', icon: 'orbit', label: 'Monitor' },
  { key: 'history', icon: 'archive-eye-outline', label: 'Archive' },
  { key: 'settings', icon: 'cog-outline', label: 'Nodes' },
];

function AppContent() {
  const insets = useSafeAreaInsets();
  const tracker = useReelsTracker();
  const [tab, setTab] = useState<Tab>('dashboard');
  const [toggleValues, setToggleValues] = useState<Record<string, boolean>>({});
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(20)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fadeAnim, { toValue: 1, duration: 800, useNativeDriver: true }),
      Animated.spring(slideAnim, { toValue: 0, tension: 20, friction: 7, useNativeDriver: true })
    ]).start();
  }, []);

  useEffect(() => {
    if (tracker.status.isInReelsView) {
      const loop = Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, { toValue: 0.4, duration: 1000, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
          Animated.timing(pulseAnim, { toValue: 1, duration: 1000, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
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

  const statusColor = !tracker.isEnabled ? '#FF6B6B' : tracker.status.isInReelsView ? '#A29BFE' : '#55556E';
  const statusLabel = !tracker.isEnabled ? 'OFFLINE' : tracker.status.isInReelsView ? 'ACTIVE' : 'IDLE';

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" translucent backgroundColor="transparent" />
      
      <Animated.View style={[styles.inner, { paddingTop: insets.top, opacity: fadeAnim, transform: [{ translateY: slideAnim }] }]}>
        
        {/* ── Header ─────────────────────────────────────── */}
        <View style={styles.header}>
          <View>
            <View style={styles.logoRow}>
              <Text style={styles.logo}>SCROLL<Text style={styles.logoAccent}>SOUL</Text></Text>
            </View>
            <Text style={styles.tagline}>NEURAL TRACKING SYSTEM</Text>
          </View>
          
          <TouchableOpacity 
            onPress={tracker.openSettings}
            style={[styles.statusWrapper, { borderColor: statusColor + '33' }]}
          >
            <Animated.View style={[styles.statusPulse, { backgroundColor: statusColor, opacity: tracker.status.isInReelsView ? pulseAnim : 0.2 }]} />
            <Text style={[styles.statusText, { color: statusColor }]}>{statusLabel}</Text>
          </TouchableOpacity>
        </View>

        {/* ── Tabs ───────────────────────────────────────── */}
        <View style={styles.tabBar}>
          {TAB_CONFIG.map((t) => {
            const active = tab === t.key;
            return (
              <TouchableOpacity
                key={t.key}
                style={[styles.tab, active && styles.tabActive]}
                onPress={() => setTab(t.key)}
                activeOpacity={0.8}
              >
                <Icon name={t.icon} size={20} color={active ? '#A29BFE' : '#3D3D56'} />
                {active && <Text style={styles.tabTextActive}>{t.label}</Text>}
              </TouchableOpacity>
            );
          })}
        </View>

        {/* ── Warning ────────────────────────────── */}
        {!tracker.isEnabled && (
          <TouchableOpacity style={styles.criticalWarning} onPress={tracker.openSettings}>
            <View style={styles.warningIcon}>
              <Icon name="alert-decagram" size={20} color="#FF6B6B" />
            </View>
            <View style={{flex:1}}>
              <Text style={styles.warningTitle}>PERMISSIONS_REQUIRED</Text>
              <Text style={styles.warningDesc}>Accessibility bridge not established</Text>
            </View>
            <Icon name="chevron-right" size={20} color="#FF6B6B44" />
          </TouchableOpacity>
        )}

        {/* ── Live Stream ─────────────────────────────────── */}
        {tracker.status.isInReelsView && (
          <View style={styles.liveIndicator}>
            <View style={styles.liveWave}>
              <Icon name="waveform" size={14} color="#A29BFE" />
            </View>
            <Text style={styles.liveText} numberOfLines={1}>
              SYNCHRONIZED: <Text style={styles.liveUser}>@{tracker.status.currentUsername || 'Scanning...'}</Text>
            </Text>
          </View>
        )}

        {/* ── Main content ────────────────────────────────── */}
        <View style={styles.mainContent}>
          {tab === 'history' ? (
            <ReelHistory reels={tracker.recentReels} onClearHistory={tracker.clearHistory} />
          ) : (
            <ScrollView style={styles.mainScroll} showsVerticalScrollIndicator={false}>
              {tab === 'dashboard' && <StatsDashboard stats={tracker.stats} />}
              {tab === 'settings' && (
                <View style={styles.settingsSection}>
                  <Text style={styles.sectionHeading}>System Configuration</Text>
                  <FeatureToggles values={toggleValues} onToggle={handleToggle} />
                  
                  <View style={styles.techSpecs}>
                    <SpecRow label="CORE" value="V2.1.0-MLKIT" />
                    <SpecRow label="UPLINK" value="ACTIVE_LOCAL" />
                    <SpecRow label="LATENCY" value="~150ms" />
                  </View>
                </View>
              )}
              <View style={{ height: 100 }} />
            </ScrollView>
          )}
        </View>
      </Animated.View>
      
      {/* ── Decorator ────────────────────────────────── */}
      <View style={styles.scanLine} />
    </View>
  );
}

function SpecRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.specRow}>
      <Text style={styles.specLabel}>{label}</Text>
      <Text style={styles.specValue}>{value}</Text>
    </View>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <AppContent />
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#05050A',
  },
  inner: { flex: 1 },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    paddingHorizontal: 24,
    paddingTop: 20,
    paddingBottom: 30,
  },
  logo: {
    fontSize: 28,
    fontWeight: '900',
    color: '#FFFFFF',
    letterSpacing: -1,
  },
  logoAccent: {
    color: '#A29BFE',
  },
  tagline: {
    fontSize: 9,
    color: '#3D3D56',
    fontWeight: '900',
    letterSpacing: 2.5,
    marginTop: 4,
  },
  statusWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#0B0B1A',
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
    gap: 8,
  },
  statusPulse: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  statusText: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1,
  },
  tabBar: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginBottom: 24,
    backgroundColor: '#0B0B1A',
    padding: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#1A1A30',
  },
  tab: {
    flex: 1,
    flexDirection: 'row',
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
    gap: 8,
  },
  tabActive: {
    backgroundColor: '#A29BFE15',
  },
  tabTextActive: {
    color: '#A29BFE',
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  criticalWarning: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1A0B0B',
    marginHorizontal: 20,
    padding: 16,
    borderRadius: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#FF6B6B33',
    gap: 16,
  },
  warningIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#FF6B6B15',
    alignItems: 'center',
    justifyContent: 'center',
  },
  warningTitle: { color: '#FF6B6B', fontSize: 13, fontWeight: '900', letterSpacing: 0.5 },
  warningDesc: { color: '#6B4545', fontSize: 11, marginTop: 2, fontWeight: '500' },
  liveIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 20,
    backgroundColor: '#0B0B1A',
    padding: 10,
    borderRadius: 12,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#A29BFE33',
    gap: 12,
  },
  liveWave: {
    backgroundColor: '#A29BFE20',
    padding: 6,
    borderRadius: 6,
  },
  liveText: { color: '#55556E', fontSize: 10, fontWeight: '800', letterSpacing: 0.5 },
  liveUser: { color: '#A29BFE' },
  mainContent: { flex: 1 },
  mainScroll: { flex: 1 },
  settingsSection: { paddingHorizontal: 20 },
  sectionHeading: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '900',
    marginBottom: 20,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  techSpecs: {
    marginTop: 30,
    backgroundColor: '#0B0B1A',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#1A1A30',
  },
  specRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#1A1A30',
  },
  specLabel: { color: '#3D3D56', fontSize: 10, fontWeight: '900' },
  specValue: { color: '#7B7B9E', fontSize: 10, fontWeight: '700' },
  scanLine: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 1,
    backgroundColor: '#A29BFE10',
    shadowColor: '#A29BFE',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 5,
  },
});
