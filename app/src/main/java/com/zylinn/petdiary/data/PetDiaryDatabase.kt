package com.zylinn.petdiary.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetDao
import com.zylinn.petdiary.data.record.Attachment
import com.zylinn.petdiary.data.record.AttachmentDao
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordDao
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.data.record.SupplementDao

@Database(
    entities = [Pet::class, Record::class, Attachment::class, Supplement::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class PetDiaryDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao
    abstract fun recordDao(): RecordDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun supplementDao(): SupplementDao

    companion object {
        const val NAME = "petdiary.db"

        fun build(context: Context): PetDiaryDatabase =
            Room.databaseBuilder(context, PetDiaryDatabase::class.java, NAME).build()
    }
}
