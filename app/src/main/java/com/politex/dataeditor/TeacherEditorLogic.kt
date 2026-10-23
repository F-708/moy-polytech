package com.politex.dataeditor

import com.politex.content.OwnerOverridesStore
import com.politex.content.TeacherOverride
import com.politex.data.repository.Teacher
import java.io.File

data class TeacherEditorForm(
    val key: String,
    val fio: String = "",
    val position: String = "",
    val phone: String = "",
    val subjects: String = "",
    val experience: String = "",
    val category: String = "",
    val pendingPhoto: File? = null,
)

enum class TeacherEditorStatus { IDLE, SAVED, DELETED, ERROR }

object TeacherEditorLogic {

    fun formFor(
        key: String,
        teacher: Teacher?,
        override: TeacherOverride?,
    ): TeacherEditorForm = when {
        override != null ->
            TeacherEditorForm(
                key = override.key,
                fio = override.fio,
                position = override.position,
                phone = override.phone,
                subjects = override.subjects,
                experience = override.experience,
                category = override.category,
            )

        teacher != null ->
            TeacherEditorForm(
                key = OwnerOverridesStore.keyOf(teacher.name),
                fio = teacher.name,
                position = teacher.position,
                phone = teacher.phone,
                subjects = teacher.subjects,
                experience = teacher.experience,
                category = teacher.category,
            )

        else -> TeacherEditorForm(key = OwnerOverridesStore.keyOf(key))
    }

    fun isDirty(
        form: TeacherEditorForm,
        initial: TeacherEditorForm,
    ): Boolean = form.pendingPhoto != null ||
        form.fio != initial.fio ||
        form.position != initial.position ||
        form.phone != initial.phone ||
        form.subjects != initial.subjects ||
        form.experience != initial.experience ||
        form.category != initial.category

    fun validate(form: TeacherEditorForm): String? = if (form.fio.isBlank()) "Укажите ФИО преподавателя" else null

    fun canSave(
        form: TeacherEditorForm,
        initial: TeacherEditorForm,
        saving: Boolean,
    ): Boolean = !saving && isDirty(form, initial) && validate(form) == null

    fun statusText(status: TeacherEditorStatus): String = when (status) {
        TeacherEditorStatus.IDLE -> ""
        TeacherEditorStatus.SAVED -> "Сохранено"
        TeacherEditorStatus.DELETED -> "Правка удалена"
        TeacherEditorStatus.ERROR -> "Ошибка сохранения"
    }
}
