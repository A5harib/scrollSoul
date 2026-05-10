import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  StatusBar,
  ScrollView,
  TouchableOpacity,
  Animated,
  Easing,
  ImageBackground,
} from 'react-native';
import {
  SafeAreaProvider,
  useSafeAreaInsets,
} from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useReelsTracker } from './src/hooks/useReelsTracker';
import { StatsDashboard } from './src/components/StatsDashboard';
import { ReelHistory } from './src/components/ReelHistory';
import { FeatureToggles } from './src/components/FeatureToggles';
import { AnalyticsTab } from './src/components/AnalyticsTab';

type Tab = 'dashboard' | 'history' | 'analytics' | 'settings';

const TAB_CONFIG: { key: Tab; icon: string; label: string }[] = [
  { key: 'history', icon: 'history', label: 'History' },
  { key: 'dashboard', icon: 'monitor-eye', label: 'Monitor' },
  { key: 'analytics', icon: 'chart-arc', label: 'Insights' },
  { key: 'settings', icon: 'cog-outline', label: 'Settings' },
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
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 800,
        useNativeDriver: true,
      }),
      Animated.spring(slideAnim, {
        toValue: 0,
        tension: 20,
        friction: 7,
        useNativeDriver: true,
      }),
    ]).start();
  }, []);

  useEffect(() => {
    if (tracker.status.isInReelsView) {
      const loop = Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, {
            toValue: 0.4,
            duration: 1000,
            easing: Easing.inOut(Easing.ease),
            useNativeDriver: true,
          }),
          Animated.timing(pulseAnim, {
            toValue: 1,
            duration: 1000,
            easing: Easing.inOut(Easing.ease),
            useNativeDriver: true,
          }),
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
    ? '#A29BFE'
    : '#55556E';
  const statusLabel = !tracker.isEnabled
    ? 'OFFLINE'
    : tracker.status.isInReelsView
    ? 'ACTIVE'
    : 'IDLE';

  return (
    <View style={styles.container}>
      <StatusBar
        barStyle="dark-content"
        translucent
        backgroundColor="transparent"
      />
      <ImageBackground
        source={require('./paper-texture.png')}
        resizeMode="repeat"
        style={styles.paperContainer}
        imageStyle={styles.paperImage}
      >
        {/* ── Background Decorations ──────────────────── */}
        <BackgroundDecorations />

        <Animated.View
          style={[
            styles.inner,
            {
              paddingTop: insets.top,
              opacity: fadeAnim,
              transform: [{ translateY: slideAnim }],
            },
          ]}
        >
          {/* ── Header ─────────────────────────────────────── */}
          <View style={styles.header}>
            <View>
              <View style={styles.logoRow}>
                <Text style={styles.logo}>SCROLLSOUL</Text>
                <View
                  style={[
                    styles.statusWrapper,
                    { borderColor: statusColor + '33' },
                  ]}
                >
                  <Animated.View
                    style={[
                      styles.statusPulse,
                      {
                        backgroundColor: statusColor,
                        opacity: tracker.status.isInReelsView ? pulseAnim : 0.2,
                      },
                    ]}
                  />
                  <Text style={[styles.statusText, { color: statusColor }]}>
                    {statusLabel}
                  </Text>
                </View>
              </View>
              <Text style={styles.tagline}> NEURAL DASHBOARD</Text>
            </View>
          </View>

          {/* ── Tabs ───────────────────────────────────────── */}
          <View style={styles.tabBarContainer}>
            <View style={styles.tabBarOuter}>
              <View style={styles.tabBarInner}>
                {/* Left Side Decoration */}
                <View style={styles.sideDecor}>
                  <View style={styles.decorCircle}>
                    <Text style={styles.decorText}>?</Text>
                  </View>
                  <View style={styles.decorHorizontalLine} />
                </View>

                {/* Dynamic Tabs */}
                <View style={styles.tabsWrapper}>
                  {TAB_CONFIG.map(t => {
                    const active = tab === t.key;
                    return (
                      <TouchableOpacity
                        key={t.key}
                        style={styles.tab}
                        onPress={() => setTab(t.key)}
                        activeOpacity={0.7}
                      >
                        <View
                          style={[
                            styles.tabIconCircle,
                            active && styles.tabIconCircleActive,
                          ]}
                        >
                          <Icon
                            name={t.icon}
                            size={22}
                            color={active ? '#A67C6D' : '#333333'}
                          />
                        </View>
                        <Text
                          style={[
                            styles.tabLabel,
                            active && styles.tabLabelActive,
                          ]}
                        >
                          {t.label}
                        </Text>
                      </TouchableOpacity>
                    );
                  })}
                </View>

                {/* Right Side Decoration */}
                <View
                  style={[styles.sideDecor, { flexDirection: 'row-reverse' }]}
                >
                  <View style={styles.decorCircle}>
                    <Text style={styles.decorText}>?</Text>
                  </View>
                  <View style={styles.decorHorizontalLine} />
                </View>
              </View>
            </View>
          </View>

          {/* ── Warning ────────────────────────────── */}
          {!tracker.isEnabled && (
            <TouchableOpacity
              style={styles.criticalWarning}
              onPress={tracker.openSettings}
            >
              <View style={styles.warningIcon}>
                <Icon name="alert-decagram" size={20} color="#FF6B6B" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.warningTitle}>PERMISSIONS_REQUIRED</Text>
                <Text style={styles.warningDesc}>
                  Accessibility bridge not established
                </Text>
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
                SYNCHRONIZED:{' '}
                <Text style={styles.liveUser}>
                  @{tracker.status.currentUsername || 'Scanning...'}
                </Text>
              </Text>
            </View>
          )}

          {/* ── Main content ────────────────────────────────── */}
          <View style={styles.mainContent}>
            {tab === 'history' ? (
              <ReelHistory
                reels={tracker.recentReels}
                onClearHistory={tracker.clearHistory}
              />
            ) : (
              <ScrollView
                style={styles.mainScroll}
                showsVerticalScrollIndicator={false}
              >
                {tab === 'dashboard' && (
                  <StatsDashboard stats={tracker.stats} />
                )}
                {tab === 'analytics' && (
                  <AnalyticsTab stats={tracker.stats} />
                )}
                {tab === 'settings' && (
                  <View style={styles.settingsSection}>
                    <Text style={styles.sectionHeading}>
                      System Configuration
                    </Text>
                    <FeatureToggles
                      values={toggleValues}
                      onToggle={handleToggle}
                    />

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
      </ImageBackground>
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

function BackgroundDecorations() {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {/* Stars */}
      <Icon
        name="star-four-points"
        size={10}
        color="#A67C6D22"
        style={{ position: 'absolute', top: 50, left: 30 }}
      />
      <Icon
        name="star-four-points"
        size={6}
        color="#A67C6D33"
        style={{ position: 'absolute', top: 120, right: 40 }}
      />
      <Icon
        name="star-four-points"
        size={8}
        color="#A67C6D22"
        style={{ position: 'absolute', bottom: 150, left: 50 }}
      />
      <Icon
        name="star-four-points"
        size={12}
        color="#A67C6D15"
        style={{ position: 'absolute', bottom: 200, right: 60 }}
      />
      <Icon
        name="star-four-points"
        size={5}
        color="#A67C6D44"
        style={{ position: 'absolute', top: 300, left: '50%' }}
      />

      {/* Constellation Lines (Simulated) */}
      <View
        style={[
          styles.constellationLine,
          { top: 80, left: 40, width: 40, transform: [{ rotate: '45deg' }] },
        ]}
      />
      <View
        style={[
          styles.constellationLine,
          { top: 100, left: 70, width: 30, transform: [{ rotate: '-20deg' }] },
        ]}
      />
      <View
        style={[
          styles.constellationLine,
          {
            bottom: 180,
            right: 80,
            width: 50,
            transform: [{ rotate: '15deg' }],
          },
        ]}
      />
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
  paperContainer: {
    flex: 1,
    backgroundColor: '#F5F0E6', // Fallback color
  },
  paperImage: {
    opacity: 0.25, // Adjust opacity for desired visibility
  },
  content: {
    flex: 1,
    padding: 20,
  },
  container: {
    flex: 1,
    backgroundColor: '#F5F0E6', // Cream base
  },
  inner: { flex: 1 },
  header: {
    paddingHorizontal: 24,
    paddingTop: 40,
    paddingBottom: 20,
  },
  logoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  logo: {
    fontSize: 34,
    fontWeight: '900',
    color: '#333333',
    letterSpacing: 1,
    fontFamily: 'serif',
  },
  tagline: {
    fontSize: 14,
    color: '#333333',
    fontWeight: '500',
    letterSpacing: 2,
    marginTop: 8,
    lineHeight: 18,
    fontFamily: 'serif',
  },
  statusWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FDFCF9',
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    gap: 8,
    borderColor: '#A67C6D33',
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
  tabBarContainer: {
    marginHorizontal: 12,
    marginTop: 0,
    marginBottom: 20,
    height: 90,
  },
  tabBarOuter: {
    backgroundColor: '#FDFCF9',
    borderWidth: 1.5,
    borderColor: '#333333',
    borderRadius: 24,
    padding: 3,
    height: 80,
  },
  tabBarInner: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#33333322',
    borderRadius: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
  },
  sideDecor: {
    flexDirection: 'row',
    alignItems: 'center',
    width: 40,
  },
  decorCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#33333322',
    alignItems: 'center',
    justifyContent: 'center',
  },
  decorText: {
    fontSize: 12,
    color: '#33333322',
    fontWeight: '300',
  },
  decorHorizontalLine: {
    flex: 1,
    height: 1,
    backgroundColor: '#33333311',
    marginHorizontal: 4,
  },
  tabsWrapper: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
  },
  tab: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 8,
  },
  tabIconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 2,
  },
  tabIconCircleActive: {
    backgroundColor: '#A67C6D10',
    borderWidth: 1,
    borderColor: '#A67C6D33',
    borderRadius: 20,
  },
  tabLabel: {
    fontSize: 10,
    color: '#333333',
    fontWeight: '600',
    fontFamily: 'serif',
  },
  tabLabelActive: {
    color: '#A67C6D',
    fontWeight: '800',
  },
  criticalWarning: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF5F5',
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
  warningTitle: {
    color: '#FF6B6B',
    fontSize: 13,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  warningDesc: {
    color: '#6B4545',
    fontSize: 11,
    marginTop: 2,
    fontWeight: '500',
  },
  liveIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 20,
    backgroundColor: '#FDFCF9',
    padding: 10,
    borderRadius: 12,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#A67C6D33',
    gap: 12,
  },
  liveWave: {
    backgroundColor: '#A67C6D20',
    padding: 6,
    borderRadius: 6,
  },
  liveText: {
    color: '#333333',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  liveUser: { color: '#A67C6D' },
  mainContent: { flex: 1 },
  mainScroll: { flex: 1 },
  settingsSection: { paddingHorizontal: 20 },
  sectionHeading: {
    color: '#333333',
    fontSize: 14,
    fontWeight: '900',
    marginBottom: 20,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  techSpecs: {
    marginTop: 30,
    backgroundColor: '#FDFCF9',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#A67C6D33',
  },
  specRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#A67C6D22',
  },
  specLabel: { color: '#A67C6D', fontSize: 10, fontWeight: '900' },
  specValue: { color: '#333333', fontSize: 10, fontWeight: '700' },
  scanLine: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 1,
    backgroundColor: '#A67C6D08',
  },
  constellationLine: {
    position: 'absolute',
    height: 1,
    backgroundColor: '#A67C6D15',
  },
});
