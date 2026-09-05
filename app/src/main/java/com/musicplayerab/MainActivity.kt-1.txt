package com.musicplayerab

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val folder: String
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

data class Palette(
    val name: String,
    val light: Color,
    val dark: Color
)

val palettes = listOf(
    Palette("زمردي", Color(0xFF008F63), Color(0xFF43E6A1)),
    Palette("أزرق", Color(0xFF1769E0), Color(0xFF67A6FF)),
    Palette("بنفسجي", Color(0xFF6B35D8), Color(0xFF9B72FF)),
    Palette("وردي", Color(0xFFC2185B), Color(0xFFFF72AD)),
    Palette("برتقالي", Color(0xFFE56717), Color(0xFFFFA45B)),
    Palette("أحمر", Color(0xFFC62828), Color(0xFFFF7070)),
    Palette("تركوازي", Color(0xFF008C95), Color(0xFF52E4EA)),
    Palette("ذهبي", Color(0xFFA46B00), Color(0xFFFFC857)),
    Palette("زيتوني", Color(0xFF5C7511), Color(0xFFA9D34A)),
    Palette("وردي داكن", Color(0xFF8A285E), Color(0xFFE96CB5))
)

class MainActivity : ComponentActivity() {

    lateinit var player: ExoPlayer

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        player = ExoPlayer.Builder(this).build()

        setContent {
            App(this, player)
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    suspend fun library(): List<Song> = withContext(Dispatchers.IO) {

        val result = mutableListOf<Song>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        contentResolver.query(
            base,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->

            val id = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )

            val title = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )

            val artist = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )

            val album = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM
            )

            val duration = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DURATION
            )

            val path = cursor.getColumnIndex(
                MediaStore.Audio.Media.DATA
            )

            while (cursor.moveToNext()) {

                val songId = cursor.getLong(id)

                val songPath =
                    if (path >= 0) cursor.getString(path) ?: ""
                    else ""

                result += Song(
                    id = songId,
                    title = cursor.getString(title) ?: "بدون عنوان",
                    artist = cursor.getString(artist) ?: "فنان غير معروف",
                    album = cursor.getString(album) ?: "ألبوم غير معروف",
                    duration = cursor.getLong(duration),
                    uri = ContentUris.withAppendedId(base, songId),
                    folder = songPath
                        .substringBeforeLast("/")
                        .substringAfterLast("/")
                )
            }
        }

        result
    }
}

@Composable
fun App(
    activity: MainActivity,
    player: ExoPlayer
) {

    val prefs = activity.getSharedPreferences(
        "prefs",
        Context.MODE_PRIVATE
    )

    var theme by remember {
        mutableStateOf(
            ThemeMode.valueOf(
                prefs.getString("theme", "SYSTEM")!!
            )
        )
    }

    var paletteIndex by remember {
        mutableIntStateOf(prefs.getInt("palette", 2))
    }

    var songs by remember {
        mutableStateOf<List<Song>>(emptyList())
    }

    var current by remember {
        mutableStateOf<Song?>(null)
    }

    var query by remember {
        mutableStateOf("")
    }

    var tab by remember {
        mutableIntStateOf(0)
    }

    var position by remember {
        mutableLongStateOf(0)
    }

    var duration by remember {
        mutableLongStateOf(1)
    }

    var pointA by remember {
        mutableStateOf<Long?>(null)
    }

    var pointB by remember {
        mutableStateOf<Long?>(null)
    }

    var loops by remember {
        mutableIntStateOf(0)
    }

    var repeatCount by remember {
        mutableStateOf("3")
    }

    var speed by remember {
        mutableFloatStateOf(1f)
    }

    var showNowPlaying by remember {
        mutableStateOf(false)
    }

    var favs by remember {
        mutableStateOf(
            prefs.getStringSet("favs", emptySet())!!.toSet()
        )
    }

    val permission =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val requestPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            if (it) {
            }
        }

    LaunchedEffect(Unit) {

        if (
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            songs = activity.library()
        } else {
            requestPermission.launch(permission)
        }
    }

    LaunchedEffect(Unit) {

        while (true) {

            position =
                player.currentPosition.coerceAtLeast(0)

            duration =
                player.duration.takeIf { it > 0 } ?: 1

            if (
                pointA != null &&
                pointB != null &&
                position >= pointB!!
            ) {

                val max =
                    if (repeatCount == "∞") {
                        Int.MAX_VALUE
                    } else {
                        repeatCount.toInt()
                    }

                loops++

                if (loops < max) {
                    player.seekTo(pointA!!)
                } else {
                    pointA = null
                    pointB = null
                    loops = 0
                }
            }

            delay(80)
        }
    }

    val dark = when (theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val palette = palettes[paletteIndex]

    val colors =
        if (dark) {
            darkColorScheme(
                primary = palette.dark,
                secondary = palette.dark
            )
        } else {
            lightColorScheme(
                primary = palette.light,
                secondary = palette.light
            )
        }

    MaterialTheme(colorScheme = colors) {

        if (
            showNowPlaying &&
            current != null
        ) {

            NowPlayingScreen(
                song = current!!,
                player = player,
                position = position,
                duration = duration,
                pointA = pointA,
                pointB = pointB,
                loops = loops,
                repeatCount = repeatCount,
                speed = speed,

                onBack = {
                    showNowPlaying = false
                },

                seek = {
                    player.seekTo(it)
                },

                setA = {
                    val now =
                        player.currentPosition.coerceAtLeast(0)

                    pointA = now

                    if (
                        pointB != null &&
                        now >= pointB!!
                    ) {
                        pointB = null
                    }

                    loops = 0
                },

                setB = {
                    val now =
                        player.currentPosition.coerceAtLeast(0)

                    if (
                        pointA != null &&
                        now >= pointA!!
                    ) {
                        pointB = now
                        loops = 0
                    }
                },

                clear = {
                    pointA = null
                    pointB = null
                    loops = 0
                },

                setRepeat = {
                    repeatCount = it
                    loops = 0
                },

                setSpeed = {
                    speed = it
                    player.setPlaybackSpeed(it)
                }
            )

        } else {

            Scaffold(
                bottomBar = {

                    NavigationBar {

                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = {
                                tab = 0
                            },
                            icon = {
                                Icon(
                                    Icons.Default.LibraryMusic,
                                    null
                                )
                            },
                            label = {
                                Text("المكتبة")
                            }
                        )

                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = {
                                tab = 1
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Favorite,
                                    null
                                )
                            },
                            label = {
                                Text("المفضلة")
                            }
                        )

                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = {
                                tab = 2
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Settings,
                                    null
                                )
                            },
                            label = {
                                Text("الإعدادات")
                            }
                        )
                    }
                }
            ) { padding ->

                if (tab == 2) {

                    Settings(
                        theme = theme,
                        paletteIndex = paletteIndex,

                        onTheme = {
                            theme = it

                            prefs.edit()
                                .putString(
                                    "theme",
                                    it.name
                                )
                                .apply()
                        },

                        onPalette = {
                            paletteIndex = it

                            prefs.edit()
                                .putInt(
                                    "palette",
                                    it
                                )
                                .apply()
                        }
                    )

                } else {

                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(14.dp)
                    ) {

                        Text(
                            "Music Player A-B",
                            fontSize = 26.sp,
                            fontWeight =
                                FontWeight.ExtraBold
                        )

                        Text(
                            if (tab == 0)
                                "مكتبة الموسيقى"
                            else
                                "المفضلة",
                            fontSize = 12.sp,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )

                        Spacer(
                            Modifier.height(10.dp)
                        )

                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "ابحث عن أغنية أو فنان أو ألبوم"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    null
                                )
                            }
                        )

                        Spacer(
                            Modifier.height(8.dp)
                        )

                        Row(
                            Modifier.horizontalScroll(
                                rememberScrollState()
                            ),
                            horizontalArrangement =
                                Arrangement.spacedBy(7.dp)
                        ) {

                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = {
                                    Text(
                                        "${songs.size} أغنية"
                                    )
                                }
                            )
                        }

                        val filteredSongs =
                            songs.filter {

                                (
                                    tab == 0 ||
                                        favs.contains(
                                            it.id.toString()
                                        )
                                    ) && (
                                    query.isBlank() ||
                                        it.title.contains(
                                            query,
                                            true
                                        ) ||
                                        it.artist.contains(
                                            query,
                                            true
                                        ) ||
                                        it.album.contains(
                                            query,
                                            true
                                        ) ||
                                        it.folder.contains(
                                            query,
                                            true
                                        )
                                    )
                            }

                        LazyColumn(
                            Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {

                            items(
                                filteredSongs,
                                key = {
                                    it.id
                                }
                            ) { song ->

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(
                                            RoundedCornerShape(
                                                16.dp
                                            )
                                        )
                                        .background(
                                            if (
                                                current?.id ==
                                                song.id
                                            ) {
                                                MaterialTheme
                                                    .colorScheme
                                                    .primaryContainer
                                            } else {
                                                MaterialTheme
                                                    .colorScheme
                                                    .surfaceVariant
                                            }
                                        )
                                        .clickable {

                                            current = song

                                            player.setMediaItem(
                                                MediaItem.fromUri(
                                                    song.uri
                                                )
                                            )

                                            player.prepare()

                                            player.setPlaybackSpeed(
                                                speed
                                            )

                                            player.play()

                                            pointA = null
                                            pointB = null
                                            loops = 0

                                            showNowPlaying = true
                                        }
                                        .padding(11.dp),

   verticalAlignment =
                                        Alignment.CenterVertically
                                ) {

                                    Box(
                                        Modifier
                                            .size(46.dp)
                                            .clip(
                                                RoundedCornerShape(13.dp)
                                            )
                                            .background(
                                                MaterialTheme
                                                    .colorScheme
                                                    .primary
                                            ),
                                        contentAlignment =
                                            Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            null,
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(
                                        Modifier.width(10.dp)
                                    )

                                    Column(
                                        Modifier.weight(1f)
                                    ) {

                                        Text(
                                            song.title,
                                            fontWeight =
                                                FontWeight.Bold,
                                            maxLines = 1
                                        )

                                        Text(
                                            "${song.artist} • ${song.folder}",
                                            fontSize = 11.sp,
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    IconButton(
                                        onClick = {

                                            val newFavs =
                                                favs.toMutableSet()

                                            if (
                                                !newFavs.add(
                                                    song.id.toString()
                                                )
                                            ) {
                                                newFavs.remove(
                                                    song.id.toString()
                                                )
                                            }

                                            favs = newFavs

                                            prefs.edit()
                                                .putStringSet(
                                                    "favs",
                                                    newFavs
                                                )
                                                .apply()
                                        }
                                    ) {

                                        Icon(
                                            if (
                                                favs.contains(
                                                    song.id.toString()
                                                )
                                            ) {
                                                Icons.Default.Favorite
                                            } else {
                                                Icons.Default.FavoriteBorder
                                            },
                                            null
                                        )
                                    }
                                }
                            }
                        }

                        current?.let { song ->

                            MiniPlayerBar(
                                song = song,
                                player = player,
                                position = position,
                                duration = duration,
                                open = {
                                    showNowPlaying = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    song: Song,
    player: ExoPlayer,
    position: Long,
    duration: Long,
    open: () -> Unit
) {

    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    open()
                },
        shape =
            RoundedCornerShape(20.dp)
    ) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                Modifier
                    .size(45.dp)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    .background(
                        MaterialTheme
                            .colorScheme
                            .primary
                    ),
                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = Color.White
                )
            }

            Spacer(
                Modifier.width(10.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {

                Text(
                    song.title,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    "${fmt(position)} / ${fmt(duration)}",
                    fontSize = 10.sp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
            ) {

                Icon(
                    if (player.isPlaying) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    null
                )
            }

            IconButton(
                onClick = {
                    open()
                }
            ) {

                Icon(
                    Icons.Default.OpenInFull,
                    null
                )
            }
        }
    }
}

@Composable
fun NowPlayingScreen(
    song: Song,
    player: ExoPlayer,
    position: Long,
    duration: Long,
    pointA: Long?,
    pointB: Long?,
    loops: Int,
    repeatCount: String,
    speed: Float,
    onBack: () -> Unit,
    seek: (Long) -> Unit,
    setA: () -> Unit,
    setB: () -> Unit,
    clear: () -> Unit,
    setRepeat: (String) -> Unit,
    setSpeed: (Float) -> Unit
) {

    var repeatMenu by remember {
        mutableStateOf(false)
    }

    var speedMenu by remember {
        mutableStateOf(false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 6.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    null
                )
            }

            Column(
                Modifier.weight(1f)
            ) {

                Text(
                    "التشغيل الآن",
                    fontSize = 18.sp,
                    fontWeight =
                        FontWeight.ExtraBold
                )

                Text(
                    "Music Player A-B",
                    fontSize = 10.sp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            IconButton(
                onClick = {}
            ) {

                Icon(
                    Icons.Default.MoreVert,
                    null
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Spacer(
                Modifier.height(10.dp)
            )

            Box(
                Modifier
                    .size(210.dp)
                    .clip(
                        RoundedCornerShape(32.dp)
                    )
                    .background(
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                    ),
                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    Icons.Default.MusicNote,
                    null,
                    modifier =
                        Modifier.size(92.dp),
                    tint =
                        MaterialTheme
                            .colorScheme
                            .primary
                )
            }

            Spacer(
                Modifier.height(18.dp)
            )

            Text(
                song.title,
                fontSize = 22.sp,
                fontWeight =
                    FontWeight.ExtraBold,
                maxLines = 1
            )

            Text(
                song.artist,
                fontSize = 13.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                maxLines = 1
            )

            Spacer(
                Modifier.height(14.dp)
            )

            Slider(
                value =
                    position
                        .coerceIn(0, duration)
                        .toFloat(),
                onValueChange = {
                    seek(it.toLong())
                },
                valueRange =
                    0f..duration.toFloat()
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    fmt(position),
                    fontSize = 11.sp
                )

                Text(
                    fmt(duration),
                    fontSize = 11.sp
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceEvenly,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = {
                        player.seekBack()
                    }
                ) {
                    Icon(
                        Icons.Default.Replay5,
                        null
                    )
                }

                IconButton(
                    onClick = {
                        player.seekToPreviousMediaItem()
                    }
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        null
                    )
                }

                FilledIconButton(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                    modifier =
                        Modifier.size(66.dp)
                ) {

                    Icon(
                        if (player.isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        null,
                        modifier =
                            Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = {
                        player.seekToNextMediaItem()
                    }
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        null
                    )
                }

                IconButton(
                    onClick = {
                        player.seekForward()
                    }
                ) {
                    Icon(
                        Icons.Default.Forward5,
                        null
                    )
                }
            }

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                "تحديد التكرار A-B",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                "يمكن تحديد A و B أثناء التشغيل أو بعد الإيقاف المؤقت.",
                fontSize = 11.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                FilledTonalButton(
                    onClick = setA,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(58.dp),
                    shape =
                        RoundedCornerShape(18.dp)
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            "A",
                            fontSize = 21.sp,
                            fontWeight =
                                FontWeight.ExtraBold
                        )

                        Text(
                            pointA?.let(::fmt)
                                ?: "تعيين النقطة",
                            fontSize = 10.sp
                        )
                    }
                }

                FilledTonalButton(
                    onClick = setB,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(58.dp),
                    shape =
                        RoundedCornerShape(18.dp),
                    enabled =
                        pointA != null
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            "B",
                            fontSize = 21.sp,
                            fontWeight =
                                FontWeight.ExtraBold
                        )

                        Text(
                            pointB?.let(::fmt)
                                ?: "تعيين النقطة",
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(7.dp)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = clear,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        "مسح A-B"
                    )
                }

                Box(
                    Modifier.weight(1f)
                ) {

                    OutlinedButton(
                        onClick = {
                            repeatMenu = true
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            if (
                                repeatCount == "∞"
                            ) {
                                "لا نهائي"
                            } else {
                                "$repeatCount مرات"
                            }
                        )
                    }

                    DropdownMenu(
                        expanded =
                            repeatMenu,
                        onDismissRequest = {
                            repeatMenu = false
                        }
                    ) {

                        listOf(
                            "3",
                            "5",
                            "7",
                            "11",
                            "∞"
                        ).forEach { value ->

                            DropdownMenuItem(
                                text = {

                                    Text(
                                        if (
                                            value == "∞"
                                        ) {
                                            "لا نهائي"
                                        } else {
                                            "$value مرات"
                                        }
                                    )
                                },
                                onClick = {

                                    setRepeat(value)

                                    repeatMenu = false
                                }
                            )
                        }
                    }
                }

                Box(
                    Modifier.weight(1f)
                ) {

                    OutlinedButton(
                        onClick = {
                            speedMenu = true
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "${"%.2f".format(speed)}x"
                        )
                    }

                    DropdownMenu(
                        expanded =
                            speedMenu,
                     Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = clear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("مسح A-B")
                }

                Box(
                    modifier = Modifier.weight(1f)
                ) {

                    OutlinedButton(
                        onClick = {
                            repeatMenu = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            if (repeatCount == "∞")
                                "لا نهائي"
                            else
                                "$repeatCount مرات"
                        )
                    }

                    DropdownMenu(
                        expanded = repeatMenu,
                        onDismissRequest = {
                            repeatMenu = false
                        }
                    ) {

                        listOf(
                            "3",
                            "5",
                            "7",
                            "11",
                            "∞"
                        ).forEach { value ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (value == "∞")
                                            "لا نهائي"
                                        else
                                            "$value مرات"
                                    )
                                },
                                onClick = {

                                    setRepeat(value)

                                    repeatMenu = false
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f)
                ) {

                    OutlinedButton(
                        onClick = {
                            speedMenu = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "${"%.2f".format(speed)}x"
                        )
                    }

                    DropdownMenu(
                        expanded = speedMenu,
                        onDismissRequest = {
                            speedMenu = false
                        }
                    ) {

                        listOf(
                            0.5f,
                            0.75f,
                            1f,
                            1.25f,
                            1.5f,
                            2f
                        ).forEach { value ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${"%.2f".format(value)}x"
                                    )
                                },
                                onClick = {

                                    setSpeed(value)

                                    speedMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(
                Modifier.height(7.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Column {

                        Text(
                            if (
                                pointA != null &&
                                pointB != null
                            ) {
                                "A-B مفعّل"
                            } else {
                                "A-B غير مكتمل"
                            },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Text(
                            if (
                                pointA != null &&
                                pointB != null
                            ) {
                                "من ${formatTime(pointA!!)} إلى ${formatTime(pointB!!)}"
                            } else {
                                "اضغط A ثم B لتحديد المقطع"
                            },
                            fontSize = 10.sp,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    if (
                        pointA != null &&
                        pointB != null
                    ) {

                        Text(
                            "$loops / $repeatCount",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                "يمكنك سحب شريط التقدم لتغيير موضع الأغنية في أي وقت.",
                fontSize = 10.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
fun Settings(
    theme: ThemeMode,
    paletteIndex: Int,
    onTheme: (ThemeMode) -> Unit,
    onPalette: (Int) -> Unit
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Text(
                "الإعدادات",
                fontSize = 27.sp,
                fontWeight =
                    FontWeight.ExtraBold
            )

            Text(
                "Music Player A-B",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        item {

            ElevatedCard(
                shape =
                    RoundedCornerShape(22.dp)
            ) {

                Column(
                    Modifier.padding(16.dp)
                ) {

                    Text(
                        "المظهر",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {

                        listOf(
                            ThemeMode.LIGHT to "فاتح",
                            ThemeMode.DARK to "غامق",
                            ThemeMode.SYSTEM to "تلقائي"
                        ).forEach { pair ->

                            val mode = pair.first
                            val name = pair.second

                            FilterChip(
                                selected =
                                    theme == mode,
                                onClick = {
                                    onTheme(mode)
                                },
                                label = {
                                    Text(name)
                                }
                            )
                        }
                    }
                }
            }
        }

        item {

            ElevatedCard(
                shape =
                    RoundedCornerShape(22.dp)
            ) {

                Column(
                    Modifier.padding(16.dp)
                ) {

                    Text(
                        "ألوان الواجهة",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "ألوان متعددة متناسقة للوضع الفاتح والغامق",
                        fontSize = 11.sp,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    palettes
                        .chunked(5)
                        .forEach { row ->

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceEvenly
                            ) {

                                row.forEach { palette ->

                                    val index =
                                        palettes.indexOf(
                                            palette
                                        )

                                    Box(
                                        modifier =
                                            Modifier
                                                .size(40.dp)
                                                .clip(
                                                    CircleShape
                                                )
                                                .background(
                                                    if (
                                                        isSystemInDarkTheme()
                                                    ) {
                                                        palette.dark
                                                    } else {
                                                        palette.light
                                                    }
                                                )
                                                .clickable {

                                                    onPalette(
                                                        index
                                                    )
                                                },
                                        contentAlignment =
                                            Alignment.Center
                                    ) {

                                        if (
                                            paletteIndex ==
                                            index
                                        ) {

                                            Icon(
                                                Icons.Default.Check,
                                                null,
                                                tint =
                                                    Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }

        item {

            Text(
                "A-B: 3 / 5 / 7 / 11 / لا نهائي • مكتبة الهاتف • المفضلة • سرعة التشغيل",
                fontSize = 12.sp
            )
        }
    }
}

fun formatTime(
    milliseconds: Long
): String {

    val seconds =
        milliseconds
            .coerceAtLeast(0)
            .div(1000)

    return "%02d:%02d".format(
        seconds / 60,
        seconds % 60
    )
}
