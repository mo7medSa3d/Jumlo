package com.universalutilities.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "managed_files")
data class ManagedFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val folder: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val pageCount: Int?,
    val dimensions: String?,
    val itemCount: Int?,
    val modifiedLabelEn: String,
    val modifiedLabelAr: String,
    val isDraft: Boolean,
    val isArchive: Boolean
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val extension: String,
    val domain: String,
    val fullUrl: String,
    val mimeType: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedMbps: Float,
    val etaLabel: String,
    val statusName: String,
    val destinationPath: String
)

@Dao
interface ManagedFileDao {
    @Query("SELECT * FROM managed_files ORDER BY rowid DESC")
    fun observeAllFiles(): Flow<List<ManagedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<ManagedFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: ManagedFileEntity)

    @Query("DELETE FROM managed_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM managed_files WHERE isDraft = 1")
    suspend fun clearDrafts()

    @Query("DELETE FROM managed_files WHERE isDraft = 0")
    suspend fun clearAllOutputs()
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks")
    fun observeAllDownloads(): Flow<List<DownloadTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<DownloadTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [ManagedFileEntity::class, DownloadTaskEntity::class, SourceEntity::class, JobEntity::class, OutputEntity::class, DraftEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun processingDao(): ProcessingDao
    abstract fun managedFileDao(): ManagedFileDao
    abstract fun downloadTaskDao(): DownloadTaskDao
}
