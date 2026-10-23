package com.politex.util

import android.content.Context
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import com.politex.ui.theme.AppTheme
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    private const val FORMAT_VERSION = 2

    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_APP = "app"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_TASKS = "tasks"
    private const val KEY_GRADES = "grades"
    private const val KEY_REMINDERS = "reminders"
    private const val KEY_LESSONS = "lessons"
    private const val KEY_SUBJECT_RENAMES = "subjectRenames"

    enum class BackupSection {
        SCHEDULE_HISTORY,
        TASKS,
        GRADES,
        SUBJECT_NAMES,
        SETTINGS,
        ;

        companion object {
            val ALL: Set<BackupSection> = entries.toSet()
        }
    }

    data class ImportResult(
        val tasksRestored: Int,
        val groupRestored: String?,
        val themeRestored: Boolean,
        val warnings: List<String>,
        val gradesRestored: Int = 0,
        val remindersRestored: Int = 0,
        val lessonsRestored: Int = 0,
        val subjectRenamesRestored: Int = 0,
    )

    suspend fun buildBackup(
        context: Context,
        sections: Set<BackupSection> = BackupSection.ALL,
    ): String {
        val root = JSONObject()
        root.put(KEY_VERSION, FORMAT_VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(KEY_APP, "Мой Политех")

        val db = MpkDatabase.getInstance(context)

        if (BackupSection.SETTINGS in sections) {
            val settings = JSONObject()
            settings.put("group", WidgetUpdateHelper.getSelectedGroup(context))
            settings.put("teacherMode", TeacherModeStore.teacherName(context) ?: "")
            settings.put("notificationsEnabled", WidgetUpdateHelper.isNotificationEnabled(context))
            settings.put("appTheme", AppThemeStore.load(context).id)
            settings.put("appDark", AppThemeStore.isDark(context))
            settings.put("bellLineEnabled", BellCountdownNotifier.isEnabled(context))
            settings.put("notificationsStyle", BellCountdownNotifier.getStyle(context).id)
            settings.put("notificationsTheme", BellCountdownNotifier.getTheme(context).id)
            settings.put("notificationsScope", BellCountdownNotifier.getScope(context).id)
            root.put(KEY_SETTINGS, settings)
        }

        if (BackupSection.TASKS in sections) {
            val reminderDao = db.taskReminderDao()
            val tasks = db.taskDao().getAllTasks()
            val array = JSONArray()
            tasks.forEach { task ->
                array.put(
                    taskToJson(task).apply {
                        val reminders = reminderDao.getForTask(task.id)
                        if (reminders.isNotEmpty()) {
                            put(
                                KEY_REMINDERS,
                                JSONArray(reminders.map { it.triggerAtMillis }),
                            )
                        }
                    },
                )
            }
            root.put(KEY_TASKS, array)
        }

        if (BackupSection.GRADES in sections) {
            val grades = db.gradeDao().allEntries().first()
            val gradesArray = JSONArray()
            grades.forEach { grade ->
                gradesArray.put(
                    JSONObject()
                        .put("subjectName", grade.subjectName)
                        .put("category", grade.category)
                        .put("value", grade.value)
                        .put("label", grade.label)
                        .put("dateIso", grade.dateIso)
                        .put("createdAt", grade.createdAt),
                )
            }
            root.put(KEY_GRADES, gradesArray)
        }

        if (BackupSection.SCHEDULE_HISTORY in sections) {
            val lessonsArray = JSONArray()
            db.lessonDao().getAllLessonsSync().forEach { lesson ->
                lessonsArray.put(lessonToJson(lesson))
            }
            root.put(KEY_LESSONS, lessonsArray)
        }

        if (BackupSection.SUBJECT_NAMES in sections) {
            val renames = JSONObject()
            SubjectRenameStore.allRenames(context).forEach { (original, custom) ->
                renames.put(original, custom)
            }
            root.put(KEY_SUBJECT_RENAMES, renames)
        }

        return root.toString(2)
    }

    private fun taskToJson(task: StudentTaskEntity): JSONObject = JSONObject().apply {
        put("groupName", task.groupName)
        put("course", task.course)
        put("subjectName", task.subjectName)
        put("taskType", task.taskType)
        put("title", task.title)
        put("description", task.description)
        put("isCompleted", task.isCompleted)
        put("deadlineDate", task.deadlineDate)
        put("createdAt", task.createdAt)
    }

    private fun lessonToJson(lesson: LessonEntity): JSONObject = JSONObject().apply {
        put("groupName", lesson.groupName)
        put("dayOfWeek", lesson.dayOfWeek)
        put("lessonNumber", lesson.lessonNumber)
        put("timeStart", lesson.timeStart)
        put("timeEnd", lesson.timeEnd)
        put("subjectRaw", lesson.subjectRaw)
        put("roomFirst", lesson.roomFirst)
        put("teacherFirst", lesson.teacherFirst)
        put("roomSecond", lesson.roomSecond)
        put("teacherSecond", lesson.teacherSecond)
        put("isSplit", lesson.isSplit)
        put("dateString", lesson.dateString)
    }

    suspend fun restoreBackup(
        context: Context,
        json: String,
    ): Result<ImportResult> = try {
        val root = JSONObject(json)
        val warnings = mutableListOf<String>()

        val version = root.optInt(KEY_VERSION, 0)
        if (version > FORMAT_VERSION) {
            warnings += "Файл сохранён более новой версией приложения. " +
                "Что удалось — восстановлено, остальное пропущено."
        }

        val db = MpkDatabase.getInstance(context)

        val settings = root.optJSONObject(KEY_SETTINGS)
        var groupRestored: String? = null
        var themeRestored = false

        settings?.optString("group")?.takeIf { it.isNotBlank() }?.let { group ->
            WidgetUpdateHelper.setSelectedGroup(context, group)
            groupRestored = group
        }
        if (settings?.has("teacherMode") == true) {
            val restoredName = settings.optString("teacherMode").trim()
            val before = TeacherModeStore.teacherName(context)
            if (restoredName.isNotBlank()) {
                if (restoredName != before) TeacherModeStore.setTeacher(context, restoredName)
            } else if (before != null) {
                TeacherModeStore.clear(context)
            }
        }
        settings?.optString("appTheme")?.takeIf { it.isNotBlank() }?.let { themeId ->
            val theme = AppTheme.from(themeId)
            AppThemeStore.save(context, theme)
            themeRestored = theme != AppTheme.COLLEGE || themeId == AppTheme.COLLEGE.id
        }
        if (settings?.has("appDark") == true) {
            AppThemeStore.setDark(context, settings.optBoolean("appDark"))
        }
        if (settings?.has("notificationsEnabled") == true) {
            WidgetUpdateHelper.setNotificationEnabled(context, settings.optBoolean("notificationsEnabled"))
        }
        if (settings?.has("bellLineEnabled") == true) {
            BellCountdownNotifier.setEnabled(context, settings.optBoolean("bellLineEnabled"))
        }

        settings?.optString("notificationsStyle")?.takeIf { it.isNotBlank() }?.let {
            BellCountdownNotifier.setStyle(context, BellCountdownNotifier.Style.from(it))
        }
        settings?.optString("notificationsTheme")?.takeIf { it.isNotBlank() }?.let {
            BellCountdownNotifier.setTheme(context, BellCountdownNotifier.Theme.from(it))
        }
        settings?.optString("notificationsScope")?.takeIf { it.isNotBlank() }?.let {
            BellCountdownNotifier.setScope(context, BellCountdownNotifier.Scope.from(it))
        }

        val dao = db.taskDao()
        val reminderDao = db.taskReminderDao()
        val array = root.optJSONArray(KEY_TASKS)
        var restored = 0
        var remindersRestored = 0

        if (array != null) {
            val existing =
                dao
                    .getAllTasks()
                    .map { it.dedupeKey() }
                    .toHashSet()
            var skipped = 0

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title")
                if (title.isBlank()) {
                    warnings += "Задание без названия пропущено"
                    continue
                }
                val entity =
                    StudentTaskEntity(
                        groupName = item.optString("groupName"),
                        course = item.optInt("course", 0),
                        subjectName = item.optString("subjectName"),
                        taskType = item.optString("taskType"),
                        title = title,
                        description = item.optString("description"),
                        isCompleted = item.optBoolean("isCompleted", false),
                        deadlineDate = item.optString("deadlineDate"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    )
                if (!existing.add(entity.dedupeKey())) {
                    skipped++
                    continue
                }
                val newTaskId = dao.insertTask(entity)
                restored++

                val remindersJson = item.optJSONArray(KEY_REMINDERS)
                if (remindersJson != null && remindersJson.length() > 0) {
                    val reminders =
                        (0 until remindersJson.length())
                            .map { remindersJson.optLong(it) }
                            .filter { it > 0L }
                            .map { TaskReminderEntity(taskId = newTaskId, triggerAtMillis = it) }
                    if (reminders.isNotEmpty()) {
                        reminderDao.insertAll(reminders)
                        remindersRestored += reminders.size
                    }
                }
            }
            if (skipped > 0) warnings += "Уже есть, пропущено: $skipped"
        }

        val gradeDao = db.gradeDao()
        val gradesArray = root.optJSONArray(KEY_GRADES)
        var gradesRestored = 0
        if (gradesArray != null) {
            val existingGrades =
                gradeDao
                    .allEntries()
                    .first()
                    .map { it.dedupeKey() }
                    .toHashSet()
            for (i in 0 until gradesArray.length()) {
                val item = gradesArray.optJSONObject(i) ?: continue
                val entry =
                    GradeEntryEntity(
                        subjectName = item.optString("subjectName"),
                        category = item.optString("category"),
                        value = item.optInt("value"),
                        label = item.optString("label"),
                        dateIso = item.optString("dateIso"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    )
                if (entry.subjectName.isBlank() || entry.dateIso.isBlank()) continue
                if (!existingGrades.add(entry.dedupeKey())) continue
                if (entry.category == GradeCategory.EXAM.name) {
                    gradeDao.upsertExam(entry)
                } else {
                    gradeDao.insert(entry)
                }
                gradesRestored++
            }
        }

        val lessonsArray = root.optJSONArray(KEY_LESSONS)
        var lessonsRestored = 0
        if (lessonsArray != null) {
            val byGroup = LinkedHashMap<String, MutableList<LessonEntity>>()
            for (i in 0 until lessonsArray.length()) {
                val item = lessonsArray.optJSONObject(i) ?: continue
                val groupName = item.optString("groupName")
                if (groupName.isBlank()) continue
                val entity =
                    LessonEntity(
                        groupName = groupName,
                        dayOfWeek = item.optInt("dayOfWeek"),
                        lessonNumber = item.optInt("lessonNumber"),
                        timeStart = item.optString("timeStart"),
                        timeEnd = item.optString("timeEnd"),
                        subjectRaw = item.optString("subjectRaw"),
                        roomFirst = item.optString("roomFirst"),
                        teacherFirst = item.optString("teacherFirst"),
                        roomSecond = item.optString("roomSecond"),
                        teacherSecond = item.optString("teacherSecond"),
                        isSplit = item.optBoolean("isSplit", false),
                        dateString = item.optString("dateString"),
                    )
                byGroup.getOrPut(groupName) { mutableListOf() }.add(entity)
            }
            for ((group, lessons) in byGroup) {
                val existing =
                    db
                        .lessonDao()
                        .getAllLessonsForGroup(group)
                        .first()
                        .map { it.dedupeKey() }
                        .toHashSet()
                val fresh = lessons.filter { existing.add(it.dedupeKey()) }
                if (fresh.isNotEmpty()) {
                    db.lessonDao().insertLessons(fresh)
                    lessonsRestored += fresh.size
                }
            }
        }

        val renamesObj = root.optJSONObject(KEY_SUBJECT_RENAMES)
        var subjectRenamesRestored = 0
        if (renamesObj != null) {
            val keys = renamesObj.keys()
            while (keys.hasNext()) {
                val original = keys.next()
                val custom = renamesObj.optString(original, "").trim()
                if (original.isNotBlank() && custom.isNotBlank()) {
                    SubjectRenameStore.setCustomName(context, original, custom)
                    subjectRenamesRestored++
                }
            }
        }

        Result.success(
            ImportResult(
                tasksRestored = restored,
                groupRestored = groupRestored,
                themeRestored = themeRestored,
                warnings = warnings,
                gradesRestored = gradesRestored,
                remindersRestored = remindersRestored,
                lessonsRestored = lessonsRestored,
                subjectRenamesRestored = subjectRenamesRestored,
            ),
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun StudentTaskEntity.dedupeKey(): String = listOf(groupName, subjectName, title, deadlineDate).joinToString("|").lowercase()

private fun GradeEntryEntity.dedupeKey(): String = listOf(subjectName, category, value, label, dateIso).joinToString("|").lowercase()

private fun LessonEntity.dedupeKey(): String = listOf(groupName, dateString, dayOfWeek, lessonNumber, subjectRaw, timeStart, timeEnd, roomFirst, roomSecond)
    .joinToString("|")
    .lowercase()

fun defaultBackupFileName(): String {
    val cal = java.util.Calendar.getInstance()
    val stamp =
        String.format(
            java.util.Locale.ROOT,
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    return "moy-politech-backup-$stamp.json"
}
