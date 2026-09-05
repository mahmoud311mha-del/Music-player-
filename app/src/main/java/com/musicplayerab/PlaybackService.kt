package com.musicplayerab
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
class PlaybackService: MediaSessionService() {
 private var session: MediaSession?=null
 override fun onCreate(){super.onCreate(); val p=ExoPlayer.Builder(this).build(); session=MediaSession.Builder(this,p).build()}
 override fun onGetSession(c: MediaSession.ControllerInfo)=session
 override fun onDestroy(){session?.player?.release();session?.release();super.onDestroy()}
}
