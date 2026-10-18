package com.zylinn.petdiary.data.photo

import java.io.File

/** App-private photo files, referenced from the database by file name only. */
class PhotoStore(private val dir: File) {

    fun file(name: String): File = File(dir, name)

    fun exists(name: String): Boolean = file(name).exists()

    /** Deletes the file if present. Names are never allowed to escape [dir]. */
    fun delete(name: String) {
        val f = file(name)
        require(f.canonicalFile.parentFile == dir.canonicalFile) { "invalid photo name" }
        f.delete()
    }
}
