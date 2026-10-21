package oss.krtirtho.spotube

import com.ryanheise.audioservice.AudioServiceActivity

// The single audio_service MediaSession owns the native media notification.
// OriginOS may render its own compact music island for eligible media apps.
class MainActivity : AudioServiceActivity()
