package com.politex

import android.app.Application
import com.politex.content.ContentPackStore
import com.politex.data.local.MpkDatabase
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.StudentsRepository
import com.politex.data.repository.TeachersRepository
import com.politex.util.GroupCatalog
import com.politex.util.NotificationHelper

object AppGraph {
    lateinit var scheduleRepository: ScheduleRepository
        private set
    lateinit var teachersRepository: TeachersRepository
        private set
    lateinit var studentsRepository: StudentsRepository
        private set

    fun init(context: Application) {
        val app = context.applicationContext
        ContentPackStore.attach(app)
        val db = MpkDatabase.getInstance(app)
        scheduleRepository =
            ScheduleRepository(
                lessonDao = db.lessonDao(),
                catalogProvider = { GroupCatalog.load(app).all() },
                teacherLessonDao = db.teacherLessonDao(),
            )
        teachersRepository = TeachersRepository(app)
        studentsRepository = StudentsRepository(app)
    }
}

open class MpkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        NotificationHelper.createNotificationChannel(this)
        com.politex.util.BellCountdownNotifier
            .createChannel(this)
    }
}
