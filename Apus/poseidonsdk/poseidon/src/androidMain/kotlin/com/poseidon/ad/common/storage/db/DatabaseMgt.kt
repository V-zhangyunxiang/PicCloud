package com.poseidon.ad.common.storage.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** Builds Room with query and transaction execution owned by the Room policy lane. */
class DriverFactory(private val context: Context) {
    internal fun createDatabase(): PoseidonRoomDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            PoseidonRoomDatabase::class.java,
            PoseidonRoomDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryExecutor(RoomPolicyDispatcher.executor)
            .setTransactionExecutor(RoomPolicyDispatcher.executor)
            .build()
    }
}

internal fun createDatabase(driverFactory: DriverFactory): PoseidonRoomDatabase {
    return driverFactory.createDatabase()
}
