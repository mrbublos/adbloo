package bloo.ad.addbloo.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface UrlDao {

    @Query("SELECT * FROM blocked")
    fun getAll(): LiveData<List<Blocked>>

    @Query("SELECT * FROM blocked WHERE host = :host")
    suspend fun get(host: String): Blocked

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Blocked>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(proxy: Blocked)

    @Update
    suspend fun update(proxy: Blocked)

    @Delete
    suspend fun delete(proxy: Blocked)

    @Query("DELETE FROM blocked")
    suspend fun deleteAll()
}

@Entity(primaryKeys = ["host"])
class Blocked(val host: String, val blocked: Boolean) : Comparable<Blocked> {
    override fun compareTo(other: Blocked): Int {
        return host.compareTo(other.host)
    }
}