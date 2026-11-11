#!/usr/bin/env bash
set -euo pipefail
mkdir -p /tmp/jarvis-voice-tests
javac -encoding UTF-8 -d /tmp/jarvis-voice-tests \
  android/app/src/main/java/com/andriy/jarvis/CommandPatterns.java \
  android/app/src/main/java/com/andriy/jarvis/SpotifyMusicCommands.java \
  android/app/src/main/java/com/andriy/jarvis/SpotifyUiLabels.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisSearchRouting.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisVoicePause.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisWavEnvelope.java \
  android/app/src/main/java/com/andriy/jarvis/GeneralWebSearchResults.java \
  android/app/src/main/java/com/andriy/jarvis/WorldNewsCommands.java \
  android/app/src/main/java/com/andriy/jarvis/WorldNewsFeed.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisOrbTranscript.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisLiveTranscript.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisTranscriptCorrection.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisOrbPlacement.java \
  android/app/src/main/java/com/andriy/jarvis/ScreenControlCommands.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisControlFeedback.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisPhoneMode.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisWearProtocol.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisShakePattern.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisShakeGate.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisSpeechErrorPolicy.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisCommandSequence.java \
  android/app/src/main/java/com/andriy/jarvis/JarvisSequenceEngine.java \
  android/app/src/main/java/com/andriy/jarvis/AssistantIntents.java \
  android/app/src/main/java/com/andriy/jarvis/DeviceCommands.java \
  android/app/src/main/java/com/andriy/jarvis/ScreenTextInsertion.java \
  android/app/src/main/java/com/andriy/jarvis/ScreenTargetMatcher.java \
  android/app/src/main/java/com/andriy/jarvis/NeuralSpeechPolicy.java \
  android/app/src/main/java/com/andriy/jarvis/OfflineVoicePackRules.java \
  android/app/src/main/java/com/andriy/jarvis/UkrainianVoiceText.java \
  android/app/src/test/java/com/andriy/jarvis/CommandPatternsTest.java \
  android/app/src/test/java/com/andriy/jarvis/SpotifyMusicCommandsTest.java \
  android/app/src/test/java/com/andriy/jarvis/SpotifyUiLabelsTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisSearchRoutingTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisVoicePauseTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisWavEnvelopeTest.java \
  android/app/src/test/java/com/andriy/jarvis/GeneralWebSearchResultsTest.java \
  android/app/src/test/java/com/andriy/jarvis/WorldNewsCommandsTest.java \
  android/app/src/test/java/com/andriy/jarvis/WorldNewsFeedTest.java \
  android/app/src/test/java/com/andriy/jarvis/DeviceCommandsTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisOrbTranscriptTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisLiveTranscriptTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisTranscriptCorrectionTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisOrbPlacementTest.java \
  android/app/src/test/java/com/andriy/jarvis/ScreenControlCommandsTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisControlFeedbackTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisPhoneModeTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisWearProtocolTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisShakePatternTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisShakeGateTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisSpeechErrorPolicyTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisCommandSequenceTest.java \
  android/app/src/test/java/com/andriy/jarvis/JarvisSequenceEngineTest.java \
  android/app/src/test/java/com/andriy/jarvis/ScreenTextInsertionTest.java \
  android/app/src/test/java/com/andriy/jarvis/ScreenTargetMatcherTest.java \
  android/app/src/test/java/com/andriy/jarvis/NeuralSpeechPolicyTest.java \
  android/app/src/test/java/com/andriy/jarvis/OfflineVoicePackRulesTest.java \
  android/app/src/test/java/com/andriy/jarvis/UkrainianVoiceTextTest.java
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.CommandPatternsTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.SpotifyMusicCommandsTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.SpotifyUiLabelsTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisSearchRoutingTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisVoicePauseTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisWavEnvelopeTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.GeneralWebSearchResultsTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.WorldNewsCommandsTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.WorldNewsFeedTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.DeviceCommandsTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisOrbTranscriptTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisLiveTranscriptTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisTranscriptCorrectionTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisOrbPlacementTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.ScreenControlCommandsTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisControlFeedbackTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisPhoneModeTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisWearProtocolTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisShakePatternTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisShakeGateTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisSpeechErrorPolicyTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisCommandSequenceTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.JarvisSequenceEngineTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.ScreenTextInsertionTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.ScreenTargetMatcherTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.NeuralSpeechPolicyTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.OfflineVoicePackRulesTest
java -cp /tmp/jarvis-voice-tests com.andriy.jarvis.UkrainianVoiceTextTest
