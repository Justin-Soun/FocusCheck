package com.justin.focuscheck

import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import com.justin.focuscheck.notifications.ReminderScheduler
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justin.focuscheck.data.TaskEntity
import com.justin.focuscheck.data.TaskStatus
import com.justin.focuscheck.ui.theme.FocusCheckTheme
import com.justin.focuscheck.viewmodel.TaskViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.justin.focuscheck.settings.ReminderSettings
import com.justin.focuscheck.settings.SettingsViewModel
import android.app.TimePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.justin.focuscheck.notifications.NotificationHelper
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.lazy.items
import com.justin.focuscheck.data.CheckInEntity
import com.justin.focuscheck.viewmodel.CheckInViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.justin.focuscheck.notifications.ReminderStateRepository
import com.justin.focuscheck.notifications.ReminderRuntimeState
import com.justin.focuscheck.notifications.ReminderCoordinator
import kotlinx.coroutines.launch
import android.app.DatePickerDialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import androidx.compose.ui.graphics.Color
class MainActivity : ComponentActivity() {

    private val taskViewModel:
            TaskViewModel by viewModels()

    private val settingsViewModel:
            SettingsViewModel by viewModels()

    private val checkInViewModel:
            CheckInViewModel by viewModels()

    private val openCheckInRequest =
        mutableStateOf(false)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        NotificationHelper
            .createNotificationChannel(this)

        openCheckInRequest.value =
            intent.getBooleanExtra(
                NotificationHelper.EXTRA_OPEN_CHECK_IN,
                false
            )

        enableEdgeToEdge()

        setContent {
            FocusCheckTheme {
                FocusCheckApp(
                    taskViewModel = taskViewModel,
                    settingsViewModel =
                        settingsViewModel,
                    checkInViewModel =
                        checkInViewModel,
                    openCheckInRequested =
                        openCheckInRequest.value,
                    onOpenCheckInHandled = {
                        openCheckInRequest.value = false
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        if (
            intent.getBooleanExtra(
                NotificationHelper.EXTRA_OPEN_CHECK_IN,
                false
            )
        ) {
            openCheckInRequest.value = true
        }
    }
}


private enum class AppScreen(
    val label: String,
    val symbol: String
) {
    TODAY("Today", "✓"),
    TASKS("Tasks", "☷"),
    CALENDAR("Calendar", "▦"),
    HISTORY("History", "↺"),
    SETTINGS("Settings", "⚙")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusCheckApp(
    taskViewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel,
    checkInViewModel: CheckInViewModel,
    openCheckInRequested: Boolean,
    onOpenCheckInHandled: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope =
        rememberCoroutineScope()

    val reminderStateRepository =
        remember(context) {
            ReminderStateRepository(
                context.applicationContext
            )
        }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { permissionGranted ->
            if (permissionGranted) {
                NotificationHelper.showTestNotification(
                    context
                )
            } else {
                Toast.makeText(
                    context,
                    "Notification permission was not granted.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    val sendTestNotification: () -> Unit = {
        val permissionAlreadyGranted =
            Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        if (permissionAlreadyGranted) {
            NotificationHelper.showTestNotification(
                context
            )
        } else {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }
    val scheduleTestReminder: () -> Unit = {
        if (
            NotificationHelper
                .canPostCheckInNotifications(context)
        ) {
            ReminderScheduler
                .scheduleTestReminder(
                    context = context,
                    delayMillis = 60_000L
                )

            Toast.makeText(
                context,
                "Alarm test scheduled for approximately one minute from now.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                context,
                "Grant notification permission before scheduling the test.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val tasks by taskViewModel.tasks.collectAsStateWithLifecycle()

    val reminderSettings by settingsViewModel
        .settings
        .collectAsStateWithLifecycle()

    val reminderRuntimeState by reminderStateRepository
        .state
        .collectAsStateWithLifecycle(
            initialValue = ReminderRuntimeState()
        )

    val startRepeatedReminderTest: () -> Unit = {
        val hasUnfinishedTask =
            tasks.any {
                it.status != TaskStatus.COMPLETED
            }

        when {
            !NotificationHelper
                .canPostCheckInNotifications(context) -> {

                Toast.makeText(
                    context,
                    "Enable Focus Check notifications first.",
                    Toast.LENGTH_LONG
                ).show()
            }

            !reminderSettings.remindersEnabled -> {
                Toast.makeText(
                    context,
                    "Enable check-ins before running this test.",
                    Toast.LENGTH_LONG
                ).show()
            }

            !ReminderScheduler
                .isInsideActiveWindow(
                    reminderSettings
                ) -> {

                Toast.makeText(
                    context,
                    "Make today active and place the current time inside the configured active hours.",
                    Toast.LENGTH_LONG
                ).show()
            }

            !hasUnfinishedTask -> {
                Toast.makeText(
                    context,
                    "Add at least one unfinished task first.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {
                coroutineScope.launch {
                    val configuredRetryDelayMillis =
                        reminderSettings
                            .missedReminderMinutes
                            .toLong() *
                                60_000L

                    /*
                     * Save the user's configured interval.
                     * Only the first test retry uses one minute.
                     */
                    reminderStateRepository
                        .markPending(
                            retryDelayMillis =
                                configuredRetryDelayMillis
                        )

                    ReminderScheduler
                        .cancelRegularReminder(context)

                    val notificationPosted =
                        NotificationHelper
                            .showScheduledCheckInNotification(
                                context = context,
                                isTest = false,
                                isRepeat = false
                            )

                    if (!notificationPosted) {
                        reminderStateRepository.clearPending()

                        ReminderCoordinator.reconcile(
                            context = context,
                            reason = "the repeated-reminder test notification could not be posted"
                        )

                        Toast.makeText(
                            context,
                            "The test notification could not be displayed.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@launch
                    }

                    /*
                     * The first test retry may use one minute.
                     * All subsequent retries use the user's
                     * configured missed-reminder interval.
                     */
                    ReminderScheduler
                        .scheduleRetryReminder(
                            context = context,
                            delayMillis = 60_000L
                        )

                    Toast.makeText(
                        context,
                        "Repeated-reminder test started. Ignore or dismiss the notification and wait approximately one minute.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    val checkIns by checkInViewModel
        .checkIns
        .collectAsStateWithLifecycle()

    var currentScreen by rememberSaveable {
        mutableStateOf(AppScreen.TODAY)
    }

    var showAddTaskDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var newTaskInitialDueDateEpochDay by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var selectedCalendarDateEpochDay by rememberSaveable {
        mutableStateOf(
            LocalDate.now().toEpochDay()
        )
    }

    var displayedCalendarMonthEpochDay by rememberSaveable {
        mutableStateOf(
            YearMonth.now()
                .atDay(1)
                .toEpochDay()
        )
    }

    var editingTaskId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var taskPendingDeletionId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    val editingTask = tasks.firstOrNull {
        it.id == editingTaskId
    }

    val taskPendingDeletion = tasks.firstOrNull {
        it.id == taskPendingDeletionId
    }


    var showCheckInDialog by rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(
        reminderSettings
    ) {
        ReminderCoordinator.reconcile(
            context = context,
            reason = "the app opened or reminder settings changed"
        )
    }

    LaunchedEffect(
        openCheckInRequested
    ) {
        if (openCheckInRequested) {
            currentScreen = AppScreen.TODAY
            showCheckInDialog = true
            onOpenCheckInHandled()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            AppScreen.TODAY -> "Focus Check"
                            else -> currentScreen.label
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = {
                            currentScreen = screen
                        },
                        icon = {
                            Text(
                                text = screen.symbol,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        label = {
                            Text(text = screen.label)
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (
                currentScreen == AppScreen.TASKS ||
                currentScreen == AppScreen.CALENDAR
            ) {
                FloatingActionButton(
                    onClick = {
                        newTaskInitialDueDateEpochDay =
                            if (
                                currentScreen ==
                                AppScreen.CALENDAR
                            ) {
                                selectedCalendarDateEpochDay
                            } else {
                                null
                            }

                        showAddTaskDialog = true
                    },
                    modifier = Modifier.semantics {
                        contentDescription =
                            if (
                                currentScreen ==
                                AppScreen.CALENDAR
                            ) {
                                "Add task for selected date"
                            } else {
                                "Add task"
                            }
                    }
                ) {
                    Text(
                        text = "+",
                        style =
                            MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.TODAY -> TodayScreen(
                    tasks = tasks,
                    reminderSettings = reminderSettings,
                    reminderRuntimeState = reminderRuntimeState,
                    onCheckIn = {
                        showCheckInDialog = true
                    },
                    onStartTask = taskViewModel::markInProgress,
                    onPauseTask = taskViewModel::pauseTask,
                    onSwitchTask = taskViewModel::switchTask,
                    onCompleteTask = taskViewModel::markCompleted,
                    onReopenTask = taskViewModel::reopenTask
                )

                AppScreen.TASKS -> TasksScreen(
                    tasks = tasks,

                    onAddTask = {
                        newTaskInitialDueDateEpochDay = null
                        showAddTaskDialog = true
                    },

                    onEditTask = { task ->
                        editingTaskId = task.id
                    },

                    onStartTask =
                        taskViewModel::markInProgress,

                    onPauseTask =
                        taskViewModel::pauseTask,

                    onSwitchTask =
                        taskViewModel::switchTask,

                    onCompleteTask =
                        taskViewModel::markCompleted,

                    onReopenTask =
                        taskViewModel::reopenTask,

                    onDeleteTask = { task ->
                        taskPendingDeletionId = task.id
                    }
                )

                AppScreen.CALENDAR -> CalendarScreen(
                    tasks = tasks,
                    displayedMonthEpochDay =
                        displayedCalendarMonthEpochDay,
                    selectedDateEpochDay =
                        selectedCalendarDateEpochDay,

                    onDisplayedMonthChanged = {
                            monthEpochDay ->

                        displayedCalendarMonthEpochDay =
                            monthEpochDay
                    },

                    onDateSelected = {
                            selectedEpochDay ->

                        selectedCalendarDateEpochDay =
                            selectedEpochDay
                    },

                    onAddTaskForDate = {
                            dueDateEpochDay ->

                        newTaskInitialDueDateEpochDay =
                            dueDateEpochDay

                        showAddTaskDialog = true
                    },

                    onEditTask = { task ->
                        editingTaskId = task.id
                    }
                )

                AppScreen.HISTORY -> HistoryScreen(
                    checkIns = checkIns
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    settings = reminderSettings,
                    onEnabledChanged =
                        settingsViewModel::setRemindersEnabled,
                    onStartTimeChanged =
                        settingsViewModel::setStartTime,
                    onEndTimeChanged =
                        settingsViewModel::setEndTime,
                    onCheckInIntervalChanged =
                        settingsViewModel::setCheckInInterval,
                    onMissedReminderChanged =
                        settingsViewModel::setMissedReminderInterval,
                    onDayChanged =
                        settingsViewModel::setActiveDay,
                    onTestNotification =
                        sendTestNotification,
                    onScheduleTestReminder =
                        scheduleTestReminder,
                    onStartRepeatedReminderTest =
                        startRepeatedReminderTest
                )
            }
        }
    }

    if (showAddTaskDialog) {
        TaskEditorDialog(
            dialogTitle = "Add task",
            initialDueDateEpochDay =
                newTaskInitialDueDateEpochDay,

            onDismiss = {
                showAddTaskDialog = false
                newTaskInitialDueDateEpochDay = null
            },

            onSave = {
                    title,
                    notes,
                    importance,
                    urgency,
                    dueDateEpochDay ->

                taskViewModel.addTask(
                    title = title,
                    notes = notes,
                    importance = importance,
                    urgency = urgency,
                    dueDateEpochDay =
                        dueDateEpochDay
                )

                showAddTaskDialog = false
                newTaskInitialDueDateEpochDay = null
            }
        )
    }

    if (editingTask != null) {
        TaskEditorDialog(
            dialogTitle = "Edit task",
            initialTask = editingTask,

            onDismiss = {
                editingTaskId = null
            },

            onSave = {
                    title,
                    notes,
                    importance,
                    urgency,
                    dueDateEpochDay ->

                taskViewModel.updateTask(
                    task = editingTask,
                    title = title,
                    notes = notes,
                    importance = importance,
                    urgency = urgency,
                    dueDateEpochDay =
                        dueDateEpochDay
                )

                editingTaskId = null
            }
        )
    }
    if (taskPendingDeletion != null) {
        DeleteTaskDialog(
            task = taskPendingDeletion,

            onDismiss = {
                taskPendingDeletionId = null
            },

            onConfirm = {
                taskViewModel.deleteTask(
                    taskPendingDeletion
                )

                taskPendingDeletionId = null
            }
        )
    }


    if (showCheckInDialog) {
        CheckInDialog(
            tasks = tasks,

            onDismiss = {
                /*
                 * Cancel and Android Back only close the dialog.
                 * They do not acknowledge the pending check-in.
                 */
                showCheckInDialog = false
            },

            onNoTasksClose = {
                showCheckInDialog = false

                coroutineScope.launch {
                    ReminderCoordinator.stopEverything(
                        context = context,
                        reason = "the check-in dialog found no unfinished tasks"
                    )
                }
            },

            onSubmit = {
                    task,
                    reportedStatus,
                    note,
                    nextTask ->

                val anotherUnfinishedTaskExists =
                    tasks.any { candidate ->
                        candidate.id != task.id &&
                                candidate.status !=
                                TaskStatus.COMPLETED
                    }

                val submittedTaskRemainsUnfinished =
                    reportedStatus !=
                            TaskStatus.COMPLETED

                val willHaveUnfinishedTasks =
                    anotherUnfinishedTaskExists ||
                            submittedTaskRemainsUnfinished ||
                            nextTask != null

                checkInViewModel.submitCheckIn(
                    task = task,
                    reportedStatus = reportedStatus,
                    note = note,
                    nextTask = nextTask
                )

                coroutineScope.launch {
                    ReminderCoordinator.acknowledgeCheckIn(
                        context = context,
                        hasUnfinishedTasks =
                            willHaveUnfinishedTasks,
                        reason = "a progress check-in was submitted"
                    )
                }

                showCheckInDialog = false
            }
        )
    }
}

@Composable
private fun TodayScreen(
    tasks: List<TaskEntity>,
    reminderSettings: ReminderSettings,
    reminderRuntimeState: ReminderRuntimeState,
    onCheckIn: () -> Unit,
    onStartTask: (TaskEntity) -> Unit,
    onPauseTask: (TaskEntity, String) -> Unit,
    onSwitchTask: (
        TaskEntity,
        TaskEntity,
        String
    ) -> Unit,
    onCompleteTask: (TaskEntity) -> Unit,
    onReopenTask: (TaskEntity) -> Unit
) {
    val today = LocalDate.now()

    val activeTasks = sortedActiveTasks(
        tasks = tasks,
        today = today
    )

    val reminderDisplay = reminderDisplayState(
        settings = reminderSettings,
        runtimeState = reminderRuntimeState,
        unfinishedTaskCount = activeTasks.size
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Today's focus",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tasks are ranked by due date, current status, importance, and urgency.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (activeTasks.isEmpty()) {
            item {
                EmptyTaskCard(
                    title = "No active tasks",
                    description = "Open the Tasks tab or Calendar and add your next task."
                )
            }
        } else {
            itemsIndexed(
                items = activeTasks,
                key = { _, task -> task.id }
            ) { index, task ->
                TaskCard(
                    ranking = index + 1,
                    task = task,
                    allTasks = tasks,
                    onStartTask = onStartTask,
                    onPauseTask = onPauseTask,
                    onSwitchTask = onSwitchTask,
                    onCompleteTask = onCompleteTask,
                    onReopenTask = onReopenTask
                )
            }
        }

        item {
            ReminderStatusCard(
                display = reminderDisplay
            )
        }

        item {
            Button(
                onClick = onCheckIn,
                enabled = activeTasks.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                contentPadding = PaddingValues(
                    vertical = 16.dp
                )
            ) {
                Text(
                    text = "CHECK IN NOW",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ReminderStatusCard(
    display: ReminderDisplayState
) {
    val containerColor =
        if (display.isPending) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }

    val contentColor =
        if (display.isPending) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${display.label}. ${display.title}. ${display.description}"
            },
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = display.label.uppercase(
                    Locale.getDefault()
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = display.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = display.description,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun TasksScreen(
    tasks: List<TaskEntity>,
    onAddTask: () -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onStartTask: (TaskEntity) -> Unit,
    onPauseTask: (TaskEntity, String) -> Unit,
    onSwitchTask: (
        TaskEntity,
        TaskEntity,
        String
    ) -> Unit,
    onCompleteTask: (TaskEntity) -> Unit,
    onReopenTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit
) {
    val today = LocalDate.now()

    val activeTasks = sortedActiveTasks(
        tasks = tasks,
        today = today
    )

    val completedTasks = sortedCompletedTasks(
        tasks = tasks
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Your tasks",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${activeTasks.size} active · ${completedTasks.size} completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onAddTask,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = "Add task")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        if (tasks.isEmpty()) {
            item {
                EmptyTaskCard(
                    title = "Your list is empty",
                    description = "Select Add task to create your first task."
                )
            }
        } else {
            item {
                TaskSectionHeader(
                    title = "Active tasks",
                    count = activeTasks.size,
                    description = "Current, overdue, and due-soon work appears first."
                )
            }

            if (activeTasks.isEmpty()) {
                item {
                    EmptyTaskCard(
                        title = "No active tasks",
                        description = "Reopen a completed task or add something new."
                    )
                }
            } else {
                itemsIndexed(
                    items = activeTasks,
                    key = { _, task -> "active-${task.id}" }
                ) { index, task ->
                    TaskCard(
                        ranking = index + 1,
                        task = task,
                        allTasks = tasks,
                        onStartTask = onStartTask,
                        onPauseTask = onPauseTask,
                        onSwitchTask = onSwitchTask,
                        onCompleteTask = onCompleteTask,
                        onReopenTask = onReopenTask,
                        onEditTask = onEditTask,
                        onDeleteTask = onDeleteTask
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                TaskSectionHeader(
                    title = "Completed tasks",
                    count = completedTasks.size,
                    description = "Completed work stays available for review or reopening."
                )
            }

            if (completedTasks.isEmpty()) {
                item {
                    EmptyTaskCard(
                        title = "Nothing completed yet",
                        description = "Completed tasks will move into this section."
                    )
                }
            } else {
                itemsIndexed(
                    items = completedTasks,
                    key = { _, task -> "completed-${task.id}" }
                ) { index, task ->
                    TaskCard(
                        ranking = index + 1,
                        task = task,
                        allTasks = tasks,
                        onStartTask = onStartTask,
                        onPauseTask = onPauseTask,
                        onSwitchTask = onSwitchTask,
                        onCompleteTask = onCompleteTask,
                        onReopenTask = onReopenTask,
                        onEditTask = onEditTask,
                        onDeleteTask = onDeleteTask
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskSectionHeader(
    title: String,
    count: Int,
    description: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor =
                    MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 5.dp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun taskStatusContainerColor(
    status: String
): Color {
    return when (status) {
        TaskStatus.IN_PROGRESS ->
            MaterialTheme.colorScheme.primaryContainer

        TaskStatus.PAUSED ->
            MaterialTheme.colorScheme.tertiaryContainer

        TaskStatus.BLOCKED ->
            MaterialTheme.colorScheme.errorContainer

        TaskStatus.COMPLETED ->
            MaterialTheme.colorScheme.secondaryContainer

        else ->
            MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun taskStatusContentColor(
    status: String
): Color {
    return when (status) {
        TaskStatus.IN_PROGRESS ->
            MaterialTheme.colorScheme.onPrimaryContainer

        TaskStatus.PAUSED ->
            MaterialTheme.colorScheme.onTertiaryContainer

        TaskStatus.BLOCKED ->
            MaterialTheme.colorScheme.onErrorContainer

        TaskStatus.COMPLETED ->
            MaterialTheme.colorScheme.onSecondaryContainer

        else ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun taskCardContainerColor(
    task: TaskEntity
): Color {
    val overdue =
        task.status != TaskStatus.COMPLETED &&
                task.dueDateEpochDay?.let {
                    it < LocalDate.now().toEpochDay()
                } == true

    return when {
        task.status == TaskStatus.IN_PROGRESS ->
            MaterialTheme.colorScheme
                .primaryContainer
                .copy(alpha = 0.62f)

        task.status == TaskStatus.BLOCKED ->
            MaterialTheme.colorScheme
                .errorContainer
                .copy(alpha = 0.62f)

        overdue ->
            MaterialTheme.colorScheme
                .errorContainer
                .copy(alpha = 0.34f)

        task.status == TaskStatus.PAUSED ->
            MaterialTheme.colorScheme
                .tertiaryContainer
                .copy(alpha = 0.42f)

        task.status == TaskStatus.COMPLETED ->
            MaterialTheme.colorScheme
                .secondaryContainer
                .copy(alpha = 0.38f)

        else ->
            MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun taskCardBorderColor(
    task: TaskEntity
): Color? {
    val overdue =
        task.status != TaskStatus.COMPLETED &&
                task.dueDateEpochDay?.let {
                    it < LocalDate.now().toEpochDay()
                } == true

    return when {
        task.status == TaskStatus.IN_PROGRESS ->
            MaterialTheme.colorScheme.primary

        task.status == TaskStatus.BLOCKED ||
                overdue ->
            MaterialTheme.colorScheme.error

        task.status == TaskStatus.PAUSED ->
            MaterialTheme.colorScheme.tertiary

        else -> null
    }
}

@Composable
private fun TaskStatusBadge(
    status: String
) {
    val badgeText =
        if (status == TaskStatus.IN_PROGRESS) {
            "CURRENT TASK"
        } else {
            statusLabel(status).uppercase(
                Locale.getDefault()
            )
        }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = taskStatusContainerColor(status),
        contentColor = taskStatusContentColor(status)
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 5.dp
            )
        )
    }
}

private fun taskAccessibilityDescription(
    task: TaskEntity,
    ranking: Int
): String {
    return buildString {
        append("Task $ranking. ")
        append(task.title)
        append(". ")
        append(statusLabel(task.status))
        append(". Importance ")
        append(task.importance)
        append(" out of 5. Urgency ")
        append(task.urgency)
        append(" out of 5. Priority score ")
        append(task.priorityScore())
        append(".")

        task.dueDateEpochDay?.let {
            append(" ")
            append(dueDateLabel(it))
            append(".")
        }

        if (task.notes.isNotBlank()) {
            append(" Notes: ")
            append(task.notes)
        }
    }
}

@Composable
private fun TaskCard(
    ranking: Int,
    task: TaskEntity,
    allTasks: List<TaskEntity>,
    onStartTask: (TaskEntity) -> Unit,
    onPauseTask: (TaskEntity, String) -> Unit,
    onSwitchTask: (
        TaskEntity,
        TaskEntity,
        String
    ) -> Unit,
    onCompleteTask: (TaskEntity) -> Unit,
    onReopenTask: (TaskEntity) -> Unit,
    onEditTask: ((TaskEntity) -> Unit)? = null,
    onDeleteTask: ((TaskEntity) -> Unit)? = null
) {
    var isConfirmingCompletion by rememberSaveable(
        task.id,
        task.status
    ) {
        mutableStateOf(false)
    }

    var showPauseDialog by rememberSaveable(task.id) {
        mutableStateOf(false)
    }

    val isCompleted =
        task.status == TaskStatus.COMPLETED

    val shape = RoundedCornerShape(18.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    taskAccessibilityDescription(
                        task = task,
                        ranking = ranking
                    )
            },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = taskCardContainerColor(task)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation =
                if (
                    task.status ==
                    TaskStatus.IN_PROGRESS
                ) {
                    3.dp
                } else {
                    1.dp
                }
        ),
        border = taskCardBorderColor(task)?.let {
            BorderStroke(
                width = 1.dp,
                color = it
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = taskStatusContainerColor(
                        task.status
                    ),
                    contentColor = taskStatusContentColor(
                        task.status
                    )
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text =
                                if (isCompleted) {
                                    "✓"
                                } else {
                                    ranking.toString()
                                },
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    TaskStatusBadge(
                        status = task.status
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = task.title,
                        style =
                            MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration =
                            if (isCompleted) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (task.notes.isNotBlank()) {
                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = task.notes,
                            style =
                                MaterialTheme.typography
                                    .bodyMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = buildString {
                            append(
                                "Importance ${task.importance}"
                            )
                            append(" · ")
                            append(
                                "Urgency ${task.urgency}"
                            )
                            append(" · ")
                            append(
                                "Score ${task.priorityScore()}"
                            )
                        },
                        style =
                            MaterialTheme.typography.bodyMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    task.dueDateEpochDay?.let {
                            dueEpochDay ->

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = dueDateLabel(
                                dueEpochDay
                            ),
                            style =
                                MaterialTheme.typography
                                    .bodyMedium,
                            fontWeight =
                                FontWeight.SemiBold,
                            color =
                                if (isCompleted) {
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                                } else {
                                    dueDateColor(
                                        dueEpochDay
                                    )
                                }
                        )
                    }

                    if (
                        (
                                task.status ==
                                        TaskStatus.PAUSED ||
                                        task.status ==
                                        TaskStatus.BLOCKED
                                ) &&
                        !task.pauseReason.isNullOrBlank()
                    ) {
                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )

                        Surface(
                            shape =
                                RoundedCornerShape(12.dp),
                            color =
                                taskStatusContainerColor(
                                    task.status
                                ),
                            contentColor =
                                taskStatusContentColor(
                                    task.status
                                )
                        ) {
                            Text(
                                text =
                                    "Reason: ${task.pauseReason}",
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                                fontWeight =
                                    FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp
                                ),
                                maxLines = 3,
                                overflow =
                                    TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (
                onEditTask != null &&
                task.status != TaskStatus.IN_PROGRESS
            ) {
                OutlinedButton(
                    onClick = {
                        onEditTask(task)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .semantics {
                            contentDescription =
                                "Edit ${task.title}"
                        }
                ) {
                    Text(
                        text = "EDIT TASK",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            when (task.status) {
                TaskStatus.NOT_STARTED -> {
                    Button(
                        onClick = {
                            onStartTask(task)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text(
                            text = "START TASK",
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TaskStatus.IN_PROGRESS -> {
                    Button(
                        onClick = {
                            if (isConfirmingCompletion) {
                                onCompleteTask(task)
                            } else {
                                isConfirmingCompletion = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                    ) {
                        Text(
                            text =
                                if (
                                    isConfirmingCompletion
                                ) {
                                    "TAP AGAIN TO COMPLETE"
                                } else {
                                    "IN PROGRESS"
                                },
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            isConfirmingCompletion = false
                            showPauseDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text(
                            text = "PAUSE TASK",
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TaskStatus.PAUSED,
                TaskStatus.BLOCKED -> {
                    Button(
                        onClick = {
                            onStartTask(task)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text(
                            text = "RESUME TASK",
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TaskStatus.COMPLETED -> {
                    OutlinedButton(
                        onClick = {
                            onReopenTask(task)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text(
                            text = "REOPEN TASK",
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (
                onDeleteTask != null &&
                task.status != TaskStatus.IN_PROGRESS
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        onDeleteTask(task)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription =
                                "Delete ${task.title}"
                        }
                ) {
                    Text(
                        text = "DELETE TASK",
                        color =
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showPauseDialog) {
        PauseTaskDialog(
            currentTask = task,
            allTasks = allTasks,
            onDismiss = {
                showPauseDialog = false
            },
            onPause = { reason ->
                onPauseTask(task, reason)
                showPauseDialog = false
            },
            onSwitchTask = {
                    nextTask,
                    reason ->

                onSwitchTask(
                    task,
                    nextTask,
                    reason
                )

                showPauseDialog = false
            }
        )
    }
}
private enum class PauseReasonOption(
    val label: String
) {
    BREAK("Break"),
    SWITCH_TASK("Work on another task"),
    WAITING("Waiting or blocked"),
    OTHER("Other")
}

@Composable
private fun PauseTaskDialog(
    currentTask: TaskEntity,
    allTasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onPause: (String) -> Unit,
    onSwitchTask: (TaskEntity, String) -> Unit
) {
    var selectedReasonName by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var details by rememberSaveable {
        mutableStateOf("")
    }

    var selectedTaskId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    val selectedReason = selectedReasonName?.let {
        PauseReasonOption.valueOf(it)
    }

    val availableTasks = allTasks.filter {
        it.id != currentTask.id &&
                it.status != TaskStatus.COMPLETED
    }

    val selectedTask = availableTasks.firstOrNull {
        it.id == selectedTaskId
    }

    val confirmEnabled = when (selectedReason) {
        PauseReasonOption.SWITCH_TASK -> selectedTask != null
        PauseReasonOption.OTHER -> details.isNotBlank()
        PauseReasonOption.BREAK,
        PauseReasonOption.WAITING -> true
        null -> false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Pause task")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Why are you pausing this task?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                PauseReasonOption.entries.forEach { option ->
                    if (selectedReason == option) {
                        Button(
                            onClick = {
                                selectedReasonName = option.name
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = option.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedReasonName = option.name

                                if (
                                    option !=
                                    PauseReasonOption.SWITCH_TASK
                                ) {
                                    selectedTaskId = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = option.label)
                        }
                    }
                }

                if (
                    selectedReason ==
                    PauseReasonOption.SWITCH_TASK
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "What are you working on instead?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (availableTasks.isEmpty()) {
                        Text(
                            text = "There are no other unfinished tasks.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        availableTasks.forEach { candidate ->
                            if (candidate.id == selectedTaskId) {
                                Button(
                                    onClick = {
                                        selectedTaskId = candidate.id
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = candidate.title)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedTaskId = candidate.id
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = candidate.title)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = details,
                    onValueChange = {
                        details = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = if (
                                selectedReason ==
                                PauseReasonOption.OTHER
                            ) {
                                "Reason"
                            } else {
                                "Optional note"
                            }
                        )
                    },
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = {
                    when (selectedReason) {
                        PauseReasonOption.BREAK -> {
                            onPause(
                                combinePauseReason(
                                    baseReason = "Break",
                                    details = details
                                )
                            )
                        }

                        PauseReasonOption.SWITCH_TASK -> {
                            val nextTask =
                                selectedTask ?: return@TextButton

                            onSwitchTask(
                                nextTask,
                                combinePauseReason(
                                    baseReason =
                                        "Working on ${nextTask.title}",
                                    details = details
                                )
                            )
                        }

                        PauseReasonOption.WAITING -> {
                            onPause(
                                combinePauseReason(
                                    baseReason =
                                        "Waiting or blocked",
                                    details = details
                                )
                            )
                        }

                        PauseReasonOption.OTHER -> {
                            onPause(details.trim())
                        }

                        null -> Unit
                    }
                }
            ) {
                Text(
                    text = if (
                        selectedReason ==
                        PauseReasonOption.SWITCH_TASK
                    ) {
                        "Switch task"
                    } else {
                        "Pause"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(text = "Cancel")
            }
        }
    )
}

private fun combinePauseReason(
    baseReason: String,
    details: String
): String {
    val trimmedDetails = details.trim()

    return if (trimmedDetails.isBlank()) {
        baseReason
    } else {
        "$baseReason: $trimmedDetails"
    }
}

@Composable
private fun EmptyTaskCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "$title. $description"
            },
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TaskEditorDialog(
    dialogTitle: String,
    initialTask: TaskEntity? = null,
    initialDueDateEpochDay: Long? = null,
    onDismiss: () -> Unit,
    onSave: (
        String,
        String,
        Int,
        Int,
        Long?
    ) -> Unit
) {
    val context = LocalContext.current

    var title by rememberSaveable(initialTask?.id) {
        mutableStateOf(
            initialTask?.title.orEmpty()
        )
    }

    var notes by rememberSaveable(initialTask?.id) {
        mutableStateOf(
            initialTask?.notes.orEmpty()
        )
    }

    var importance by rememberSaveable(
        initialTask?.id
    ) {
        mutableIntStateOf(
            initialTask?.importance ?: 3
        )
    }

    var urgency by rememberSaveable(
        initialTask?.id
    ) {
        mutableIntStateOf(
            initialTask?.urgency ?: 3
        )
    }

    var dueDateEpochDay by rememberSaveable(
        initialTask?.id,
        initialDueDateEpochDay
    ) {
        mutableStateOf(
            initialTask?.dueDateEpochDay
                ?: initialDueDateEpochDay
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(text = dialogTitle)
        },

        text = {
            Column(
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                ),
                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,

                    onValueChange = {
                        title = it
                    },

                    modifier = Modifier.fillMaxWidth(),

                    label = {
                        Text(text = "Task title")
                    },

                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,

                    onValueChange = {
                        notes = it
                    },

                    modifier = Modifier.fillMaxWidth(),

                    label = {
                        Text(text = "Notes")
                    },

                    minLines = 2,
                    maxLines = 4
                )

                RatingSelector(
                    label = "Importance",
                    selectedRating = importance,

                    onRatingSelected = {
                        importance = it
                    }
                )

                RatingSelector(
                    label = "Urgency",
                    selectedRating = urgency,

                    onRatingSelected = {
                        urgency = it
                    }
                )

                Text(
                    text = "Due date",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedButton(
                    onClick = {
                        val startingDate =
                            dueDateEpochDay?.let {
                                LocalDate.ofEpochDay(it)
                            } ?: LocalDate.now()

                        DatePickerDialog(
                            context,

                            {
                                    _,
                                    selectedYear,
                                    selectedMonth,
                                    selectedDay ->

                                dueDateEpochDay =
                                    LocalDate.of(
                                        selectedYear,
                                        selectedMonth + 1,
                                        selectedDay
                                    ).toEpochDay()
                            },

                            startingDate.year,
                            startingDate.monthValue - 1,
                            startingDate.dayOfMonth
                        ).show()
                    },

                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            dueDateEpochDay?.let {
                                formatTaskDate(it)
                            } ?: "CHOOSE DUE DATE"
                    )
                }

                if (dueDateEpochDay != null) {
                    TextButton(
                        onClick = {
                            dueDateEpochDay = null
                        },

                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Clear due date")
                    }
                }
            }
        },

        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),

                onClick = {
                    onSave(
                        title,
                        notes,
                        importance,
                        urgency,
                        dueDateEpochDay
                    )
                }
            ) {
                Text(text = "Save")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun DeleteTaskDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(text = "Delete task?")
        },

        text = {
            Text(
                text =
                    "Delete “${task.title}”? " +
                            "Existing check-in history will remain, " +
                            "but the task itself cannot be recovered."
            )
        },

        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text(text = "DELETE")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(text = "Cancel")
            }
        }
    )
}

private data class CheckInStatusOption(
    val status: String,
    val label: String
)

private val checkInStatusOptions = listOf(
    CheckInStatusOption(
        TaskStatus.IN_PROGRESS,
        "In progress"
    ),
    CheckInStatusOption(
        TaskStatus.COMPLETED,
        "Completed"
    ),
    CheckInStatusOption(
        TaskStatus.PAUSED,
        "Paused"
    ),
    CheckInStatusOption(
        TaskStatus.BLOCKED,
        "Blocked"
    )
)

@Composable
private fun CheckInDialog(
    tasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onNoTasksClose: () -> Unit,
    onSubmit: (
        TaskEntity,
        String,
        String,
        TaskEntity?
    ) -> Unit
) {
    val availableTasks = tasks.filter {
        it.status != TaskStatus.COMPLETED
    }
    if (availableTasks.isEmpty()) {
        AlertDialog(
            onDismissRequest = onNoTasksClose,
            title = {
                Text(text = "No unfinished tasks")
            },
            text = {
                Text(
                    text = "There are no active tasks to check in on."
                )
            },
            confirmButton = {
                Button(
                    onClick = onNoTasksClose
                ) {
                    Text(text = "CLOSE")
                }
            }
        )

        return
    }

    val initiallySelectedTaskId =
        availableTasks.firstOrNull {
            it.status == TaskStatus.IN_PROGRESS
        }?.id ?: availableTasks.firstOrNull()?.id

    var selectedTaskId by rememberSaveable {
        mutableStateOf(initiallySelectedTaskId)
    }

    var selectedStatus by rememberSaveable {
        mutableStateOf(TaskStatus.IN_PROGRESS)
    }

    var note by rememberSaveable {
        mutableStateOf("")
    }

    var nextTaskId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    val selectedTask = availableTasks.firstOrNull {
        it.id == selectedTaskId
    }

    val possibleNextTasks = availableTasks.filter {
        it.id != selectedTaskId
    }

    val selectedNextTask = possibleNextTasks.firstOrNull {
        it.id == nextTaskId
    }

    val noteRequired =
        selectedStatus == TaskStatus.PAUSED ||
                selectedStatus == TaskStatus.BLOCKED

    val submitEnabled =
        selectedTask != null &&
                (!noteRequired || note.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Progress check-in")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                ),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "What are you working on?",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (availableTasks.isEmpty()) {
                    Text(
                        text = "There are no unfinished tasks.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    availableTasks.forEach { task ->
                        if (task.id == selectedTaskId) {
                            Button(
                                onClick = {
                                    selectedTaskId = task.id

                                    if (
                                        nextTaskId == task.id
                                    ) {
                                        nextTaskId = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = task.title)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedTaskId = task.id

                                    if (
                                        nextTaskId == task.id
                                    ) {
                                        nextTaskId = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = task.title)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Current status",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                checkInStatusOptions.forEach { option ->
                    if (option.status == selectedStatus) {
                        Button(
                            onClick = {
                                selectedStatus = option.status
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = option.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedStatus = option.status
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = option.label)
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = when (selectedStatus) {
                                TaskStatus.BLOCKED ->
                                    "Why are you blocked?"

                                TaskStatus.PAUSED ->
                                    "Why are you pausing?"

                                TaskStatus.COMPLETED ->
                                    "What did you complete?"

                                else ->
                                    "What have you accomplished?"
                            }
                        )
                    },
                    supportingText = {
                        if (noteRequired) {
                            Text(text = "A reason is required.")
                        }
                    },
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Switch to another task?",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (nextTaskId == null) {
                    Button(
                        onClick = {
                            nextTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Not selected")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            nextTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Not selected")
                    }
                }

                possibleNextTasks.forEach { task ->
                    if (task.id == nextTaskId) {
                        Button(
                            onClick = {
                                nextTaskId = task.id
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = task.title)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                nextTaskId = task.id
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = task.title)
                        }
                    }
                }

                Text(
                    text = "Selecting another task switches to it immediately. The current task will be completed, paused, or blocked according to the status above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = submitEnabled,
                onClick = {
                    val task =
                        selectedTask ?: return@TextButton

                    onSubmit(
                        task,
                        selectedStatus,
                        note,
                        selectedNextTask
                    )
                }
            ) {
                Text(text = "Submit")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun RatingSelector(
    label: String,
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit
) {
    Column {
        Text(
            text = "$label: $selectedRating",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (rating in 1..5) {
                if (rating == selectedRating) {
                    Button(
                        onClick = {
                            onRatingSelected(rating)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = 0.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text(text = rating.toString())
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            onRatingSelected(rating)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = 0.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text(text = rating.toString())
                    }
                }
            }
        }
    }
}


@Composable
private fun CalendarScreen(
    tasks: List<TaskEntity>,
    displayedMonthEpochDay: Long,
    selectedDateEpochDay: Long,
    onDisplayedMonthChanged: (Long) -> Unit,
    onDateSelected: (Long) -> Unit,
    onAddTaskForDate: (Long) -> Unit,
    onEditTask: (TaskEntity) -> Unit
) {
    val displayedMonth =
        YearMonth.from(
            LocalDate.ofEpochDay(
                displayedMonthEpochDay
            )
        )

    val selectedDate =
        LocalDate.ofEpochDay(
            selectedDateEpochDay
        )

    val today = LocalDate.now()

    val tasksByDate =
        tasks
            .filter {
                it.dueDateEpochDay != null
            }
            .groupBy {
                it.dueDateEpochDay!!
            }

    val leadingBlankDays =
        displayedMonth
            .atDay(1)
            .dayOfWeek
            .value - 1

    val calendarDates =
        buildList<LocalDate?> {
            repeat(leadingBlankDays) {
                add(null)
            }

            for (
            day in 1..
                    displayedMonth.lengthOfMonth()
            ) {
                add(
                    displayedMonth.atDay(day)
                )
            }

            while (size % 7 != 0) {
                add(null)
            }
        }

    val selectedDateTasks =
        sortCalendarTasks(
            tasks = tasks.filter {
                it.dueDateEpochDay ==
                        selectedDateEpochDay
            },
            today = today
        )

    val monthTaskGroups =
        tasks
            .filter { task ->
                task.dueDateEpochDay
                    ?.let { epochDay ->
                        YearMonth.from(
                            LocalDate.ofEpochDay(
                                epochDay
                            )
                        ) == displayedMonth
                    }
                    ?: false
            }
            .sortedWith(
                compareBy<TaskEntity> {
                    it.dueDateEpochDay
                        ?: Long.MAX_VALUE
                }.thenBy {
                    if (
                        it.status ==
                        TaskStatus.IN_PROGRESS
                    ) {
                        0
                    } else {
                        1
                    }
                }.thenBy {
                    activeStatusRank(it.status)
                }.thenByDescending {
                    it.priorityScore(today)
                }.thenBy {
                    it.createdAt
                }
            )
            .groupBy {
                it.dueDateEpochDay!!
            }
            .toSortedMap()

    val unscheduledTaskCount =
        tasks.count {
            it.dueDateEpochDay == null &&
                    it.status != TaskStatus.COMPLETED
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 14.dp,
            vertical = 16.dp
        ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        item {
            CalendarMonthCard(
                displayedMonth = displayedMonth,
                selectedDate = selectedDate,
                today = today,
                calendarDates = calendarDates,
                tasksByDate = tasksByDate,

                onPreviousMonth = {
                    val previousMonth =
                        displayedMonth
                            .minusMonths(1)

                    onDisplayedMonthChanged(
                        previousMonth
                            .atDay(1)
                            .toEpochDay()
                    )

                    onDateSelected(
                        defaultCalendarSelection(
                            previousMonth
                        ).toEpochDay()
                    )
                },

                onNextMonth = {
                    val nextMonth =
                        displayedMonth
                            .plusMonths(1)

                    onDisplayedMonthChanged(
                        nextMonth
                            .atDay(1)
                            .toEpochDay()
                    )

                    onDateSelected(
                        defaultCalendarSelection(
                            nextMonth
                        ).toEpochDay()
                    )
                },

                onToday = {
                    val currentMonth =
                        YearMonth.now()

                    onDisplayedMonthChanged(
                        currentMonth
                            .atDay(1)
                            .toEpochDay()
                    )

                    onDateSelected(
                        today.toEpochDay()
                    )
                },

                onDateSelected = {
                        date ->

                    onDateSelected(
                        date.toEpochDay()
                    )
                }
            )
        }

        item {
            CalendarLegend()
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            formatCalendarHeading(
                                selectedDate
                            ),
                        style =
                            MaterialTheme.typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text = when (
                            selectedDateTasks.size
                        ) {
                            0 ->
                                "No tasks due"

                            1 ->
                                "1 task due"

                            else ->
                                "${selectedDateTasks.size} tasks due"
                        },
                        style =
                            MaterialTheme.typography
                                .bodyMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        onAddTaskForDate(
                            selectedDateEpochDay
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = "Add task")
                }
            }
        }

        if (selectedDateTasks.isEmpty()) {
            item {
                EmptyTaskCard(
                    title = "Nothing due",
                    description =
                        "Select Add task to create a task for this date."
                )
            }
        } else {
            items(
                items = selectedDateTasks,
                key = { task ->
                    "selected-${task.id}"
                }
            ) { task ->
                CalendarAgendaTaskCard(
                    task = task,
                    onEditTask = onEditTask
                )
            }
        }

        item {
            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Month agenda",
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "${monthTaskGroups.values.sumOf { it.size }} scheduled in ${formatCalendarMonth(displayedMonth)}",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        if (monthTaskGroups.isEmpty()) {
            item {
                EmptyTaskCard(
                    title = "No dated tasks",
                    description =
                        "There are no tasks scheduled in this month."
                )
            }
        } else {
            monthTaskGroups.forEach {
                    epochDay,
                    dayTasks ->

                item(
                    key = "month-heading-$epochDay"
                ) {
                    Text(
                        text =
                            formatCalendarHeading(
                                LocalDate.ofEpochDay(
                                    epochDay
                                )
                            ),
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            MaterialTheme.colorScheme
                                .primary
                    )
                }

                items(
                    items = dayTasks,
                    key = { task ->
                        "month-${task.id}"
                    }
                ) { task ->
                    CalendarAgendaTaskCard(
                        task = task,
                        onEditTask = onEditTask
                    )
                }
            }
        }

        if (unscheduledTaskCount > 0) {
            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(18.dp)
                    ) {
                        Text(
                            text =
                                "$unscheduledTaskCount unscheduled",
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                "These unfinished tasks do not have a due date and will not appear on the calendar.",
                            style =
                                MaterialTheme.typography
                                    .bodyMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLegend() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.46f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Calendar markers",
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                CalendarLegendItem(
                    label = "In progress",
                    color =
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                CalendarLegendItem(
                    label = "Blocked",
                    color =
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                CalendarLegendItem(
                    label = "Open",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                CalendarLegendItem(
                    label = "Completed",
                    color =
                        MaterialTheme.colorScheme
                            .secondary,
                    modifier = Modifier.weight(1f),
                    symbol = "✓"
                )
            }
        }
    }
}

@Composable
private fun CalendarLegendItem(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    symbol: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (symbol == null) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = color
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        } else {
            Text(
                text = symbol,
                color = color,
                style =
                    MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style =
                MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarMonthCard(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    calendarDates: List<LocalDate?>,
    tasksByDate: Map<Long, List<TaskEntity>>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription =
                                "Previous month"
                        },
                    contentPadding =
                        PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                ) {
                    Text(
                        text = "‹",
                        style =
                            MaterialTheme.typography
                                .titleLarge
                    )
                }

                Text(
                    text =
                        formatCalendarMonth(
                            displayedMonth
                        ),
                    modifier = Modifier.weight(1f),
                    style =
                        MaterialTheme.typography
                            .titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                OutlinedButton(
                    onClick = onNextMonth,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription =
                                "Next month"
                        },
                    contentPadding =
                        PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                ) {
                    Text(
                        text = "›",
                        style =
                            MaterialTheme.typography
                                .titleLarge
                    )
                }
            }

            TextButton(
                onClick = onToday,
                modifier = Modifier
                    .align(
                        Alignment.CenterHorizontally
                    )
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = "GO TO TODAY",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                calendarWeekdayLabels
                    .forEach { weekday ->
                        Text(
                            text = weekday,
                            modifier =
                                Modifier.weight(1f),
                            style =
                                MaterialTheme.typography
                                    .labelMedium,
                            fontWeight =
                                FontWeight.SemiBold,
                            textAlign =
                                TextAlign.Center,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            calendarDates
                .chunked(7)
                .forEach { week ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .aspectRatio(
                                                0.86f
                                            )
                                )
                            } else {
                                CalendarDayCell(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .aspectRatio(
                                                0.86f
                                            ),
                                    date = date,
                                    isSelected =
                                        date ==
                                                selectedDate,
                                    isToday =
                                        date == today,
                                    tasks =
                                        tasksByDate[
                                            date.toEpochDay()
                                        ].orEmpty(),
                                    onClick = {
                                        onDateSelected(
                                            date
                                        )
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
private fun CalendarDayCell(
    modifier: Modifier = Modifier,
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    tasks: List<TaskEntity>,
    onClick: () -> Unit
) {
    val unfinishedTasks =
        tasks.filter {
            it.status != TaskStatus.COMPLETED
        }

    val completedTaskCount =
        tasks.size - unfinishedTasks.size

    val blocked =
        unfinishedTasks.any {
            it.status == TaskStatus.BLOCKED
        }

    val inProgress =
        unfinishedTasks.any {
            it.status == TaskStatus.IN_PROGRESS
        }

    val markerColor =
        when {
            blocked ->
                MaterialTheme.colorScheme.error

            inProgress ->
                MaterialTheme.colorScheme.primary

            unfinishedTasks.isNotEmpty() ->
                MaterialTheme.colorScheme
                    .onSurfaceVariant

            else ->
                MaterialTheme.colorScheme.secondary
        }

    val containerColor =
        when {
            isSelected ->
                MaterialTheme.colorScheme
                    .primaryContainer

            isToday ->
                MaterialTheme.colorScheme
                    .secondaryContainer

            else ->
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.55f)
        }

    val dateDescription =
        buildString {
            append(formatCalendarHeading(date))

            if (isToday) {
                append(", today")
            }

            if (isSelected) {
                append(", selected")
            }

            append(". ")

            when {
                tasks.isEmpty() ->
                    append("No tasks due.")

                unfinishedTasks.isNotEmpty() &&
                        completedTaskCount > 0 -> {
                    append(
                        "${unfinishedTasks.size} unfinished and "
                    )
                    append(
                        "$completedTaskCount completed."
                    )
                }

                unfinishedTasks.isNotEmpty() ->
                    append(
                        "${unfinishedTasks.size} unfinished."
                    )

                else ->
                    append(
                        "$completedTaskCount completed."
                    )
            }
        }

    val shape = RoundedCornerShape(12.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = dateDescription
                role = Role.Button
            },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border =
            when {
                isSelected ->
                    BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.primary
                    )

                isToday ->
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary
                    )

                else -> null
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.SpaceBetween
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                fontWeight =
                    if (
                        isSelected ||
                        isToday
                    ) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    }
            )

            if (tasks.isNotEmpty()) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(3.dp)
                ) {
                    if (unfinishedTasks.isNotEmpty()) {
                        Surface(
                            modifier =
                                Modifier.size(7.dp),
                            shape = CircleShape,
                            color = markerColor
                        ) {
                            Box(
                                modifier =
                                    Modifier.fillMaxSize()
                            )
                        }

                        Text(
                            text =
                                unfinishedTasks.size
                                    .toString(),
                            style =
                                MaterialTheme.typography
                                    .labelSmall,
                            fontWeight =
                                FontWeight.Bold,
                            color = markerColor
                        )
                    }

                    if (completedTaskCount > 0) {
                        Text(
                            text = "✓$completedTaskCount",
                            style =
                                MaterialTheme.typography
                                    .labelSmall,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme.colorScheme
                                    .secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarAgendaTaskCard(
    task: TaskEntity,
    onEditTask: (TaskEntity) -> Unit
) {
    val isCompleted =
        task.status == TaskStatus.COMPLETED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${task.title}. ${statusLabel(task.status)}. Priority score ${task.priorityScore()}."
            },
        colors = CardDefaults.cardColors(
            containerColor = taskCardContainerColor(task)
        ),
        shape = RoundedCornerShape(16.dp),
        border = taskCardBorderColor(task)?.let {
            BorderStroke(1.dp, it)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                TaskStatusBadge(
                    status = task.status
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = task.title,
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                    textDecoration =
                        if (isCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.notes.isNotBlank()) {
                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text = task.notes,
                        style =
                            MaterialTheme.typography
                                .bodyMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text =
                        "Priority score ${task.priorityScore()}",
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                    color =
                        calendarStatusColor(
                            task.status
                        )
                )
            }

            if (
                task.status !=
                TaskStatus.IN_PROGRESS
            ) {
                TextButton(
                    onClick = {
                        onEditTask(task)
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription =
                                "Edit ${task.title}"
                        }
                ) {
                    Text(text = "EDIT")
                }
            }
        }
    }
}

private val calendarWeekdayLabels =
    listOf(
        "M",
        "T",
        "W",
        "T",
        "F",
        "S",
        "S"
    )

private fun defaultCalendarSelection(
    month: YearMonth
): LocalDate {
    val today = LocalDate.now()

    return if (
        YearMonth.from(today) == month
    ) {
        today
    } else {
        month.atDay(1)
    }
}

private fun formatCalendarMonth(
    month: YearMonth
): String {
    val formatter =
        DateTimeFormatter.ofPattern(
            "MMMM yyyy",
            Locale.getDefault()
        )

    return month
        .atDay(1)
        .format(formatter)
}

private fun formatCalendarHeading(
    date: LocalDate
): String {
    val formatter =
        DateTimeFormatter.ofPattern(
            "EEEE, MMMM d",
            Locale.getDefault()
        )

    return date.format(formatter)
}

private fun calendarStatusRank(
    status: String
): Int {
    return when (status) {
        TaskStatus.IN_PROGRESS -> 0
        TaskStatus.NOT_STARTED -> 1
        TaskStatus.PAUSED -> 2
        TaskStatus.BLOCKED -> 3
        TaskStatus.COMPLETED -> 4
        else -> 5
    }
}

@Composable
private fun calendarStatusColor(
    status: String
): Color {
    return when (status) {
        TaskStatus.BLOCKED ->
            MaterialTheme.colorScheme.error

        TaskStatus.IN_PROGRESS ->
            MaterialTheme.colorScheme.primary

        TaskStatus.COMPLETED ->
            MaterialTheme.colorScheme
                .onSurfaceVariant

        else ->
            MaterialTheme.colorScheme
                .onSurface
    }
}

@Composable
private fun HistoryScreen(
    checkIns: List<CheckInEntity>
) {
    if (checkIns.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No check-ins yet",
                    style =
                        MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your progress reports will appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                )
            }
        }

        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Check-in history",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${checkIns.size} recorded",
                style = MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(
            items = checkIns,
            key = { checkIn ->
                checkIn.id
            }
        ) { checkIn ->
            CheckInHistoryCard(
                checkIn = checkIn
            )
        }
    }
}

@Composable
private fun CheckInHistoryCard(
    checkIn: CheckInEntity
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Check-in for ${checkIn.taskTitle}. ${statusLabel(checkIn.reportedStatus)}. ${formatCheckInTime(checkIn.createdAt)}."
            },
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = formatCheckInTime(
                    checkIn.createdAt
                ),
                style =
                    MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            TaskStatusBadge(
                status = checkIn.reportedStatus
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = checkIn.taskTitle,
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (checkIn.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = checkIn.note,
                    style =
                        MaterialTheme.typography.bodyLarge,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!checkIn.nextTaskTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text =
                        "Next: ${checkIn.nextTaskTitle}",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatCheckInTime(
    timestamp: Long
): String {
    val formatter = DateTimeFormatter.ofPattern(
        "EEE, MMM d · h:mm a",
        Locale.getDefault()
    )

    return Instant
        .ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
private fun formatTaskDate(
    epochDay: Long
): String {
    val formatter =
        DateTimeFormatter.ofPattern(
            "EEE, MMM d, yyyy",
            Locale.getDefault()
        )

    return LocalDate
        .ofEpochDay(epochDay)
        .format(formatter)
}

private fun dueDateLabel(
    epochDay: Long
): String {
    val dueDate =
        LocalDate.ofEpochDay(epochDay)

    val today =
        LocalDate.now()

    return when {
        dueDate.isBefore(today) -> {
            "OVERDUE · ${formatTaskDate(epochDay)}"
        }

        dueDate == today -> {
            "DUE TODAY"
        }

        dueDate == today.plusDays(1) -> {
            "DUE TOMORROW"
        }

        else -> {
            "Due ${formatTaskDate(epochDay)}"
        }
    }
}

@Composable
private fun dueDateColor(
    epochDay: Long
): Color {
    val dueDate =
        LocalDate.ofEpochDay(epochDay)

    val today =
        LocalDate.now()

    return when {
        dueDate.isBefore(today) -> {
            MaterialTheme.colorScheme.error
        }

        dueDate == today -> {
            MaterialTheme.colorScheme.primary
        }

        else -> {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

private data class ActiveDayOption(
    val number: Int,
    val shortLabel: String,
    val fullLabel: String
)

private val activeDayOptions = listOf(
    ActiveDayOption(1, "Mon", "Monday"),
    ActiveDayOption(2, "Tue", "Tuesday"),
    ActiveDayOption(3, "Wed", "Wednesday"),
    ActiveDayOption(4, "Thu", "Thursday"),
    ActiveDayOption(5, "Fri", "Friday"),
    ActiveDayOption(6, "Sat", "Saturday"),
    ActiveDayOption(7, "Sun", "Sunday")
)

@Composable
private fun SettingsScreen(
    settings: ReminderSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onStartTimeChanged: (Int, Int) -> Unit,
    onEndTimeChanged: (Int, Int) -> Unit,
    onCheckInIntervalChanged: (Int) -> Unit,
    onMissedReminderChanged: (Int) -> Unit,
    onDayChanged: (Int, Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onScheduleTestReminder: () -> Unit,
    onStartRepeatedReminderTest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = "Reminder schedule",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Choose when Focus Check should ask for progress updates.",
                style = MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Check-ins enabled",
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (
                                settings.remindersEnabled
                            ) {
                                "The schedule is enabled."
                            } else {
                                "The schedule is currently disabled."
                            },
                            style =
                                MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = settings.remindersEnabled,
                        onCheckedChange = onEnabledChanged,
                        modifier = Modifier.semantics {
                            contentDescription =
                                "Enable scheduled check-ins"
                        }
                    )
                }
            }
        }

        item {
            Text(
                text = "Active hours",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            TimeSettingRow(
                title = "Start time",
                hour = settings.startHour,
                minute = settings.startMinute,
                onTimeSelected = onStartTimeChanged
            )

            HorizontalDivider()

            TimeSettingRow(
                title = "End time",
                hour = settings.endHour,
                minute = settings.endMinute,
                onTimeSelected = onEndTimeChanged
            )

            if (!settings.activeWindowIsValid) {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "The end time must be later than the start time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item {
            Text(
                text = "Active days",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            ActiveDayRow(
                options = activeDayOptions.take(4),
                activeDays = settings.activeDays,
                onDayChanged = onDayChanged
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActiveDayRow(
                options = activeDayOptions.drop(4),
                activeDays = settings.activeDays,
                onDayChanged = onDayChanged
            )

            if (settings.activeDays.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Select at least one active day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item {
            DurationSettingRow(
                title = "Regular check-in interval",
                description =
                    "How often the normal progress check appears.",
                minutes =
                    settings.checkInIntervalMinutes,
                minimumMinutes = 15,
                maximumMinutes = 240,
                stepMinutes = 5,
                onMinutesChanged =
                    onCheckInIntervalChanged
            )
        }

        item {
            DurationSettingRow(
                title = "Missed check-in reminder",
                description =
                    "How often the app reminds you until you respond.",
                minutes =
                    settings.missedReminderMinutes,
                minimumMinutes = 10,
                maximumMinutes = 60,
                stepMinutes = 5,
                onMinutesChanged =
                    onMissedReminderChanged
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "Current schedule",
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = scheduleSummary(settings),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Changes are saved automatically and applied to the next reminder cycle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "About Focus Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Task planning, due-date calendars, progress check-ins, and focused reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )
                }
            }
        }

        /*
         * These controls remain available while running a debug build,
         * but they are not shown in a signed release build.
         */
        if (BuildConfig.DEBUG) {
            item {
                Column {
                    Text(
                        text = "Developer tools",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "These test controls appear only in debug builds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "Notification test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Confirm that Android allows Focus Check to display progress reminders.",
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onTestNotification,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            Text(
                                text = "SEND TEST NOTIFICATION",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "Scheduled alarm test",
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = "Schedule an AlarmManager reminder approximately one minute from now. Android may delay an inexact alarm.",
                            style =
                                MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )

                        Spacer(
                            modifier = Modifier.height(14.dp)
                        )

                        Button(
                            onClick =
                                onScheduleTestReminder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            Text(
                                text =
                                    "SCHEDULE 1-MINUTE TEST",
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "Repeated-reminder test",
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = "Display a check-in now, then repeat it approximately one minute later if you do not submit a response.",
                            style =
                                MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )

                        Spacer(
                            modifier = Modifier.height(14.dp)
                        )

                        Button(
                            onClick =
                                onStartRepeatedReminderTest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            Text(
                                text =
                                    "START 1-MINUTE REPEAT TEST",
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSettingRow(
    title: String,
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val formattedTime =
        formatTime(
            hour = hour,
            minute = minute
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style =
                MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    {
                            _,
                            selectedHour,
                            selectedMinute ->

                        onTimeSelected(
                            selectedHour,
                            selectedMinute
                        )
                    },
                    hour,
                    minute,
                    false
                ).show()
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription =
                        "$title, $formattedTime"
                }
        ) {
            Text(text = formattedTime)
        }
    }
}

@Composable
private fun ActiveDayRow(
    options: List<ActiveDayOption>,
    activeDays: Set<Int>,
    onDayChanged: (Int, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { day ->
            val selected = day.number in activeDays

            FilterChip(
                selected = selected,
                onClick = {
                    onDayChanged(
                        day.number,
                        !selected
                    )
                },
                label = {
                    Text(text = day.shortLabel)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DurationSettingRow(
    title: String,
    description: String,
    minutes: Int,
    minimumMinutes: Int,
    maximumMinutes: Int,
    stepMinutes: Int,
    onMinutesChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    enabled = minutes > minimumMinutes,
                    onClick = {
                        onMinutesChanged(
                            (minutes - stepMinutes)
                                .coerceAtLeast(
                                    minimumMinutes
                                )
                        )
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .semantics {
                            contentDescription =
                                "Decrease $title"
                        },
                    contentPadding =
                        PaddingValues(0.dp)
                ) {
                    Text(
                        text = "−",
                        style =
                            MaterialTheme.typography
                                .titleLarge
                    )
                }

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatMinutes(minutes),
                        style =
                            MaterialTheme.typography
                                .titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "Range ${formatMinutes(minimumMinutes)} to ${formatMinutes(maximumMinutes)}",
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }

                OutlinedButton(
                    enabled = minutes < maximumMinutes,
                    onClick = {
                        onMinutesChanged(
                            (minutes + stepMinutes)
                                .coerceAtMost(
                                    maximumMinutes
                                )
                        )
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .semantics {
                            contentDescription =
                                "Increase $title"
                        },
                    contentPadding =
                        PaddingValues(0.dp)
                ) {
                    Text(
                        text = "+",
                        style =
                            MaterialTheme.typography
                                .titleLarge
                    )
                }
            }
        }
    }
}

private data class ReminderDisplayState(
    val label: String,
    val title: String,
    val description: String,
    val isPending: Boolean = false
)

private fun reminderDisplayState(
    settings: ReminderSettings,
    runtimeState: ReminderRuntimeState,
    unfinishedTaskCount: Int,
    now: ZonedDateTime = ZonedDateTime.now()
): ReminderDisplayState {
    if (unfinishedTaskCount <= 0) {
        return ReminderDisplayState(
            label = "Next check-in",
            title = "No reminder needed",
            description = "Add or reopen a task before the next progress check-in can be useful."
        )
    }

    if (!settings.remindersEnabled) {
        return ReminderDisplayState(
            label = "Next check-in",
            title = "Check-ins are disabled",
            description = "Enable check-ins in Settings to schedule progress reminders."
        )
    }

    if (
        !settings.activeWindowIsValid ||
        settings.activeDays.isEmpty()
    ) {
        return ReminderDisplayState(
            label = "Next check-in",
            title = "Schedule needs attention",
            description = "Choose at least one active day and a valid start and end time in Settings."
        )
    }

    if (runtimeState.checkInPending) {
        val retryMinutes =
            runtimeState.retryDelayMillis
                ?.takeIf { it > 0L }
                ?.div(60_000L)
                ?.toInt()
                ?: settings.missedReminderMinutes

        return ReminderDisplayState(
            label = "Check-in pending",
            title = "Waiting for your response",
            description = "The reminder repeats every ${formatMinutes(retryMinutes)} until you submit a check-in.",
            isPending = true
        )
    }

    val nextReminder = calculateNextReminder(
        settings = settings,
        now = now
    )

    if (nextReminder == null) {
        return ReminderDisplayState(
            label = "Next check-in",
            title = "No upcoming reminder found",
            description = "Review the active days and reminder window in Settings."
        )
    }

    return ReminderDisplayState(
        label = "Next scheduled check-in",
        title = formatReminderDateTime(
            dateTime = nextReminder,
            today = now.toLocalDate()
        ),
        description = "Scheduled every ${formatMinutes(settings.checkInIntervalMinutes)} during your active reminder window."
    )
}

private fun calculateNextReminder(
    settings: ReminderSettings,
    now: ZonedDateTime
): ZonedDateTime? {
    if (
        !settings.remindersEnabled ||
        !settings.activeWindowIsValid ||
        settings.activeDays.isEmpty()
    ) {
        return null
    }

    val intervalMinutes =
        settings.checkInIntervalMinutes
            .toLong()
            .coerceAtLeast(1L)

    for (dayOffset in 0L..7L) {
        val date = now.toLocalDate().plusDays(dayOffset)

        if (date.dayOfWeek.value !in settings.activeDays) {
            continue
        }

        val startTime =
            date.atTime(
                settings.startHour,
                settings.startMinute
            ).atZone(now.zone)

        val endTime =
            date.atTime(
                settings.endHour,
                settings.endMinute
            ).atZone(now.zone)

        if (dayOffset > 0L) {
            return startTime
        }

        if (now.isBefore(startTime)) {
            return startTime
        }

        if (now.isBefore(endTime)) {
            val minutesSinceStart =
                Duration
                    .between(startTime, now)
                    .toMinutes()
                    .coerceAtLeast(0L)

            val completedIntervals =
                minutesSinceStart / intervalMinutes

            val candidate =
                startTime.plusMinutes(
                    (completedIntervals + 1L) *
                            intervalMinutes
                )

            if (candidate.isBefore(endTime)) {
                return candidate
            }
        }
    }

    return null
}

private fun formatReminderDateTime(
    dateTime: ZonedDateTime,
    today: LocalDate
): String {
    val timeFormatter =
        DateTimeFormatter.ofPattern(
            "h:mm a",
            Locale.getDefault()
        )

    val timeText = dateTime.format(timeFormatter)
    val reminderDate = dateTime.toLocalDate()

    return when (reminderDate) {
        today -> "Today at $timeText"
        today.plusDays(1) -> "Tomorrow at $timeText"
        else -> {
            val dateFormatter =
                DateTimeFormatter.ofPattern(
                    "EEE, MMM d",
                    Locale.getDefault()
                )

            "${reminderDate.format(dateFormatter)} at $timeText"
        }
    }
}

private fun sortedActiveTasks(
    tasks: List<TaskEntity>,
    today: LocalDate = LocalDate.now()
): List<TaskEntity> {
    return tasks
        .filter {
            it.status != TaskStatus.COMPLETED
        }
        .sortedWith(
            taskPriorityComparator(today)
        )
}

private fun sortedCompletedTasks(
    tasks: List<TaskEntity>
): List<TaskEntity> {
    return tasks
        .filter {
            it.status == TaskStatus.COMPLETED
        }
        .sortedWith(
            compareByDescending<TaskEntity> {
                it.dueDateEpochDay
                    ?: Long.MIN_VALUE
            }.thenByDescending {
                it.createdAt
            }
        )
}

private fun sortCalendarTasks(
    tasks: List<TaskEntity>,
    today: LocalDate = LocalDate.now()
): List<TaskEntity> {
    return tasks.sortedWith(
        taskPriorityComparator(today)
    )
}

private fun taskPriorityComparator(
    today: LocalDate
): Comparator<TaskEntity> {
    return compareBy<TaskEntity> {
        if (it.status == TaskStatus.IN_PROGRESS) {
            0
        } else {
            1
        }
    }.thenBy {
        dueDateRank(
            dueDateEpochDay = it.dueDateEpochDay,
            today = today
        )
    }.thenBy {
        activeStatusRank(it.status)
    }.thenByDescending {
        it.priorityScore(today)
    }.thenBy {
        it.dueDateEpochDay
            ?: Long.MAX_VALUE
    }.thenBy {
        it.createdAt
    }
}

private fun dueDateRank(
    dueDateEpochDay: Long?,
    today: LocalDate
): Int {
    if (dueDateEpochDay == null) {
        return 5
    }

    val daysUntilDue =
        dueDateEpochDay - today.toEpochDay()

    return when {
        daysUntilDue < 0L -> 0
        daysUntilDue == 0L -> 1
        daysUntilDue == 1L -> 2
        daysUntilDue <= 7L -> 3
        else -> 4
    }
}

private fun activeStatusRank(
    status: String
): Int {
    return when (status) {
        TaskStatus.IN_PROGRESS -> 0
        TaskStatus.NOT_STARTED -> 1
        TaskStatus.PAUSED -> 2
        TaskStatus.BLOCKED -> 3
        TaskStatus.COMPLETED -> 4
        else -> 5
    }
}

private fun formatTime(
    hour: Int,
    minute: Int
): String {
    val localTime = LocalTime.of(
        hour.coerceIn(0, 23),
        minute.coerceIn(0, 59)
    )

    val formatter = DateTimeFormatter.ofPattern(
        "h:mm a",
        Locale.getDefault()
    )

    return localTime.format(formatter)
}

private fun formatMinutes(minutes: Int): String {
    return when {
        minutes < 60 -> {
            "$minutes minutes"
        }

        minutes % 60 == 0 -> {
            val hours = minutes / 60

            if (hours == 1) {
                "1 hour"
            } else {
                "$hours hours"
            }
        }

        else -> {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60

            "${hours}h ${remainingMinutes}m"
        }
    }
}

private fun scheduleSummary(
    settings: ReminderSettings
): String {
    val enabledText =
        if (settings.remindersEnabled) {
            "Enabled"
        } else {
            "Disabled"
        }

    val dayText =
        activeDayOptions
            .filter {
                it.number in settings.activeDays
            }
            .joinToString(", ") {
                it.fullLabel
            }
            .ifBlank {
                "No active days"
            }

    return buildString {
        append(enabledText)
        append("\n")
        append(dayText)
        append("\n")
        append(
            formatTime(
                settings.startHour,
                settings.startMinute
            )
        )
        append(" to ")
        append(
            formatTime(
                settings.endHour,
                settings.endMinute
            )
        )
        append("\n")
        append("Check in every ")
        append(
            formatMinutes(
                settings.checkInIntervalMinutes
            )
        )
        append("\n")
        append("Repeat missed reminders every ")
        append(
            formatMinutes(
                settings.missedReminderMinutes
            )
        )
    }
}

private fun statusLabel(status: String): String {
    return when (status) {
        TaskStatus.NOT_STARTED -> "Not started"
        TaskStatus.IN_PROGRESS -> "In progress"
        TaskStatus.PAUSED -> "Paused"
        TaskStatus.COMPLETED -> "Completed"
        TaskStatus.BLOCKED -> "Blocked"
        else -> status
    }
}