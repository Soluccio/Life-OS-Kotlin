package br.com.lifeos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Bg=Color(0xFF10131B); private val Panel=Color(0xFF191E29); private val Panel2=Color(0xFF202737)
private val Blue=Color(0xFF55B8FF); private val Gold=Color(0xFFF4C96B); private val Green=Color(0xFF55D6A0); private val Muted=Color(0xFF9AA7BA)

data class Mission(val id:String,val title:String,val xp:Int,val essential:Boolean=false)
data class Reward(val name:String,val cost:Int,var claimed:Int=0)
data class Achievement(val id:String,val icon:String,val name:String,val description:String,val unlocked:Boolean)

enum class Screen(val label:String){Dashboard("Início"),Weekly("Missões"),Progress("Evolução"),Calendar("Calendário"),Focus("Foco")}

class Store(context:Context){
    private val p=context.getSharedPreferences("life_os_native",Context.MODE_PRIVATE)
    var totalXp:Int get()=p.getInt("xp",0); set(v){p.edit().putInt("xp",v).apply()}
    var spentXp:Int get()=p.getInt("spent",0); set(v){p.edit().putInt("spent",v).apply()}
    fun completed(date:LocalDate):MutableSet<String> = p.getStringSet("day_$date", emptySet())!!.toMutableSet()
    fun toggle(date:LocalDate,m:Mission):Boolean{val set=completed(date);val added=if(set.remove(m.id)){totalXp=(totalXp-m.xp).coerceAtLeast(0);false}else{set.add(m.id);totalXp+=m.xp;true};p.edit().putStringSet("day_$date",set).apply();return added}
    fun activeDays():Int=p.all.keys.count{it.startsWith("day_")&&(p.getStringSet(it,emptySet())?.isNotEmpty()==true)}
    fun rewards():MutableList<Reward>{val a=JSONArray(p.getString("rewards","[]"));return MutableList(a.length()){i->val o=a.getJSONObject(i);Reward(o.getString("name"),o.getInt("cost"),o.optInt("claimed"))}}
    fun saveRewards(list:List<Reward>){val a=JSONArray();list.forEach{a.put(JSONObject().put("name",it.name).put("cost",it.cost).put("claimed",it.claimed))};p.edit().putString("rewards",a.toString()).apply()}
    fun unlocked():MutableSet<String> = p.getStringSet("achievements",emptySet())!!.toMutableSet()
    fun saveUnlocked(s:Set<String>)=p.edit().putStringSet("achievements",s).apply()
}

private val missions=listOf(
 Mission("water","Beber água antes do PC",5,true),Mission("food","Comer alguma coisa",10,true),Mission("bath","Tomar banho morno",10,true),Mission("change","Trocar de roupa",5),Mission("study","Estudar por 25 minutos",15),Mission("home","Tarefa doméstica por 10 minutos",10),Mission("workout","Calistenia por 20 minutos",20),Mission("teeth","Escovar os dentes e desacelerar",10,true),Mission("bed","Deitar no horário-alvo",5),Mission("rest","Descanso livre, sem culpa",5)
)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);createChannel();setContent{LifeOS(Store(this),this)}}
 private fun createChannel(){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("timer","Temporizador",NotificationManager.IMPORTANCE_DEFAULT))}
 fun timerFinished(){(getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createWaveform(longArrayOf(0,250,120,250),-1));getSystemService(NotificationManager::class.java).notify(25,NotificationCompat.Builder(this,"timer").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Life OS").setContentText("Sessão concluída. Hora de respirar.").setPriority(NotificationCompat.PRIORITY_DEFAULT).build())}
}

@Composable fun LifeOS(store:Store,activity:MainActivity){
 var screen by remember{mutableStateOf(Screen.Dashboard)};var revision by remember{mutableIntStateOf(0)}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
 LaunchedEffect(Unit){if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(activity,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)launcher.launch(Manifest.permission.POST_NOTIFICATIONS)}
 MaterialTheme(colorScheme=darkColorScheme(primary=Blue,secondary=Gold,background=Bg,surface=Panel,onBackground=Color(0xFFF1F5FB),onSurface=Color(0xFFF1F5FB))){
  Scaffold(containerColor=Bg,bottomBar={NavigationBar(containerColor=Color(0xFF111722)){Screen.entries.forEach{s->NavigationBarItem(selected=screen==s,onClick={screen=s},icon={Icon(when(s){Screen.Dashboard->Icons.Default.Home;Screen.Weekly->Icons.Default.Checklist;Screen.Progress->Icons.Default.ShowChart;Screen.Calendar->Icons.Default.CalendarMonth;Screen.Focus->Icons.Default.Timer},null)},label={Text(s.label,fontSize=9.sp)})}}}){pad->
   Column(Modifier.fillMaxSize().padding(pad).background(Brush.radialGradient(listOf(Color(0xFF1D2B42),Bg))).padding(horizontal=14.dp)){
    Row(Modifier.fillMaxWidth().padding(vertical=15.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=Blue){Text("⚡",fontSize=24.sp,modifier=Modifier.padding(9.dp))};Spacer(Modifier.width(10.dp));Column{Text("LIFE OS",fontWeight=FontWeight.Bold,fontSize=20.sp);Text("SUA VIDA EM MODO CAMPANHA",fontSize=9.sp,color=Muted)};Spacer(Modifier.weight(1f));Text(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM",Locale("pt","BR"))),color=Muted)}
    when(screen){
     Screen.Dashboard->Dashboard(store,revision){revision++}
     Screen.Weekly->Weekly(store,revision){revision++}
     Screen.Progress->Progress(store,revision)
     Screen.Calendar->CalendarView(store,revision)
     Screen.Focus->Focus(store,activity,revision){revision++}
    }
   }
  }
 }
}

@Composable fun CardBox(content:@Composable ColumnScope.()->Unit)=Card(colors=CardDefaults.cardColors(Panel),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(17.dp),content=content)}

@Composable fun Dashboard(store:Store,rev:Int,onChange:()->Unit){
 val xp=store.totalXp;val level=xp/100+1;val progress=(xp%100)/100f
 LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=14.dp)){
  item{CardBox{Row{Column(Modifier.weight(1f)){Text("PAINEL DO JOGADOR",fontSize=10.sp,color=Blue,fontWeight=FontWeight.Bold);Text("Sua campanha continua.",fontSize=23.sp,fontWeight=FontWeight.Bold);Text("Consistência sem sacrificar descanso.",color=Muted,fontSize=12.sp)};AssistChip(onClick={},label={Text("NÍVEL $level")})};Spacer(Modifier.height(16.dp));LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth(),color=Blue,trackColor=Panel2);Text("${xp%100} / 100 XP",fontSize=11.sp,color=Muted,modifier=Modifier.align(Alignment.End))}}
  item{Text("🏆 Chefe final do dia",fontSize=18.sp,fontWeight=FontWeight.Bold)}
  items(missions.take(4)){m->MissionRow(store,LocalDate.now(),m,rev,onChange)}
  item{Text("🏅 Conquistas",fontSize=18.sp,fontWeight=FontWeight.Bold);Achievements(store,rev)}
 }
}

@Composable fun MissionRow(store:Store,date:LocalDate,m:Mission,rev:Int,onChange:()->Unit){val done=store.completed(date).contains(m.id);Surface(color=if(done)Color(0xFF17352D) else Color(0xFF141B27),shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth().clickable{store.toggle(date,m);onChange()}){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(done,{store.toggle(date,m);onChange()});Text(m.title,Modifier.weight(1f),fontSize=12.sp,color=if(done)Muted else Color.White);if(m.essential)Text("★ ",color=Gold);Text("+${m.xp} XP",color=Blue,fontSize=10.sp,fontWeight=FontWeight.Bold)}}}

@Composable fun Weekly(store:Store,rev:Int,onChange:()->Unit){var selected by remember{mutableStateOf(LocalDate.now())};LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=14.dp)){item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){(-3..3).forEach{d->val date=LocalDate.now().plusDays(d.toLong());FilterChip(selected=date==selected,onClick={selected=date},label={Column(horizontalAlignment=Alignment.CenterHorizontally){Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale("pt","BR")));Text(date.dayOfMonth.toString())}})}}};item{Text(selected.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM",Locale("pt","BR"))),fontWeight=FontWeight.Bold,fontSize=18.sp)};items(missions){MissionRow(store,selected,it,rev,onChange)}}}

@Composable fun Progress(store:Store,rev:Int){val today=LocalDate.now();val start=today.minusDays(((today.dayOfWeek.value-1).toLong()));val values=(0..6).map{i->store.completed(start.plusDays(i.toLong())).sumOf{id->missions.find{it.id==id}?.xp?:0}};val max=(values.maxOrNull()?:1).coerceAtLeast(1);LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){item{CardBox{Text("Evolução desta semana",fontWeight=FontWeight.Bold,fontSize=19.sp);Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){Metric("${values.sum()}","XP");Metric("${values.count{it>0}}/7","DIAS");Metric("${store.totalXp}","TOTAL")};Spacer(Modifier.height(16.dp));Row(Modifier.fillMaxWidth().height(150.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Bottom){values.forEachIndexed{i,v->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(v.toString(),fontSize=9.sp);Box(Modifier.fillMaxWidth().height((120f*v/max).coerceAtLeast(3f).dp).background(Blue,RoundedCornerShape(7.dp)));Text(start.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW,Locale("pt","BR")),color=Muted)}}}}};item{Achievements(store,rev)}}}
@Composable fun RowScope.Metric(value:String,label:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(label,fontSize=9.sp,color=Muted)}}

@Composable fun Achievements(store:Store,rev:Int){val active=store.activeDays();val doneAll=(0..30).sumOf{store.completed(LocalDate.now().minusDays(it.toLong())).size};val defs=listOf(Achievement("first","🌟","Primeiro passo","Concluir 1 missão",doneAll>=1),Achievement("five","🔥","Semana viva","Atividade em 5 dias",active>=5),Achievement("veteran","👑","Veterano","Acumular 500 XP",store.totalXp>=500),Achievement("routine","⚔️","Chefe vencido","Concluir 20 missões",doneAll>=20));val unlocked=store.unlocked();defs.filter{it.unlocked}.forEach{unlocked.add(it.id)};store.saveUnlocked(unlocked);Column(verticalArrangement=Arrangement.spacedBy(8.dp)){defs.forEach{a->Surface(color=if(a.unlocked)Color(0xFF302A1D) else Color(0xFF141B27),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(13.dp)){Text(a.icon,fontSize=24.sp);Spacer(Modifier.width(10.dp));Column{Text(a.name,fontWeight=FontWeight.Bold,color=if(a.unlocked)Gold else Muted);Text(a.description,fontSize=10.sp,color=Muted)}}}}}}

@Composable fun CalendarView(store:Store,rev:Int){var month by remember{mutableStateOf(YearMonth.now())};val first=month.atDay(1);val blanks=first.dayOfWeek.value-1;LazyColumn{item{CardBox{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton({month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,null)};Text(month.month.getDisplayName(TextStyle.FULL,Locale("pt","BR")) + " ${month.year}",Modifier.weight(1f),fontWeight=FontWeight.Bold);IconButton({month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,null)}};Row{listOf("S","T","Q","Q","S","S","D").forEach{Text(it,Modifier.weight(1f),color=Muted)}};val cells=(List(blanks){null}+ (1..month.lengthOfMonth()).map{month.atDay(it)});cells.chunked(7).forEach{week->Row{(week+List(7-week.size){null}).forEach{date->val count=date?.let{store.completed(it).size}?:0;Surface(color=if(count>0)Color(0xFF17352D)else Color.Transparent,shape=RoundedCornerShape(8.dp),modifier=Modifier.weight(1f).aspectRatio(1f).padding(2.dp)){Box(contentAlignment=Alignment.Center){if(date!=null)Column(horizontalAlignment=Alignment.CenterHorizontally){Text(date.dayOfMonth.toString());if(count>0)Text("✓$count",fontSize=8.sp,color=Green)}}}}}}}}}}

@Composable fun Focus(store:Store,activity:MainActivity,rev:Int,onChange:()->Unit){var total by remember{mutableIntStateOf(1500)};var left by remember{mutableIntStateOf(1500)};var running by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var cost by remember{mutableStateOf("")};var rewards by remember(rev){mutableStateOf(store.rewards())};LaunchedEffect(running,left){if(running&&left>0){delay(1000);left--}else if(running&&left==0){running=false;activity.timerFinished()}};LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)){item{CardBox{Text("Temporizador integrado",fontWeight=FontWeight.Bold);Text(String.format("%02d:%02d",left/60,left%60),fontSize=64.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.CenterHorizontally));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf(10,20,25).forEach{m->OutlinedButton({running=false;total=m*60;left=total}){Text("${m}m")}}};Button({running=!running},Modifier.fillMaxWidth()){Text(if(running)"Pausar" else "Iniciar")}}};item{CardBox{Text("Recompensas · ${store.totalXp-store.spentXp} XP disponíveis",fontWeight=FontWeight.Bold);OutlinedTextField(name,{name=it},label={Text("Recompensa")},modifier=Modifier.fillMaxWidth());OutlinedTextField(cost,{cost=it.filter(Char::isDigit)},label={Text("Custo em XP")},modifier=Modifier.fillMaxWidth());Button({val c=cost.toIntOrNull();if(name.isNotBlank()&&c!=null&&c>0){rewards.add(Reward(name,c));store.saveRewards(rewards);name="";cost="";onChange()}},Modifier.fillMaxWidth()){Text("Adicionar")};rewards.forEachIndexed{i,r->Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Text("🎁 ${r.name}\n${r.cost} XP",Modifier.weight(1f));Button({if(store.totalXp-store.spentXp>=r.cost){store.spentXp+=r.cost;r.claimed++;store.saveRewards(rewards);onChange()}},enabled=store.totalXp-store.spentXp>=r.cost){Text("Resgatar")};IconButton({rewards.removeAt(i);store.saveRewards(rewards);onChange()}){Icon(Icons.Default.Delete,null)}}}}}}
}
