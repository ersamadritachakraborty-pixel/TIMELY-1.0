package com.example.timely

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- MNC STYLE AESTHETIC (Obsidian / Neon Cyan) ---
val ObsidianBlack = Color(0xFF07090E)
val DarkGlassSurface = Color(0xFF12161F)
val NeonCyan = Color(0xFF00F2FE)
val VividMagenta = Color(0xFF4FACFE)
val CriticalRed = Color(0xFFFF4B4B)
val TextPrimary = Color(0xFFF0F3F8)
val TextMuted = Color(0xFF7A8194)

enum class UrgencyLevel { CRITICAL, HIGH, NORMAL }

// --- DATABASE (ROOM PERSISTENCE) ---
@Entity(tableName = "reminders")
data class TimelyReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetTimeString: String,
    val message: String,
    val urgency: UrgencyLevel
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY id DESC")
    fun getAllReminders(): Flow<List<TimelyReminder>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: TimelyReminder)
    @Delete
    suspend fun delete(reminder: TimelyReminder)
}

@Database(entities = [TimelyReminder::class], version = 2)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
}

class Converters {
    @TypeConverter
    fun fromUrgencyLevel(value: UrgencyLevel) = value.name

    @TypeConverter
    fun toUrgencyLevel(value: String) = try {
        UrgencyLevel.valueOf(value)
    } catch (_: Exception) {
        UrgencyLevel.NORMAL
    }
}


class MainActivity : ComponentActivity() {
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "timely-db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = ObsidianBlack, surface = DarkGlassSurface)) {
                TimelyApp(db.reminderDao())
            }
        }
    }
}

@Composable
fun TimelyApp(dao: ReminderDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reminders by dao.getAllReminders().collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = ObsidianBlack) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).statusBarsPadding()) {
                Spacer(modifier = Modifier.height(20.dp))
                // HEADER
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "TIMELY", color = NeonCyan, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text(text = "Getting reminders right before it's too late", color = TextMuted, fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(DarkGlassSurface).border(1.dp, TextMuted.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = NeonCyan)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text(text = "ACTIVE TIMELINES (${reminders.size})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderCard(reminder = reminder, onComplete = {
                            scope.launch { dao.delete(reminder) }
                        })
                    }
                }
            }

            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = Color.Transparent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).navigationBarsPadding().clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(colors = listOf(NeonCyan, VividMagenta)))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", tint = ObsidianBlack)
            }
        }

        if (showDialog) {
            AddReminderModal(
                onDismiss = { showDialog = false },
                onAdd = { title, msg, time, urgency, calendar ->
                    val newReminder = TimelyReminder(title = title, targetTimeString = time, message = msg, urgency = urgency)
                    scope.launch { dao.insert(newReminder) }
                    scheduleAlarm(context, calendar, newReminder)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun ReminderCard(reminder: TimelyReminder, onComplete: () -> Unit) {
    val urgencyColor = when(reminder.urgency) {
        UrgencyLevel.CRITICAL -> CriticalRed
        UrgencyLevel.HIGH -> Color(0xFFFF9F43)
        UrgencyLevel.NORMAL -> NeonCyan
    }

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DarkGlassSurface).border(1.dp, TextMuted.copy(alpha = 0.15f), RoundedCornerShape(18.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(urgencyColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = reminder.urgency.name, color = urgencyColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = reminder.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(text = reminder.message, color = TextMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Target: ${reminder.targetTimeString}", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onComplete, modifier = Modifier.clip(CircleShape).background(ObsidianBlack).border(1.dp, TextMuted.copy(alpha = 0.2f), CircleShape)) {
                Icon(Icons.Default.Check, contentDescription = null, tint = TextPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderModal(onDismiss: () -> Unit, onAdd: (String, String, String, UrgencyLevel, Calendar) -> Unit) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf("Select Date & Time") }
    var urgency by remember { mutableStateOf(UrgencyLevel.HIGH) }
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val dateCal = Calendar.getInstance().apply { timeInMillis = it }
                        calendar.set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                        calendar.set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                        calendar.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next", color = NeonCyan) }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkGlassSurface)
        ) {
            DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(
                containerColor = DarkGlassSurface,
                titleContentColor = NeonCyan,
                headlineContentColor = TextPrimary,
                selectedDayContainerColor = NeonCyan,
                selectedDayContentColor = ObsidianBlack,
                todayContentColor = NeonCyan,
                todayDateBorderColor = NeonCyan
            ))
        }
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = DarkGlassSurface) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState, colors = TimePickerDefaults.colors(
                        clockDialColor = ObsidianBlack,
                        selectorColor = NeonCyan,
                        containerColor = DarkGlassSurface,
                        periodSelectorSelectedContainerColor = NeonCyan,
                        periodSelectorSelectedContentColor = ObsidianBlack,
                        timeSelectorSelectedContainerColor = NeonCyan,
                        timeSelectorSelectedContentColor = ObsidianBlack
                    ))
                    Button(onClick = {
                        calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        calendar.set(Calendar.MINUTE, timePickerState.minute)
                        calendar.set(Calendar.SECOND, 0)
                        selectedTime = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(calendar.time)
                        showTimePicker = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) { Text("Confirm", color = ObsidianBlack) }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkGlassSurface,
        title = { Text("New Timely Task", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Reminder Message") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                Button(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = DarkGlassSurface), border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f))) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedTime, color = TextPrimary)
                }
                // Urgency Selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UrgencyLevel.entries.forEach { level ->
                        val isSelected = urgency == level
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (isSelected) NeonCyan else TextMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { urgency = level }.padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(level.name, color = if (isSelected) NeonCyan else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (title.isNotBlank()) onAdd(title, message, selectedTime, urgency, calendar) }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) {
                Text("Save Task", color = ObsidianBlack, fontWeight = FontWeight.Bold)
            }
        }
    )
}

fun scheduleAlarm(context: Context, calendar: Calendar, reminder: TimelyReminder) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("REMINDER_TITLE", reminder.title)
        putExtra("REMINDER_MESSAGE", reminder.message)
        putExtra("REMINDER_URGENCY", reminder.urgency.name)
    }
    val pendingIntent = PendingIntent.getBroadcast(context, reminder.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    } else {
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddReminderModalPreview() {
    MaterialTheme(colorScheme = darkColorScheme(background = ObsidianBlack, surface = DarkGlassSurface)) {
        Box(modifier = Modifier.fillMaxSize().background(ObsidianBlack)) {
            AddReminderModal(
                onDismiss = {},
                onAdd = { _, _, _, _, _ -> }
            )
        }
    }
}
