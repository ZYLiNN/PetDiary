package com.zylinn.petdiary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetDao

@Database(entities = [Pet::class], version = 1)
abstract class PetDiaryDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao

    companion object {
        const val NAME = "petdiary.db"

        fun build(context: Context): PetDiaryDatabase =
            Room.databaseBuilder(context, PetDiaryDatabase::class.java, NAME).build()
    }
}
