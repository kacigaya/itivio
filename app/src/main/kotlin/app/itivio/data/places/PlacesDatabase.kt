package app.itivio.data.places

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The list a saved row belongs to. A place can be in several lists, one row each. */
internal enum class PlaceRole { HOME, WORK, FAVORITE, RECENT }

/**
 * A place as the user saved it. The row keeps a full copy of the place, so saved places work
 * without a network request. Kind and modes are stored as names; unknown names read as defaults.
 */
@Entity(tableName = "saved_place", primaryKeys = ["role", "place_key"])
internal data class SavedPlaceEntity(
    val role: PlaceRole,
    @ColumnInfo(name = "place_key") val key: String,
    val id: String,
    val name: String,
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    val area: String?,
    @ColumnInfo(name = "stop_id") val stopId: String?,
    /** Comma-separated transit mode names. */
    val modes: String,
    /** Epoch milliseconds; orders favorites and recents. */
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)

@Dao
internal abstract class SavedPlaceDao {
    @Query("SELECT * FROM saved_place ORDER BY saved_at, rowid")
    abstract fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Upsert
    abstract suspend fun upsert(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_place WHERE role = :role AND place_key = :key")
    abstract suspend fun delete(role: PlaceRole, key: String)

    @Query("DELETE FROM saved_place WHERE role = :role")
    abstract suspend fun deleteAll(role: PlaceRole)

    @Query(
        "DELETE FROM saved_place WHERE role = :role AND place_key NOT IN " +
            "(SELECT place_key FROM saved_place WHERE role = :role ORDER BY saved_at DESC, rowid DESC LIMIT :keep)",
    )
    abstract suspend fun trim(role: PlaceRole, keep: Int)

    /** Home and Work hold one place each. */
    @Transaction
    open suspend fun replace(place: SavedPlaceEntity) {
        deleteAll(place.role)
        upsert(place)
    }

    @Transaction
    open suspend fun upsertAndTrim(place: SavedPlaceEntity, keep: Int) {
        upsert(place)
        trim(place.role, keep)
    }
}

@Database(entities = [SavedPlaceEntity::class], version = 1)
internal abstract class PlacesDatabase : RoomDatabase() {
    abstract fun places(): SavedPlaceDao
}
