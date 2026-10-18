package com.zylinn.petdiary.data.photo

import java.io.File
import java.util.UUID

/** App-private files (avatars, attachments), referenced from the database by file name only. */
class FileStore(private val dir: File) {

    fun file(name: String): File = File(dir, name)

    fun exists(name: String): Boolean = file(name).exists()

    /** Deletes the file if present. Names are never allowed to escape [dir]. */
    fun delete(name: String) {
        val f = file(name)
        require(f.canonicalFile.parentFile == dir.canonicalFile) { "invalid file name" }
        f.delete()
    }

    /** Duplicates a stored file under a new name, keeping its extension. Returns the new name. */
    fun copy(name: String): String {
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val newName = "${UUID.randomUUID()}$ext"
        file(name).copyTo(file(newName))
        return newName
    }
}
