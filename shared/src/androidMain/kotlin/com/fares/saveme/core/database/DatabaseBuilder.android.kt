package com.fares.saveme.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** File name must match the exclusions in androidApp's backup rules (P-09.1). */
fun databaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(appContext, appContext.getDatabasePath(DATABASE_NAME).absolutePath)
}

const val DATABASE_NAME = "saveme.db"
