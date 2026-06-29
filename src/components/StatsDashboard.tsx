import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  Dimensions,
  ImageBackground,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { formatDuration, formatNumber } from '../utils/format';

const { width } = Dimensions.get('window');
const DIAL_SIZE = width * 0.85;
const image_multiplier = 3.5

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
      {/* ── Data Pills (Top) ─────────────────────────── */}
      <View style={styles.topPills}>
        <View style={styles.pillContainer}>
          <View style={styles.pill}>
            <Text style={styles.pillLabel}>
              ENGAGED:{' '}
              <Text style={styles.pillValue}>
                {formatNumber(stats.totalLikes)}
              </Text>
            </Text>
          </View>
          <View style={styles.pillLineLeft} />
        </View>

        <View style={styles.pillContainer}>
          <View style={styles.pill}>
            <Text style={styles.pillLabel}>
              ALL TIME:{' '}
              <Text style={styles.pillValue}>
                {formatNumber(stats.totalReels)}
              </Text>
            </Text>
          </View>
          <View style={styles.pillLineRight} />
        </View>
      </View>

      {/* ── Main Circular Dial ────────────────────────── */}
      <View style={styles.dialContainer}>
        <ImageBackground
          source={require('./circle.png')}
          resizeMode='center'
          style={styles.paperContainer}
          imageStyle={styles.paperImage}
        >
          {/* Outer Ring */}
          <View style={styles.outerRing}>
            {/* Middle Ring */}
            <View style={styles.middleRing}>
              {/* Waveform Area (Simulated Neural Wave) */}
              <View style={styles.waveformContainer}>
                <View style={styles.waveformInner}>
                  {/* Decorative Neural Waves */}
                  <View
                    style={[
                      styles.waveLayer,
                      { transform: [{ rotate: '15deg' }] },
                    ]}
                  />
                  <View
                    style={[
                      styles.waveLayer,
                      {
                        transform: [{ rotate: '-10deg' }],
                        borderColor: '#A67C6D22',
                      },
                    ]}
                  />
                  <View
                    style={[
                      styles.waveLayer,
                      {
                        transform: [{ rotate: '40deg' }],
                        borderColor: '#A67C6D11',
                      },
                    ]}
                  />

                  {/* Central Text */}
                  <View style={styles.centerContent}>
                    <Text style={styles.todayCount}>
                      {formatNumber(stats.todayReels)}
                    </Text>
                    <Text style={styles.todayLabel}>SESSIONS TODAY</Text>
                    <Text style={styles.focusTime}>
                      {formatDuration(stats.todayWatchTimeMs)}
                    </Text>
                    <Text style={styles.focusLabel}>Total Focus</Text>
                  </View>
                </View>
              </View>
            </View>

            {/* Dial Markers/Labels */}
            <View style={styles.markerTop}>
              <Icon name="star-four-points" size={14} color="#A67C6D" />
              <Text style={styles.markerText}>Neural Waveform</Text>
            </View>

            <View style={styles.markerBottomLeft}>
              <Text style={styles.markerText}>
                RETENTION: {Math.round(stats.avgCompletionPercent || 0)}%
              </Text>
            </View>

            <View style={styles.markerBottomRight}>
              <Text style={styles.markerText}>
                AVG TEMPO: {formatDuration(stats.avgWatchTimeMs)}
              </Text>
            </View>
          </View>
        </ImageBackground>
      </View>

      {/* ── Data Pills (Bottom) ──────────────────────── */}
      <View style={styles.bottomPills}>
        <View style={styles.pillContainer}>
          <View style={styles.pill}>
            <Text style={styles.pillLabel}>
              ENGAGED:{' '}
              <Text style={styles.pillValue}>
                {formatDuration(stats.avgWatchTimeMs)}
              </Text>
            </Text>
          </View>
          <View style={styles.pillLineLeftBottom} />
        </View>

        <View style={styles.pillContainer}>
          <View style={styles.pill}>
            <Text style={styles.pillLabel}>
              ALL TIME:{' '}
              <Text style={styles.pillValue}>
                {formatNumber(stats.totalReels)}
              </Text>
            </Text>
          </View>
          <View style={styles.pillLineRightBottom} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  paperContainer: {
    flex: 1,
  },
  paperImage: {
    opacity: 0.4, // Adjust opacity for desired visibility
    alignItems: 'center',
    justifyContent: 'center',
    height: DIAL_SIZE*image_multiplier,
    width: DIAL_SIZE*image_multiplier,
    left:-DIAL_SIZE*(image_multiplier-1)/2,
    top:-DIAL_SIZE*(image_multiplier-1)/2,
    transform: [{ scale: image_multiplier/2.5 }],
    filter:"blur(1px)"

    
    
  },
  container: {
    paddingHorizontal: 20,
    alignItems: 'center',
    paddingTop: 10,
    paddingBottom: 40,
  },
  topPills: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
    marginBottom: 20,
  },
  bottomPills: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
    marginTop: 20,
  },
  pillContainer: {
    alignItems: 'center',
    position: 'relative',
  },
  pill: {
    backgroundColor: '#FDFCF9',
    borderWidth: 1.5,
    borderColor: '#333333',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    zIndex: 2,
  },
  pillLabel: {
    fontSize: 10,
    color: '#333333',
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  pillValue: {
    fontWeight: '900',
  },
  pillLineLeft: {
    position: 'absolute',
    top: 24,
    left: 10,
    width: 60,
    height: 30,
    borderLeftWidth: 2,
    borderBottomWidth: 2,
    borderColor: '#A67C6D',
    borderBottomLeftRadius: 15,
  },
  pillLineRight: {
    position: 'absolute',
    top: 24,
    right: 10,
    width: 60,
    height: 30,
    borderRightWidth: 2,
    borderBottomWidth: 2,
    borderColor: '#A67C6D',
    borderBottomRightRadius: 15,
  },
  pillLineLeftBottom: {
    position: 'absolute',
    bottom: 24,
    left: 10,
    width: 60,
    height: 30,
    borderLeftWidth: 1.2,
    borderTopWidth: 1.2,
    borderColor: '#A67C6D',
    borderTopLeftRadius: 15,
  },
  pillLineRightBottom: {
    position: 'absolute',
    bottom: 24,
    right: 10,
    width: 60,
    height: 30,
    borderRightWidth: 1.2,
    borderTopWidth: 1.2,
    borderColor: '#A67C6D',
    borderTopRightRadius: 15,
  },
  dialContainer: {
    width: DIAL_SIZE,
    height: DIAL_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    position:'relative',
  },
  outerRing: {
    width: DIAL_SIZE,
    height: DIAL_SIZE,
    borderRadius: DIAL_SIZE / 2,
    borderWidth: 1,
    borderColor: '#A67C6D66',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  middleRing: {
    width: DIAL_SIZE * 0.85,
    height: DIAL_SIZE * 0.85,
    borderRadius: (DIAL_SIZE * 0.85) / 2,
    borderWidth: 1.5,
    borderColor: '#A67C6D',
    alignItems: 'center',
    justifyContent: 'center',
  },
  waveformContainer: {
    width: DIAL_SIZE * 0.7,
    height: DIAL_SIZE * 0.7,
    borderRadius: (DIAL_SIZE * 0.7) / 2,
    borderWidth: 1,
    borderColor: '#A67C6D33',
    padding: 4,
  },
  waveformInner: {
    flex: 1,
    borderRadius: (DIAL_SIZE * 0.7) / 2,
    // backgroundColor: '#FDFCF9',
    borderWidth: 1.5,
    borderColor: '#333333',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  waveLayer: {
    position: 'absolute',
    width: '110%',
    height: '110%',
    borderRadius: DIAL_SIZE * 0.7 * 0.55,
    borderWidth: 1,
    borderColor: '#A67C6D33',
    borderStyle: 'dashed',
  },
  centerContent: {
    alignItems: 'center',
  },
  todayCount: {
    fontSize: 72,
    fontWeight: '900',
    color: '#333333',
    fontFamily: 'serif',
  },
  todayLabel: {
    fontSize: 14,
    fontWeight: '800',
    color: '#333333',
    letterSpacing: 1,
    marginTop: -4,
  },
  focusTime: {
    fontSize: 16,
    fontWeight: '800',
    color: '#333333',
    marginTop: 10,
  },
  focusLabel: {
    fontSize: 12,
    color: '#A67C6D',
    fontWeight: '600',
  },
  markerTop: {
    position: 'absolute',
    top: -10,
    alignItems: 'center',
    backgroundColor: '#F5F0E6',
    paddingHorizontal: 10,
    flexDirection: 'row',
    gap: 4,
  },
  markerText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#333333',
    textTransform: 'capitalize',
    fontFamily: 'serif',
  },
  markerBottomLeft: {
    position: 'absolute',
    bottom: 40,
    left: -10,
    transform: [{ rotate: '-30deg' }],
    backgroundColor: '#F5F0E6',
    paddingHorizontal: 8,
  },
  markerBottomRight: {
    position: 'absolute',
    bottom: 40,
    right: -10,
    transform: [{ rotate: '30deg' }],
    backgroundColor: '#F5F0E6',
    paddingHorizontal: 8,
  },
});
