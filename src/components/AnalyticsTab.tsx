import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Dimensions,
  Animated,
  Easing,
  ScrollView,
} from 'react-native';
import Svg, { Path, Circle, G, Text as SvgText, Line } from 'react-native-svg';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { formatDuration } from '../utils/format';

const { width } = Dimensions.get('window');
const CLOCK_SIZE = width * 0.8;
const CENTER = CLOCK_SIZE / 2;

interface AnalyticsTabProps {
  stats: {
    hourlyActivity?: number[];
    topCreators?: Array<{ username: string; watchTimeMs: number; count: number }>;
    topKeywords?: Array<{ text: string; value: number }>;
  };
}

export function AnalyticsTab({ stats }: AnalyticsTabProps) {
  const rotateAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.loop(
      Animated.timing(rotateAnim, {
        toValue: 1,
        duration: 60000,
        easing: Easing.linear,
        useNativeDriver: true,
      }),
    ).start();
  }, []);

  const getPeakHour = () => {
    if (!stats.hourlyActivity) return '10:00 PM';
    let maxIdx = 0;
    let maxVal = 0;
    stats.hourlyActivity.forEach((val, idx) => {
      if (val > maxVal) {
        maxVal = val;
        maxIdx = idx;
      }
    });
    const period = maxIdx >= 12 ? 'PM' : 'AM';
    const displayHour = maxIdx % 12 || 12;
    return `${displayHour}:00 ${period}`;
  };

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      {/* ── THE CELESTIAL CLOCK (TOP) ─────────────────── */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>The Celestial Clock</Text>
        <View style={styles.clockContainer}>
          <Svg width={CLOCK_SIZE} height={CLOCK_SIZE} viewBox={`0 0 ${CLOCK_SIZE} ${CLOCK_SIZE}`}>
            {/* Background Rings */}
            <Circle cx={CENTER} cy={CENTER} r={CENTER - 20} stroke="#A67C6D22" strokeWidth="1" fill="none" />
            <Circle cx={CENTER} cy={CENTER} r={CENTER - 40} stroke="#A67C6D11" strokeWidth="1" fill="none" />
            
            {/* Hour Markers */}
            {[...Array(24)].map((_, i) => {
              const angle = (i * 15 - 90) * (Math.PI / 180);
              const x1 = CENTER + (CENTER - 25) * Math.cos(angle);
              const y1 = CENTER + (CENTER - 25) * Math.sin(angle);
              const x2 = CENTER + (CENTER - 15) * Math.cos(angle);
              const y2 = CENTER + (CENTER - 15) * Math.sin(angle);
              return <Line key={i} x1={x1} y1={y1} x2={x2} y2={y2} stroke="#A67C6D44" strokeWidth="1" />;
            })}

            {/* Activity Bars (Radial) */}
            {stats.hourlyActivity?.map((val, i) => {
              if (val === 0) return null;
              const angle = (i * 15 - 90) * (Math.PI / 180);
              const maxVal = Math.max(...(stats.hourlyActivity || [1]));
              const barLength = (val / maxVal) * (CENTER - 60);
              const x2 = CENTER + (40 + barLength) * Math.cos(angle);
              const y2 = CENTER + (40 + barLength) * Math.sin(angle);
              const x1 = CENTER + 40 * Math.cos(angle);
              const y1 = CENTER + 40 * Math.sin(angle);

              return (
                <Line
                  key={i}
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke="#A67C6D"
                  strokeWidth="6"
                  strokeLinecap="round"
                  opacity={0.8}
                />
              );
            })}

            {/* Central Eye */}
            <Circle cx={CENTER} cy={CENTER} r={30} fill="#FDFCF9" stroke="#333333" strokeWidth="1.5" />
            <Icon name="eye-outline" size={24} color="#333333" style={{ position: 'absolute', top: CENTER - 12, left: CENTER - 12 }} />
          </Svg>
          
          <View style={styles.clockOverlay}>
            <Text style={styles.peakStat}>
              Your peak consciousness is hijacked at <Text style={styles.peakTime}>{getPeakHour()}</Text>
            </Text>
          </View>
        </View>
      </View>

      {/* ── THE NEURAL WEB (MIDDLE) ──────────────────── */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>The Neural Web</Text>
        <View style={styles.webContainer}>
          <Animated.View
            style={[
              styles.webInner,
              {
                transform: [
                  {
                    rotate: rotateAnim.interpolate({
                      inputRange: [0, 1],
                      outputRange: ['0deg', '360deg'],
                    }),
                  },
                ],
              },
            ]}
          >
            {stats.topKeywords?.map((kw, i) => {
              const angle = (i * (360 / (stats.topKeywords?.length || 1))) * (Math.PI / 180);
              const radius = 60 + (i % 3) * 30;
              const x = radius * Math.cos(angle);
              const y = radius * Math.sin(angle);
              
              return (
                <View
                  key={i}
                  style={[
                    styles.keywordNode,
                    { left: CENTER + x - 40, top: CENTER + y - 100, transform: [{ scale: 0.8 + (kw.value / 10) }] },
                  ]}
                >
                  <Text style={styles.keywordText}>{kw.text.toUpperCase()}</Text>
                </View>
              );
            })}
          </Animated.View>
          <View style={styles.webOverlay}>
            <Text style={styles.webSubtext}>REVOLVING CONSTELLATION OF DOMINANT THOUGHTS</Text>
          </View>
        </View>
      </View>

      {/* ── THE PANTHEON (BOTTOM) ────────────────────── */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>The Pantheon</Text>
        <View style={styles.pantheonList}>
          {stats.topCreators?.map((creator, i) => {
            const archetype = i === 0 ? 'THE HOOK' : i === 1 ? 'THE MENTOR' : 'THE DISTRACTOR';
            const icon = i === 0 ? 'anchor' : i === 1 ? 'school' : 'incognito';
            
            return (
              <View key={i} style={styles.creatorCard}>
                <View style={styles.creatorHeader}>
                  <View style={styles.creatorAvatar}>
                    <Icon name={icon} size={20} color="#A67C6D" />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.creatorName} numberOfLines={1}>@{creator.username}</Text>
                    <Text style={styles.archetypeLabel}>{archetype}</Text>
                  </View>
                </View>
                <View style={styles.creatorStats}>
                  <View style={styles.creatorStatItem}>
                    <Text style={styles.creatorStatValue}>{formatDuration(creator.watchTimeMs)}</Text>
                    <Text style={styles.creatorStatLabel}>DEVOTION</Text>
                  </View>
                  <View style={styles.creatorStatItem}>
                    <Text style={styles.creatorStatValue}>{creator.count}</Text>
                    <Text style={styles.creatorStatLabel}>ENCOUNTERS</Text>
                  </View>
                </View>
              </View>
            );
          })}
        </View>
      </View>

      <View style={{ height: 100 }} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 20,
  },
  section: {
    marginBottom: 40,
    alignItems: 'center',
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '900',
    color: '#333333',
    letterSpacing: 2,
    textTransform: 'uppercase',
    marginBottom: 20,
    fontFamily: 'serif',
  },
  clockContainer: {
    width: CLOCK_SIZE,
    height: CLOCK_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  clockOverlay: {
    position: 'absolute',
    bottom: -20,
    width: '100%',
    alignItems: 'center',
  },
  peakStat: {
    fontSize: 12,
    color: '#333333',
    fontWeight: '600',
    textAlign: 'center',
    fontFamily: 'serif',
  },
  peakTime: {
    color: '#A67C6D',
    fontWeight: '900',
  },
  webContainer: {
    width: width - 40,
    height: 300,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FDFCF9',
    borderRadius: 24,
    borderWidth: 1.5,
    borderColor: '#333333',
    overflow: 'hidden',
  },
  webInner: {
    width: '100%',
    height: '100%',
    position: 'relative',
  },
  keywordNode: {
    position: 'absolute',
    paddingHorizontal: 10,
    paddingVertical: 4,
    backgroundColor: '#FDFCF9',
    borderWidth: 1,
    borderColor: '#A67C6D44',
    borderRadius: 12,
  },
  keywordText: {
    fontSize: 9,
    fontWeight: '800',
    color: '#A67C6D',
    letterSpacing: 1,
  },
  webOverlay: {
    position: 'absolute',
    bottom: 10,
  },
  webSubtext: {
    fontSize: 8,
    color: '#33333344',
    fontWeight: '800',
    letterSpacing: 1,
  },
  pantheonList: {
    width: '100%',
    gap: 12,
  },
  creatorCard: {
    backgroundColor: '#FDFCF9',
    borderWidth: 1.5,
    borderColor: '#333333',
    borderRadius: 20,
    padding: 16,
  },
  creatorHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginBottom: 12,
  },
  creatorAvatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#A67C6D15',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#A67C6D33',
  },
  creatorName: {
    fontSize: 16,
    fontWeight: '900',
    color: '#333333',
    fontFamily: 'serif',
  },
  archetypeLabel: {
    fontSize: 10,
    color: '#A67C6D',
    fontWeight: '800',
    letterSpacing: 1,
  },
  creatorStats: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    borderTopWidth: 1,
    borderTopColor: '#33333311',
    paddingTop: 12,
  },
  creatorStatItem: {
    alignItems: 'center',
  },
  creatorStatValue: {
    fontSize: 14,
    fontWeight: '900',
    color: '#333333',
  },
  creatorStatLabel: {
    fontSize: 8,
    color: '#A67C6D',
    fontWeight: '800',
    letterSpacing: 1,
  },
});
