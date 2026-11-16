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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val Ink = Color(0xFF080B12)
private val Navy = Color(0xFF0D1421)
private val Surface = Color(0xFF131D2D)
private val Surface2 = Color(0xFF19263A)
private val Cyan = Color(0xFF48C6EF)
private val Blue = Color(0xFF6C63FF)
private val Mint = Color(0xFF56E0B5)
private val Amber = Color(0xFFFFCE68)
private val Rose = Color(0xFFFF719A)
private val Text = Color(0xFFF4F7FB)
private val Muted = Color(0xFF8F9DB1)
private val Border = Color(0xFF26354B)

data class Mission(val id: String, val title: String, val subtitle: String, val xp: Int, val icon: String, val essential: Boolean = false)
data class Reward(val name: String, val cost: Int, var claimed: Int = 0)
data class Achievement(val id: String, val icon: String, val name: String, val description: String, val unlocked: Boolean)
enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("Início", Icons.Default.Home), Weekly("Missões", Icons.Default.Checklist),
    Progress("Evolução", Icons.AutoMirrored.Filled.ShowChart), Calendar("Calendário", Icons.Default.CalendarMonth),
    Focus("Foco", Icons.Default.Timer)
}

class Store(context: Context) {
    private val p = context.getSharedPreferences("life_os_native", Context.MODE_PRIVATE)
    var totalXp: Int get() = p.getInt("xp", 0); set(v) { p.edit().putInt("xp", v).apply() }
    var spentXp: Int get() = p.getInt("spent", 0); set(v) { p.edit().putInt("spent", v).apply() }
    fun completed(date: LocalDate): MutableSet<String> = p.getStringSet("day_$date", emptySet())!!.toMutableSet()
    fun toggle(date: LocalDate, m: Mission) {
        val set = completed(date)
        if (set.remove(m.id)) totalXp = (totalXp - m.xp).coerceAtLeast(0) else { set.add(m.id); totalXp += m.xp }
        p.edit().putStringSet("day_$date", set).apply()
    }
    fun activeDays(): Int = p.all.keys.count { it.startsWith("day_") && p.getStringSet(it, emptySet())?.isNotEmpty() == true }
    fun rewards(): MutableList<Reward> {
        val a = JSONArray(p.getString("rewards", "[]")); return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i); Reward(o.getString("name"), o.getInt("cost"), o.optInt("claimed"))
        }
    }
    fun saveRewards(list: List<Reward>) {
        val a = JSONArray(); list.forEach { a.put(JSONObject().put("name", it.name).put("cost", it.cost).put("claimed", it.claimed)) }
        p.edit().putString("rewards", a.toString()).apply()
    }
}

private val missions = listOf(
    Mission("water", "Beber água", "Hidratação antes da primeira tela", 5, "💧", true),
    Mission("food", "Fazer uma refeição", "Energia para continuar a campanha", 10, "🥗", true),
    Mission("bath", "Ritual de chegada", "Banho e troca de roupa", 10, "🚿", true),
    Mission("study", "Bloco de estudo", "25 minutos sem interrupções", 15, "📚"),
    Mission("home", "Missão doméstica", "10 minutos para organizar o ambiente", 10, "🏠"),
    Mission("workout", "Treino rápido", "20 minutos de movimento", 20, "⚡"),
    Mission("teeth", "Desacelerar", "Higiene e preparação para o descanso", 10, "🌙", true),
    Mission("bed", "Horário-alvo", "Encerrar o dia no horário combinado", 5, "🛏️"),
    Mission("rest", "Descanso consciente", "Pausa livre, sem culpa", 5, "🎧")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); createChannel(); setContent { LifeOS(Store(this), this) } }
    private fun createChannel() = getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("timer", "Temporizador", NotificationManager.IMPORTANCE_DEFAULT))
    fun timerFinished() {
        getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
        getSystemService(NotificationManager::class.java).notify(25, NotificationCompat.Builder(this, "timer").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Sessão concluída").setContentText("Respire, registre o progresso e escolha o próximo passo.").build())
    }
}

@Composable fun LifeOS(store: Store, activity: MainActivity) {
    var screen by remember { mutableStateOf(Screen.Dashboard) }; var revision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Blue, background = Ink, surface = Surface, onSurface = Text, onBackground = Text)) {
        Scaffold(containerColor = Ink, bottomBar = { PremiumNav(screen) { screen = it } }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).background(Brush.verticalGradient(listOf(Navy, Ink)))) {
                Box(Modifier.size(260.dp).offset(x = 170.dp, y = (-100).dp).background(Brush.radialGradient(listOf(Blue.copy(.19f), Color.Transparent)), CircleShape))
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    AppHeader(store.totalXp)
                    AnimatedContent(targetState = screen, label = "screen") { selected ->
                        when (selected) {
                            Screen.Dashboard -> Dashboard(store, revision) { revision++ }
                            Screen.Weekly -> Weekly(store, revision) { revision++ }
                            Screen.Progress -> Progress(store, revision)
                            Screen.Calendar -> CalendarView(store, revision)
                            Screen.Focus -> Focus(store, activity, revision) { revision++ }
                        }
                    }
                }
            }
        }
    }
}

}
@Composable fun AppHeader(xp: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).shadow(12.dp, RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Cyan, Blue)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("⚡", fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp)); Column { Text("LIFE OS", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp); Text("MODO CAMPANHA", fontSize = 9.sp, color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp) }
        Spacer(Modifier.weight(1f)); Surface(color = Surface2, shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) { Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) { Text("✦", color = Amber); Spacer(Modifier.width(5.dp)); Text("$xp XP", fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
    }
}

@Composable fun PremiumNav(selected: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(containerColor = Color(0xF20B101A), tonalElevation = 0.dp) { Screen.entries.forEach { s ->
        NavigationBarItem(selected = selected == s, onClick = { onSelect(s) }, icon = { Icon(s.icon, null, Modifier.size(21.dp)) }, label = { Text(s.label, fontSize = 9.sp, fontWeight = if (selected == s) FontWeight.Bold else FontWeight.Normal) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Text, indicatorColor = Cyan.copy(.12f), unselectedIconColor = Muted, unselectedTextColor = Muted))
    } }

@Composable fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(Surface.copy(.92f)), border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(.8f))) { Column(Modifier.padding(17.dp), content = content) }
@Composable fun SectionTitle(eyebrow: String, title: String, action: String? = null) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) { Column(Modifier.weight(1f)) { Text(eyebrow.uppercase(), color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp); Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }; if (action != null) Text(action, color = Muted, fontSize = 11.sp) } }

@Composable fun Dashboard(store: Store, rev: Int, onChange: () -> Unit) {
    val today = LocalDate.now(); val done = store.completed(today); val xp = store.totalXp; val level = xp / 100 + 1; val progress by animateFloatAsState((xp % 100) / 100f, tween(650), label = "xp")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { GlassCard(Modifier.background(Brush.linearGradient(listOf(Blue.copy(.08f), Cyan.copy(.03f))), RoundedCornerShape(22.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(greeting(), color = Muted, fontSize = 12.sp); Text("Continue de onde parou.", fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp) }; LevelRing(level, progress) }
            Spacer(Modifier.height(18.dp)); Row { MiniMetric("${done.size}/${missions.size}", "MISSÕES", Mint, Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); MiniMetric("${missions.filter { done.contains(it.id) }.sumOf { it.xp }}", "XP HOJE", Cyan, Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); MiniMetric("${store.activeDays()}", "DIAS ATIVOS", Amber, Modifier.weight(1f)) }
        } }
        item { SectionTitle("Prioridades", "Próximas missões", "${done.size} concluídas") }
        items(missions.filterNot { done.contains(it.id) }.take(4), key = { it.id }) { MissionRow(store, today, it, rev, onChange) }
        if (done.isNotEmpty()) item { SectionTitle("Progresso", "Concluídas hoje") }
        items(missions.filter { done.contains(it.id) }.take(3), key = { "done_${it.id}" }) { MissionRow(store, today, it, rev, onChange) }
        item { WeeklyPulse(store) }
    }
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) { in 5..11 -> "Bom dia, jogador"; in 12..17 -> "Boa tarde, jogador"; else -> "Boa noite, jogador" }

@Composable fun LevelRing(level: Int, progress: Float) { Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = Cyan, trackColor = Border, strokeWidth = 7.dp); Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$level", fontSize = 22.sp, fontWeight = FontWeight.Black); Text("NÍVEL", fontSize = 8.sp, color = Muted) } } }
@Composable fun MiniMetric(value: String, label: String, accent: Color, modifier: Modifier = Modifier) { Surface(modifier, color = accent.copy(.08f), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(.22f))) { Column(Modifier.padding(11.dp)) { Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = accent); Text(label, fontSize = 8.sp, color = Muted) } } }

@Composable fun MissionRow(store: Store, date: LocalDate, m: Mission, rev: Int, onChange: () -> Unit) {
    val done = store.completed(date).contains(m.id); val bg by animateColorAsState(if (done) Mint.copy(.10f) else Surface, label = "missionColor")
    Surface(color = bg, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (done) Mint.copy(.35f) else Border), modifier = Modifier.fillMaxWidth().clickable { store.toggle(date, m); onChange() }) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(if (done) Mint.copy(.14f) else Surface2, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(if (done) "✓" else m.icon, fontSize = 20.sp, color = Mint) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(m.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); if (m.essential) { Spacer(Modifier.width(6.dp)); Text("ESSENCIAL", color = Amber, fontSize = 7.sp, fontWeight = FontWeight.Bold) } }; Text(m.subtitle, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Surface(color = if (done) Mint.copy(.14f) else Cyan.copy(.10f), shape = RoundedCornerShape(50)) { Text(if (done) "FEITO" else "+${m.xp} XP", color = if (done) Mint else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) }
        }
    }
}

@Composable fun WeeklyPulse(store: Store) { val start = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()); val values = (0..6).map { store.completed(start.plusDays(it.toLong())).size }; GlassCard { SectionTitle("Pulso semanal", "Sua constância"); Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) { values.forEachIndexed { i, v -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.width(24.dp).height((12 + v * 9).dp).background(if (v > 0) Brush.verticalGradient(listOf(Cyan, Blue)) else Brush.verticalGradient(listOf(Border, Border)), RoundedCornerShape(8.dp))); Spacer(Modifier.height(6.dp)); Text(start.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("pt", "BR")), color = if (i == LocalDate.now().dayOfWeek.value - 1) Cyan else Muted, fontSize = 9.sp) } } } } }

@Composable fun Weekly(store: Store, rev: Int, onChange: () -> Unit) { var selected by remember { mutableStateOf(LocalDate.now()) }; val scroll = rememberScrollState(); LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { item { SectionTitle("Planejamento", "Missões da semana"); Spacer(Modifier.height(12.dp)); Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (-3..3).forEach { d -> val date = LocalDate.now().plusDays(d.toLong()); val chosen = date == selected; Surface(color = if (chosen) Cyan else Surface, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (chosen) Cyan else Border), modifier = Modifier.width(58.dp).clickable { selected = date }) { Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR")).uppercase(), fontSize = 8.sp, color = if (chosen) Ink else Muted); Text(date.dayOfMonth.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (chosen) Ink else Text) } } } } }; item { Text(selected.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "BR"))).replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 12.sp) }; items(missions, key = { it.id }) { MissionRow(store, selected, it, rev, onChange) } } }

@Composable fun Progress(store: Store, rev: Int) { val today = LocalDate.now(); val start = today.minusDays((today.dayOfWeek.value - 1).toLong()); val values = (0..6).map { i -> store.completed(start.plusDays(i.toLong())).sumOf { id -> missions.find { it.id == id }?.xp ?: 0 } }; val max = (values.maxOrNull() ?: 1).coerceAtLeast(1); LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) { item { SectionTitle("Estatísticas", "Evolução da campanha") }; item { Row { MiniMetric("${values.sum()}", "XP SEMANAL", Cyan, Modifier.weight(1f)); Spacer(Modifier.width(9.dp)); MiniMetric("${values.count { it > 0 }}/7", "DIAS ATIVOS", Mint, Modifier.weight(1f)); Spacer(Modifier.width(9.dp)); MiniMetric("${store.totalXp / 100 + 1}", "NÍVEL", Amber, Modifier.weight(1f)) } }; item { GlassCard { Text("Ritmo dos últimos 7 dias", fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) { values.forEachIndexed { i, v -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(v.toString(), color = if (v > 0) Text else Muted, fontSize = 9.sp); Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().height((125f * v / max).coerceAtLeast(5f).dp).background(Brush.verticalGradient(listOf(Cyan, Blue)), RoundedCornerShape(9.dp))); Spacer(Modifier.height(7.dp)); Text(start.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("pt", "BR")), color = Muted, fontSize = 9.sp) } } } } }; item { Achievements(store) } } }

@Composable fun Achievements(store: Store) { val doneAll = (0..30).sumOf { store.completed(LocalDate.now().minusDays(it.toLong())).size }; val defs = listOf(Achievement("first", "🌟", "Primeiro passo", "Complete sua primeira missão", doneAll >= 1), Achievement("five", "🔥", "Ritmo consistente", "Atividade em cinco dias", store.activeDays() >= 5), Achievement("veteran", "👑", "Veterano", "Acumule 500 XP", store.totalXp >= 500), Achievement("routine", "⚔️", "Chefe vencido", "Complete 20 missões", doneAll >= 20)); GlassCard { SectionTitle("Coleção", "Conquistas", "${defs.count { it.unlocked }}/${defs.size}"); Spacer(Modifier.height(12.dp)); defs.forEach { a -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(43.dp).background(if (a.unlocked) Amber.copy(.13f) else Surface2, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(if (a.unlocked) a.icon else "🔒", fontSize = 20.sp) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(a.name, fontWeight = FontWeight.Bold, color = if (a.unlocked) Text else Muted); Text(a.description, color = Muted, fontSize = 10.sp) }; if (a.unlocked) Icon(Icons.Default.CheckCircle, null, tint = Mint, modifier = Modifier.size(20.dp)) } } } }

@Composable fun CalendarView(store: Store, rev: Int) { var month by remember { mutableStateOf(YearMonth.now()) }; val first = month.atDay(1); val blanks = first.dayOfWeek.value - 1; LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) { item { SectionTitle("Histórico", "Calendário de missões"); Spacer(Modifier.height(14.dp)); GlassCard { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }; Text(month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() } + " ${month.year}", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) } }; Row { listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { Text(it, Modifier.weight(1f), color = Muted, fontSize = 9.sp) } }; val cells = List(blanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }; cells.chunked(7).forEach { week -> Row { (week + List(7 - week.size) { null }).forEach { date -> val count = date?.let { store.completed(it).size } ?: 0; val isToday = date == LocalDate.now(); Surface(color = if (count > 0) Mint.copy(.13f) else Color.Transparent, shape = RoundedCornerShape(11.dp), border = if (isToday) androidx.compose.foundation.BorderStroke(1.dp, Cyan) else null, modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) { Box(contentAlignment = Alignment.Center) { if (date != null) Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(date.dayOfMonth.toString(), fontWeight = if (isToday) FontWeight.Black else FontWeight.Normal, fontSize = 12.sp); if (count > 0) Text("$count", fontSize = 8.sp, color = Mint, fontWeight = FontWeight.Bold) } } } } } } } } } }

@Composable fun Focus(store: Store, activity: MainActivity, rev: Int, onChange: () -> Unit) { var total by remember { mutableIntStateOf(1500) }; var left by remember { mutableIntStateOf(1500) }; var running by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var cost by remember { mutableStateOf("") }; var rewards by remember(rev) { mutableStateOf(store.rewards()) }; val progress by animateFloatAsState(if (total == 0) 0f else (total - left).toFloat() / total, label = "timer"); LaunchedEffect(running, left) { if (running && left > 0) { delay(1000); left-- } else if (running && left == 0) { running = false; activity.timerFinished() } }; LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) { item { SectionTitle("Modo concentração", "Foco e recuperação") }; item { GlassCard { Box(Modifier.size(210.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) { CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 9.dp, color = Cyan, trackColor = Border); Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(String.format("%02d:%02d", left / 60, left % 60), fontSize = 46.sp, fontWeight = FontWeight.Black); Text(if (running) "EM ANDAMENTO" else "PRONTO PARA COMEÇAR", color = if (running) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(10 to "Casa", 20 to "Treino", 25 to "Estudo").forEach { (m, label) -> FilterChip(selected = total == m * 60, onClick = { running = false; total = m * 60; left = total }, label = { Text("$label ${m}m", fontSize = 9.sp) }, modifier = Modifier.weight(1f)) } }; Spacer(Modifier.height(10.dp)); Button({ running = !running }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(if (running) Rose else Cyan, Ink)) { Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text(if (running) "Pausar sessão" else "Iniciar sessão", fontWeight = FontWeight.Bold) } } }; item { GlassCard { SectionTitle("Loja pessoal", "Recompensas", "${store.totalXp - store.spentXp} XP"); Spacer(Modifier.height(12.dp)); OutlinedTextField(name, { name = it }, label = { Text("Nova recompensa") }, leadingIcon = { Text("🎁") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)); Spacer(Modifier.height(8.dp)); Row { OutlinedTextField(cost, { cost = it.filter(Char::isDigit) }, label = { Text("Custo em XP") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)); Spacer(Modifier.width(8.dp)); Button({ val c = cost.toIntOrNull(); if (name.isNotBlank() && c != null && c > 0) { rewards.add(Reward(name, c)); store.saveRewards(rewards); name = ""; cost = ""; onChange() } }, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null) } }; rewards.forEachIndexed { i, r -> Surface(color = Surface2, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) { Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Text("🎁", fontSize = 20.sp); Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(r.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${r.cost} XP · resgatada ${r.claimed}x", color = Muted, fontSize = 9.sp) }; FilledTonalButton({ if (store.totalXp - store.spentXp >= r.cost) { store.spentXp += r.cost; r.claimed++; store.saveRewards(rewards); onChange() } }, enabled = store.totalXp - store.spentXp >= r.cost) { Text("Resgatar", fontSize = 9.sp) }; IconButton({ rewards.removeAt(i); store.saveRewards(rewards); onChange() }) { Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(18.dp)) } } } } } } } }
