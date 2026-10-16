package com.jumlo.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="sources",indices=[Index(value=["uri"])])
data class SourceEntity(@PrimaryKey val id:String,val uri:String,val displayName:String,val mimeType:String?,val sizeBytes:Long?,val readable:Boolean,val writable:Boolean,val persistedReadGrant:Boolean,val modifiedAtEpochMs:Long?,val cachedPath:String?)
@Entity(tableName="jobs")
data class JobEntity(@PrimaryKey val id:String,val requestJson:String,val snapshotJson:String,val stage:String,val leaseOwner:String?=null,val leaseUntil:Long=0,val generation:Long=0)
@Entity(tableName="outputs",indices=[Index(value=["jobId","itemKey"],unique=true)])
data class OutputEntity(@PrimaryKey val id:String,val jobId:String,val itemKey:String,val recordJson:String)
@Entity(tableName="drafts")
data class DraftEntity(@PrimaryKey val id:String,val requestJson:String)

@Dao interface ProcessingDao {
    @Query("SELECT * FROM sources WHERE id=:id") suspend fun source(id:String):SourceEntity?
    @Query("SELECT * FROM sources WHERE uri=:uri ORDER BY rowid DESC LIMIT 1") suspend fun sourceByUri(uri:String):SourceEntity?
    @Query("SELECT * FROM sources ORDER BY rowid DESC") fun sources():Flow<List<SourceEntity>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putSource(source:SourceEntity)
    @Query("UPDATE sources SET readable=0 WHERE id=:id") suspend fun accessLost(id:String)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertJob(job:JobEntity):Long
    @Query("SELECT * FROM jobs WHERE id=:id") suspend fun job(id:String):JobEntity?
    @Query("SELECT * FROM jobs WHERE id=:id") fun observeJob(id:String):Flow<JobEntity?>
    @Query("SELECT * FROM jobs ORDER BY rowid DESC") fun jobs():Flow<List<JobEntity>>
    @Query("UPDATE jobs SET leaseOwner=:owner, leaseUntil=:until, generation=generation+1 WHERE id=:id AND stage IN ('QUEUED','VALIDATING','RUNNING','FINALIZING','INTERRUPTED') AND (leaseOwner IS NULL OR leaseUntil<:now)")
    suspend fun claim(id:String,owner:String,now:Long,until:Long):Int
    @Query("UPDATE jobs SET leaseUntil=:until WHERE id=:id AND leaseOwner=:owner AND stage NOT IN ('CANCELLED','COMPLETED','FAILED','PARTIAL','WAITING_FOR_INPUT')") suspend fun heartbeat(id:String,owner:String,until:Long):Int
    @Query("UPDATE jobs SET snapshotJson=:snapshot,stage=:stage WHERE id=:id AND leaseOwner=:owner AND stage!='CANCELLED'") suspend fun updateOwned(id:String,owner:String,snapshot:String,stage:String):Int
    @Query("UPDATE jobs SET leaseOwner=NULL,leaseUntil=0 WHERE id=:id AND leaseOwner=:owner") suspend fun release(id:String,owner:String)
    @Query("UPDATE jobs SET stage=:stage,snapshotJson=:snapshot WHERE id=:id AND stage NOT IN ('COMPLETED','CANCELLED')") suspend fun setStage(id:String,stage:String,snapshot:String)
    @Query("UPDATE jobs SET stage='QUEUED',snapshotJson=:snapshot,leaseOwner=NULL,leaseUntil=0 WHERE id=:id AND stage IN ('FAILED','PARTIAL','INTERRUPTED','WAITING_FOR_INPUT') AND leaseOwner IS NULL") suspend fun retry(id:String,snapshot:String):Int
    @Query("UPDATE jobs SET stage='FAILED',snapshotJson=:snapshot WHERE id=:id AND stage IN ('QUEUED','INTERRUPTED') AND leaseOwner IS NULL")
    suspend fun failBeforeClaim(id:String,snapshot:String):Int
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertOutput(output:OutputEntity):Long
    @Query("SELECT * FROM outputs WHERE jobId=:job AND itemKey=:key") suspend fun output(job:String,key:String):OutputEntity?
    @Query("SELECT * FROM outputs ORDER BY rowid DESC") fun outputs():Flow<List<OutputEntity>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveDraft(draft:DraftEntity)
    @Query("SELECT * FROM drafts WHERE id=:id") suspend fun draft(id:String):DraftEntity?
}
