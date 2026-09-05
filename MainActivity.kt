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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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

data class Song(val id:Long,val title:String,val artist:String,val album:String,val duration:Long,val uri:Uri,val folder:String)
enum class ThemeMode{LIGHT,DARK,SYSTEM}
data class Palette(val name:String,val light:Color,val dark:Color)
val palettes=listOf(
 Palette("زمردي",Color(0xFF008F63),Color(0xFF43E6A1)),Palette("أزرق",Color(0xFF1769E0),Color(0xFF67A6FF)),
 Palette("بنفسجي",Color(0xFF6B35D8),Color(0xFF9B72FF)),Palette("وردي",Color(0xFFC2185B),Color(0xFFFF72AD)),
 Palette("برتقالي",Color(0xFFE56717),Color(0xFFFFA45B)),Palette("أحمر",Color(0xFFC62828),Color(0xFFFF7070)),
 Palette("تركوازي",Color(0xFF008C95),Color(0xFF52E4EA)),Palette("ذهبي",Color(0xFFA46B00),Color(0xFFFFC857)),
 Palette("زيتوني",Color(0xFF5C7511),Color(0xFFA9D34A)),Palette("وردي داكن",Color(0xFF8A285E),Color(0xFFE96CB5))
)

class MainActivity:ComponentActivity(){
 lateinit var player:ExoPlayer
 override fun onCreate(b:Bundle?){super.onCreate(b);player=ExoPlayer.Builder(this).build();setContent{App(this,player)}}
 override fun onDestroy(){player.release();super.onDestroy()}
 suspend fun library():List<Song>=withContext(Dispatchers.IO){
  val r=mutableListOf<Song>(); val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  val p=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATA)
  contentResolver.query(base,p,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use{c->
   val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val t=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
   val ar=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val al=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
   val d=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);val path=c.getColumnIndex(MediaStore.Audio.Media.DATA)
   while(c.moveToNext()){val sid=c.getLong(id);val s=if(path>=0)c.getString(path)?:"" else "";r+=Song(sid,c.getString(t)?:"بدون عنوان",c.getString(ar)?:"فنان غير معروف",c.getString(al)?:"ألبوم غير معروف",c.getLong(d),ContentUris.withAppendedId(base,sid),s.substringBeforeLast("/").substringAfterLast("/"))}
  };r
 }
}

@Composable fun App(a:MainActivity,p:ExoPlayer){
 val prefs=a.getSharedPreferences("prefs",Context.MODE_PRIVATE)
 var theme by remember{mutableStateOf(ThemeMode.valueOf(prefs.getString("theme","SYSTEM")!!))}
 var pi by remember{mutableIntStateOf(prefs.getInt("palette",2))}
 var songs by remember{mutableStateOf<List<Song>>(emptyList())};var current by remember{mutableStateOf<Song?>(null)}
 var q by remember{mutableStateOf("")};var tab by remember{mutableIntStateOf(0)};var position by remember{mutableLongStateOf(0)}
 var duration by remember{mutableLongStateOf(1)};var A by remember{mutableStateOf<Long?>(null)};var B by remember{mutableStateOf<Long?>(null)}
 var loops by remember{mutableIntStateOf(0)};var repeat by remember{mutableStateOf("3")};var speed by remember{mutableFloatStateOf(1f)}
 var showNowPlaying by remember{mutableStateOf(false)}
 var favs by remember{mutableStateOf(prefs.getStringSet("favs",emptySet())!!.toSet())}
 val perm=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
 val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it){}}
 LaunchedEffect(Unit){if(ContextCompat.checkSelfPermission(a,perm)==PackageManager.PERMISSION_GRANTED)songs=a.library()else request.launch(perm)}
 LaunchedEffect(Unit){while(true){position=p.currentPosition.coerceAtLeast(0);duration=p.duration.takeIf{it>0}?:1
   if(A!=null&&B!=null&&position>=B!!){
     val max=if(repeat=="∞")Int.MAX_VALUE else repeat.toInt()
     loops++
     if(loops<max)p.seekTo(A!!)else{A=null;B=null;loops=0}
   }
   delay(80)
 }}
 val dark=when(theme){ThemeMode.DARK->true;ThemeMode.LIGHT->false;ThemeMode.SYSTEM->androidx.compose.foundation.isSystemInDarkTheme()}
 val pal=palettes[pi]
 val cs=if(dark)darkColorScheme(primary=pal.dark,secondary=pal.dark)else lightColorScheme(primary=pal.light,secondary=pal.light)

 MaterialTheme(colorScheme=cs){
  if(showNowPlaying && current!=null){
   NowPlayingScreen(
    s=current!!,p=p,pos=position,dur=duration,A=A,B=B,loops=loops,rep=repeat,spd=speed,
    onBack={showNowPlaying=false},
    seek={p.seekTo(it)},
    setA={
      val now=p.currentPosition.coerceAtLeast(0)
      A=now
      if(B!=null && now>=B!!) B=null
      loops=0
    },
    setB={
      val now=p.currentPosition.coerceAtLeast(0)
      if(A!=null && now>=A!!){B=now;loops=0}
    },
    clear={A=null;B=null;loops=0},
    setRep={repeat=it;loops=0},
    setSpeed={speed=it;p.setPlaybackSpeed(it.toDouble())}
   )
  }else{
   Scaffold(bottomBar={
    NavigationBar{
     NavigationBarItem(tab==0,{tab=0},{Icon(Icons.Default.LibraryMusic,null)},{Text("المكتبة")})
     NavigationBarItem(tab==1,{tab=1},{Icon(Icons.Default.Favorite,null)},{Text("المفضلة")})
     NavigationBarItem(tab==2,{tab=2},{Icon(Icons.Default.Settings,null)},{Text("الإعدادات")})
    }
   }){pad->
    if(tab==2)Settings(theme,pi,{theme=it;prefs.edit().putString("theme",it.name).apply()},{pi=it;prefs.edit().putInt("palette",it).apply()})
    else Column(Modifier.fillMaxSize().padding(pad).padding(14.dp)){
     Text("Music Player A-B",fontSize=26.sp,fontWeight=FontWeight.ExtraBold)
     Text(if(tab==0)"مكتبة الموسيقى":"المفضلة",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Spacer(Modifier.height(10.dp))
     OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("ابحث عن أغنية أو فنان أو ألبوم")},leadingIcon={Icon(Icons.Default.Search,null)})
     Spacer(Modifier.height(8.dp))
     Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
      FilterChip(true,{},label={Text("${songs.size} أغنية")})
     }
     val list=songs.filter{(tab==0||favs.contains(it.id.toString()))&&(q.isBlank()||it.title.contains(q,true)||it.artist.contains(q,true)||it.album.contains(q,true)||it.folder.contains(q,true))}
     LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){items(list,key={it.id}){s->
      Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
       .background(if(current?.id==s.id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
       .clickable{
        current=s
        p.setMediaItem(MediaItem.fromUri(s.uri));p.prepare();p.setPlaybackSpeed(speed.toDouble());p.play()
        A=null;B=null;loops=0;showNowPlaying=true
       }.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
       Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary),contentAlignment=Alignment.Center){Icon(Icons.Default.MusicNote,null,tint=Color.White)}
       Spacer(Modifier.width(10.dp))
       Column(Modifier.weight(1f)){Text(s.title,fontWeight=FontWeight.Bold,maxLines=1);Text("${s.artist} • ${s.folder}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
       IconButton({val x=favs.toMutableSet();if(!x.add(s.id.toString()))x.remove(s.id.toString());favs=x;prefs.edit().putStringSet("favs",x).apply()}){
        Icon(if(favs.contains(s.id.toString()))Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)
       }
      }
     }}
     current?.let{s->
      MiniPlayerBar(s,p,position,duration){showNowPlaying=true}
     }
    }
   }
  }
 }
}

@Composable
fun MiniPlayerBar(s:Song,p:ExoPlayer,pos:Long,dur:Long,open:()->Unit){
 ElevatedCard(Modifier.fillMaxWidth().clickable{open()},shape=RoundedCornerShape(20.dp)){
  Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(45.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),contentAlignment=Alignment.Center){
    Icon(Icons.Default.MusicNote,null,tint=Color.White)
   }
   Spacer(Modifier.width(10.dp))
   Column(Modifier.weight(1f)){Text(s.title,fontWeight=FontWeight.Bold,maxLines=1);Text("${fmt(pos)} / ${fmt(dur)}",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   IconButton({if(p.isPlaying)p.pause()else p.play()}){Icon(if(p.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,null)}
   IconButton({open()}){Icon(Icons.Default.OpenInFull,null)}
  }
 }
}

@Composable
fun NowPlayingScreen(
 s:Song,p:ExoPlayer,pos:Long,dur:Long,A:Long?,B:Long?,loops:Int,rep:String,spd:Float,
 onBack:()->Unit,seek:(Long)->Unit,setA:()->Unit,setB:()->Unit,clear:()->Unit,setRep:(String)->Unit,setSpeed:(Float)->Unit
){
 var rm by remember{mutableStateOf(false)}
 var sm by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().statusBarsPadding()){
  Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onBack){Icon(Icons.Default.ArrowBack,null)}
   Column(Modifier.weight(1f)){
    Text("التشغيل الآن",fontSize=18.sp,fontWeight=FontWeight.ExtraBold)
    Text("Music Player A-B",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   IconButton({}){Icon(Icons.Default.MoreVert,null)}
  }
  Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
   Spacer(Modifier.height(10.dp))
   Box(Modifier.size(210.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center){
    Icon(Icons.Default.MusicNote,null,modifier=Modifier.size(92.dp),tint=MaterialTheme.colorScheme.primary)
   }
   Spacer(Modifier.height(18.dp))
   Text(s.title,fontSize=22.sp,fontWeight=FontWeight.ExtraBold,maxLines=1)
   Text(s.artist,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
   Spacer(Modifier.height(14.dp))

   Slider(pos.coerceIn(0,dur).toFloat(),{seek(it.toLong())},valueRange=0f..dur.toFloat())
   Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween){Text(fmt(pos),fontSize=11.sp);Text(fmt(dur),fontSize=11.sp)}

   Spacer(Modifier.height(8.dp))
   Row(Modifier.fillMaxWidth(),Arrangement.SpaceEvenly,Alignment.CenterVertically){
    IconButton({p.seekBack()}){Icon(Icons.Default.Replay5,null)}
    IconButton({p.seekToPreviousMediaItem()}){Icon(Icons.Default.SkipPrevious,null)}
    FilledIconButton({if(p.isPlaying)p.pause()else p.play()},Modifier.size(66.dp)){Icon(if(p.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,null,modifier=Modifier.size(32.dp))}
    IconButton({p.seekToNextMediaItem()}){Icon(Icons.Default.SkipNext,null)}
    IconButton({p.seekForward()}){Icon(Icons.Default.Forward5,null)}
   }

   Spacer(Modifier.height(12.dp))
   Text("تحديد التكرار A-B",fontWeight=FontWeight.Bold,fontSize=14.sp)
   Text("يمكن تحديد A و B أثناء التشغيل أو بعد الإيقاف المؤقت. كل زر يأخذ موضع الأغنية الحالي لحظة الضغط.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)

   Spacer(Modifier.height(8.dp))
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
    FilledTonalButton(setA,Modifier.weight(1f).height(58.dp),shape=RoundedCornerShape(18.dp)){
     Column(horizontalAlignment=Alignment.CenterHorizontally){
      Text("A",fontSize=21.sp,fontWeight=FontWeight.ExtraBold)
      Text(A?.let(::fmt)?:"تعيين النقطة",fontSize=10.sp)
     }
    }
    FilledTonalButton(setB,Modifier.weight(1f).height(58.dp),shape=RoundedCornerShape(18.dp),enabled=A!=null){
     Column(horizontalAlignment=Alignment.CenterHorizontally){
      Text("B",fontSize=21.sp,fontWeight=FontWeight.ExtraBold)
      Text(B?.let(::fmt)?:"تعيين النقطة",fontSize=10.sp)
     }
    }
   }

   Spacer(Modifier.height(7.dp))
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedButton(clear,Modifier.weight(1f)){Text("مسح A-B")}
    Box(Modifier.weight(1f)){
     OutlinedButton({rm=true},Modifier.fillMaxWidth()){Text(if(rep=="∞")"لا نهائي" else "$rep مرات")}
     DropdownMenu(rm,{rm=false}){
      listOf("3","5","7","11","∞").forEach{x->DropdownMenuItem({Text(if(x=="∞")"لا نهائي" else "$x مرات")},{setRep(x);rm=false})}
     }
    }
    Box(Modifier.weight(1f)){
     OutlinedButton({sm=true},Modifier.fillMaxWidth()){Text("${"%.2f".format(spd)}x")}
     DropdownMenu(sm,{sm=false}){listOf(.5f,.75f,1f,1.25f,1.5f,2f).forEach{x->
      DropdownMenuItem({Text("${"%.2f".format(x)}x")},{setSpeed(x);sm=false})
     }}
    }
   }

   Spacer(Modifier.height(7.dp))
   ElevatedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){
    Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
     Column{
      Text(if(A!=null&&B!=null)"A-B مفعّل" else "A-B غير مكتمل",fontWeight=FontWeight.Bold,fontSize=12.sp)
      Text(if(A!=null&&B!=null)"من ${fmt(A!!)} إلى ${fmt(B!!)}" else "اضغط A ثم B لتحديد المقطع",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
     if(A!=null&&B!=null)Text("$loops / $rep",fontWeight=FontWeight.Bold,fontSize=11.sp)
    }
   }
   Spacer(Modifier.height(8.dp))
   Text("يمكنك سحب شريط التقدم لتغيير موضع الأغنية في أي وقت.",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}

@Composable fun Settings(theme:ThemeMode,pi:Int,onTheme:(ThemeMode)->Unit,onPalette:(Int)->Unit){
 LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Text("الإعدادات",fontSize=27.sp,fontWeight=FontWeight.ExtraBold);Text("Music Player A-B",color=MaterialTheme.colorScheme.onSurfaceVariant)}
  item{ElevatedCard(shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp)){Text("المظهر",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf(ThemeMode.LIGHT to "فاتح",ThemeMode.DARK to "غامق",ThemeMode.SYSTEM to "تلقائي").forEach{(m,n)->FilterChip(theme==m,{onTheme(m)},{Text(n)})}}}}}
  item{ElevatedCard(shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp)){Text("ألوان الواجهة",fontWeight=FontWeight.Bold);Text("ألوان متعددة متناسقة للفَاتح والغامق",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(12.dp));palettes.chunked(5).forEach{row->Row(Modifier.fillMaxWidth(),Arrangement.SpaceEvenly){row.forEach{p->val i=palettes.indexOf(p);Box(Modifier.size(40.dp).clip(CircleShape).background(if(androidx.compose.foundation.isSystemInDarkTheme())p.dark else p.light).clickable{onPalette(i)},contentAlignment=Alignment.Center){if(pi==i)Icon(Icons.Default.Check,null,tint=Color.White)}}}}}}}}
  item{Text("A-B: 3 / 5 / 7 / 11 / لا نهائي • مكتبة الهاتف • المفضلة • سرعة التشغيل • التشغيل في الخلفية",fontSize=12.sp)}
 }
}
fun fmt(ms:Long):String{val s=(ms.coerceAtLeast(0)/1000);return "%02d:%02d".format(s/60,s%60)}
