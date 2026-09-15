from pathlib import Path
import re

p=Path('app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryActivity.kt')
t=p.read_text(encoding='utf-8')
t=t.replace('    var selectedIndex by rememberSaveable { mutableIntStateOf(-1) }\n','')
old='            Box(Modifier.weight(1f)) { MediaViewer(selectedUri!!, selectedIsVideo, media, selectedIndex, { index -> if (index in media.indices) { selectedIndex = index; selectedUri = media[index].uri; selectedIsVideo = media[index].isVideo } }, { selectedUri = null; selectedIndex = -1 }, { shareMedia(context, selectedUri!!) }, if (selectedIsVideo) null else ({ onEdit(selectedUri!!) })) { val uriBeingDeleted = selectedUri!!; onDelete(uriBeingDeleted) { deleted -> if (deleted) { selectedUri = null; selectedIndex = -1; refreshToken++ } } } }'
new='            Box(Modifier.weight(1f)) { MediaViewer(selectedUri!!, selectedIsVideo, { selectedUri = null }, { shareMedia(context, selectedUri!!) }, if (selectedIsVideo) null else ({ onEdit(selectedUri!!) })) { val uriBeingDeleted = selectedUri!!; onDelete(uriBeingDeleted) { deleted -> if (deleted) { selectedUri = null; refreshToken++ } } } }'
t=t.replace(old,new,1).replace(' else { selectedIndex = media.indexOfFirst { it.uri == item.uri }; selectedUri = item.uri; selectedIsVideo = item.isVideo } }',' else { selectedUri = item.uri; selectedIsVideo = item.isVideo } }')
s=t.find('@Composable private fun MediaViewer(');e=t.find('@Composable private fun VideoPlayer(',s)
if s<0 or e<0: raise SystemExit('viewer boundary missing')
v=r'''@Composable private fun MediaViewer(uri: Uri,isVideo:Boolean,onBack:()->Unit,onShare:()->Unit,onEdit:(()->Unit)?,onDelete:()->Unit){
 var confirmDelete by rememberSaveable(uri){mutableStateOf(false)};var mediaZoom by rememberSaveable(uri){mutableFloatStateOf(1f)};var mediaPanX by rememberSaveable(uri){mutableFloatStateOf(0f)};var mediaPanY by rememberSaveable(uri){mutableFloatStateOf(0f)};var videoSpeed by rememberSaveable(uri){mutableFloatStateOf(1f)};var speedMenuOpen by rememberSaveable(uri){mutableStateOf(false)};var viewportWidth by remember(uri){mutableIntStateOf(0)};var viewportHeight by remember(uri){mutableIntStateOf(0)};val context=LocalContext.current
 Surface(Modifier.fillMaxSize(),color=Color.Black){Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().padding(8.dp).zIndex(10f),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)};Spacer(Modifier.weight(1f));IconButton(onClick=onShare){Icon(Icons.Default.Share,"Share",tint=Color.White)};if(isVideo)Box{IconButton(onClick={speedMenuOpen=true}){Icon(Icons.Default.Speed,"Playback speed",tint=Color.White)};DropdownMenu(speedMenuOpen,{speedMenuOpen=false}){listOf(.5f,.75f,1f,1.25f,1.5f,2f).forEach{sp->DropdownMenuItem(text={Text("${sp}x",fontWeight=if(videoSpeed==sp)FontWeight.Bold else FontWeight.Normal)},onClick={videoSpeed=sp;speedMenuOpen=false})}}}else onEdit?.let{IconButton(onClick=it){Icon(Icons.Default.Edit,"Edit",tint=Color.White)}};IconButton(onClick={confirmDelete=true}){Icon(Icons.Default.Delete,"Delete",tint=Color.White)}}
  Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged{viewportWidth=it.width;viewportHeight=it.height},contentAlignment=Alignment.Center){Box(Modifier.fillMaxSize().graphicsLayer{translationX=mediaPanX;translationY=mediaPanY;scaleX=mediaZoom;scaleY=mediaZoom}.pointerInput(uri){detectTransformGestures{centroid,pan,zoom,_->val old=mediaZoom;val zr=zoom.coerceIn(.5f,2f);val nz=(old*zr).coerceIn(1f,8f);val fx=centroid.x-viewportWidth/2f;val fy=centroid.y-viewportHeight/2f;val sr=if(old>0f)nz/old else 1f;val mx=viewportWidth.toFloat()*(nz-1f)/2f;val my=viewportHeight.toFloat()*(nz-1f)/2f;if(zr!=1f){mediaPanX=((mediaPanX+fx)*sr-fx+pan.x).coerceIn(-mx,mx);mediaPanY=((mediaPanY+fy)*sr-fy+pan.y).coerceIn(-my,my)}else if(old>1f){mediaPanX=(mediaPanX+pan.x).coerceIn(-mx,mx);mediaPanY=(mediaPanY+pan.y).coerceIn(-my,my)}else{mediaPanX=0f;mediaPanY=0f};mediaZoom=nz}}){if(isVideo)VideoPlayer(uri,videoSpeed)else CachedFullImage(uri,context)}}}
 };if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete media?")},text={Text("Delete this ${if(isVideo)"video"else"photo"} from your device? This action cannot be undone.")},confirmButton={TextButton(onClick={confirmDelete=false;onDelete()}){Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})}}
@Composable private fun CachedFullImage(uri:Uri,context:Context){var bitmap by remember(uri){mutableStateOf(ThumbnailMemoryCache.getFull(uri)?:ThumbnailMemoryCache.get(uri,false))};LaunchedEffect(uri){ThumbnailMemoryCache.getFull(uri)?.let{bitmap=it;return@LaunchedEffect};withContext(Dispatchers.IO){loadFullImage(context,uri)?.let{full->ThumbnailMemoryCache.putFull(uri,full);withContext(Dispatchers.Main){bitmap=full}}}};bitmap?.let{Image(it.asImageBitmap(),"Photo",Modifier.fillMaxSize().padding(8.dp),contentScale=ContentScale.Fit)}}

'''
t=t[:s]+v+t[e:]
for x in ['import androidx.compose.foundation.gestures.detectTransformGestures','import androidx.compose.ui.zIndex','import androidx.compose.ui.layout.onSizeChanged','import kotlinx.coroutines.launch']:
 if x not in t:t=t.replace('import androidx.compose.foundation.layout.*',x+'\nimport androidx.compose.foundation.layout.*',1)
if any(x in t for x in ['selectedIndex','currentIndex','onNavigate(','detectHorizontalDragGestures','swipeOffset','videoNavigationStarted','AdjacentMedia(']):raise SystemExit('stale gallery navigation remains')
p.write_text(t,encoding='utf-8')

ep=Path('app/src/main/java/com/devlinguistpro/mediatoolbox/GalleryEditorActivity.kt');et=ep.read_text(encoding='utf-8')
# Crop is a one-finger drag interaction. Earlier crop code used transform gestures,
# and that is the specific overlap that caused the last 13-second failure.
et=et.replace('import androidx.compose.foundation.gestures.detectTransformGestures','import androidx.compose.foundation.gestures.detectDragGestures')
et=et.replace('androidx.compose.foundation.gestures.detectTransformGestures','detectTransformGestures')
# Handle the exact old callback form used by the crop overlay.
et=et.replace('detectTransformGestures { centroid, pan, _, _ ->','detectDragGestures { change, dragAmount ->\n            val centroid = change.position\n            val pan = dragAmount')
et=et.replace('detectTransformGestures { centroid, pan, _, _ ->','detectDragGestures { change, dragAmount ->\n            val centroid = change.position\n            val pan = dragAmount')
# If the generated source has a transform callback with a different parameter list,
# replace the detector/callback header without touching the crop body.
et=re.sub(r'detectTransformGestures\s*\{\s*centroid\s*,\s*pan\s*,[^\n]*?->', 'detectDragGestures { change, dragAmount ->\n            val centroid = change.position\n            val pan = dragAmount', et)
if 'detectTransformGestures' in et:raise SystemExit('crop transform detector remains')
if 'detectDragGestures' not in et:raise SystemExit('crop drag detector missing')
ep.write_text(et,encoding='utf-8')
print('PASS final gallery/crop generation')
