package bloo.ad.addbloo.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Blocked::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    companion object {
        @Volatile
        private var instance: Db? = null

        fun instance(context: Context): Db {
            val i = instance
            if (i != null) { return i }

            return synchronized(this) {
                val i2 = instance
                if (i2 != null) {
                    i2
                } else {
                    val newInstance = Room.databaseBuilder(context.applicationContext, Db::class.java, "bloo.ad.adbloo.db").build()
                    instance = newInstance
                    newInstance
                }
            }
        }
    }

    abstract fun blockedUrlsDao(): UrlDao
}