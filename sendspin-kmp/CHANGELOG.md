# Changelog

All changes to `io.github.music-assistant:sendspin-kmp` are in this file.

## 0.2.0

- `LocalPlayerConfig.minBufferMs` sets the `min_buffer_ms` of `client/state` (spec 1.0 wire). The
  server waits that long before the first chunk of every stream, so a lower value shortens the
  silence of a track change. The default stays 1000 ms. A change applies without a reconnect.
- The scheduler does not count the device underruns before the first write of a stream, and it logs
  a jump of the server timeline between two chunks (`Timeline jump`).

## 0.1.0

The first published version.

- Sendspin `player@v1` client with an encrypted (Noise) session, pairing, clock synchronization,
  and scheduled audio output.
- Two wire versions: `WireVersion.LEGACY_9` (aiosendspin 9.x) and `WireVersion.SPEC_1_0`
  (Sendspin spec 1.0.0-rc1, aiosendspin 10.x).
- Android ports in `io.music_assistant.sendspin.android`: `AudioTrackSink`,
  `AndroidDecoderFactory`, `SharedPreferencesSendspinKeyStore`, `connectivityOnline`,
  `sendspinAudioDispatcher`, and `ElapsedRealtimeClock`.
- Targets: Android (minSdk 28), iosArm64, and iosSimulatorArm64.
