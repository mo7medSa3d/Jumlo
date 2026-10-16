package com.universalutilities.app.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ProcessingDao_Impl implements ProcessingDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SourceEntity> __insertionAdapterOfSourceEntity;

  private final EntityInsertionAdapter<JobEntity> __insertionAdapterOfJobEntity;

  private final EntityInsertionAdapter<OutputEntity> __insertionAdapterOfOutputEntity;

  private final EntityInsertionAdapter<DraftEntity> __insertionAdapterOfDraftEntity;

  private final SharedSQLiteStatement __preparedStmtOfAccessLost;

  private final SharedSQLiteStatement __preparedStmtOfClaim;

  private final SharedSQLiteStatement __preparedStmtOfHeartbeat;

  private final SharedSQLiteStatement __preparedStmtOfUpdateOwned;

  private final SharedSQLiteStatement __preparedStmtOfRelease;

  private final SharedSQLiteStatement __preparedStmtOfSetStage;

  private final SharedSQLiteStatement __preparedStmtOfRetry;

  private final SharedSQLiteStatement __preparedStmtOfFailBeforeClaim;

  public ProcessingDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSourceEntity = new EntityInsertionAdapter<SourceEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `sources` (`id`,`uri`,`displayName`,`mimeType`,`sizeBytes`,`readable`,`writable`,`persistedReadGrant`,`modifiedAtEpochMs`,`cachedPath`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SourceEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getUri());
        statement.bindString(3, entity.getDisplayName());
        if (entity.getMimeType() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getMimeType());
        }
        if (entity.getSizeBytes() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getSizeBytes());
        }
        final int _tmp = entity.getReadable() ? 1 : 0;
        statement.bindLong(6, _tmp);
        final int _tmp_1 = entity.getWritable() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        final int _tmp_2 = entity.getPersistedReadGrant() ? 1 : 0;
        statement.bindLong(8, _tmp_2);
        if (entity.getModifiedAtEpochMs() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getModifiedAtEpochMs());
        }
        if (entity.getCachedPath() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getCachedPath());
        }
      }
    };
    this.__insertionAdapterOfJobEntity = new EntityInsertionAdapter<JobEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `jobs` (`id`,`requestJson`,`snapshotJson`,`stage`,`leaseOwner`,`leaseUntil`,`generation`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final JobEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getRequestJson());
        statement.bindString(3, entity.getSnapshotJson());
        statement.bindString(4, entity.getStage());
        if (entity.getLeaseOwner() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLeaseOwner());
        }
        statement.bindLong(6, entity.getLeaseUntil());
        statement.bindLong(7, entity.getGeneration());
      }
    };
    this.__insertionAdapterOfOutputEntity = new EntityInsertionAdapter<OutputEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `outputs` (`id`,`jobId`,`itemKey`,`recordJson`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OutputEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getJobId());
        statement.bindString(3, entity.getItemKey());
        statement.bindString(4, entity.getRecordJson());
      }
    };
    this.__insertionAdapterOfDraftEntity = new EntityInsertionAdapter<DraftEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `drafts` (`id`,`requestJson`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DraftEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getRequestJson());
      }
    };
    this.__preparedStmtOfAccessLost = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sources SET readable=0 WHERE id=?";
        return _query;
      }
    };
    this.__preparedStmtOfClaim = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE jobs SET leaseOwner=?, leaseUntil=?, generation=generation+1 WHERE id=? AND stage IN ('QUEUED','VALIDATING','RUNNING','FINALIZING','INTERRUPTED') AND (leaseOwner IS NULL OR leaseUntil<?)";
        return _query;
      }
    };
    this.__preparedStmtOfHeartbeat = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE jobs SET leaseUntil=? WHERE id=? AND leaseOwner=? AND stage NOT IN ('CANCELLED','COMPLETED','FAILED','PARTIAL','WAITING_FOR_INPUT')";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateOwned = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE jobs SET snapshotJson=?,stage=? WHERE id=? AND leaseOwner=? AND stage!='CANCELLED'";
        return _query;
      }
    };
    this.__preparedStmtOfRelease = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE jobs SET leaseOwner=NULL,leaseUntil=0 WHERE id=? AND leaseOwner=?";
        return _query;
      }
    };
    this.__preparedStmtOfSetStage = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE jobs SET stage=?,snapshotJson=? WHERE id=? AND stage NOT IN ('COMPLETED','CANCELLED')";
        return _query;
      }
    };
    this.__preparedStmtOfRetry = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE jobs SET stage='QUEUED',snapshotJson=?,leaseOwner=NULL,leaseUntil=0 WHERE id=? AND stage IN ('FAILED','PARTIAL','INTERRUPTED','WAITING_FOR_INPUT') AND leaseOwner IS NULL";
        return _query;
      }
    };
    this.__preparedStmtOfFailBeforeClaim = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE jobs SET stage='FAILED',snapshotJson=? WHERE id=? AND stage IN ('QUEUED','INTERRUPTED') AND leaseOwner IS NULL";
        return _query;
      }
    };
  }

  @Override
  public Object putSource(final SourceEntity source, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSourceEntity.insert(source);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertJob(final JobEntity job, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfJobEntity.insertAndReturnId(job);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertOutput(final OutputEntity output,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfOutputEntity.insertAndReturnId(output);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object saveDraft(final DraftEntity draft, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDraftEntity.insert(draft);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object accessLost(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfAccessLost.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfAccessLost.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object claim(final String id, final String owner, final long now, final long until,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClaim.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, owner);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, until);
        _argIndex = 3;
        _stmt.bindString(_argIndex, id);
        _argIndex = 4;
        _stmt.bindLong(_argIndex, now);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClaim.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object heartbeat(final String id, final String owner, final long until,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfHeartbeat.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, until);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        _argIndex = 3;
        _stmt.bindString(_argIndex, owner);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfHeartbeat.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateOwned(final String id, final String owner, final String snapshot,
      final String stage, final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateOwned.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, snapshot);
        _argIndex = 2;
        _stmt.bindString(_argIndex, stage);
        _argIndex = 3;
        _stmt.bindString(_argIndex, id);
        _argIndex = 4;
        _stmt.bindString(_argIndex, owner);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateOwned.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object release(final String id, final String owner,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRelease.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        _argIndex = 2;
        _stmt.bindString(_argIndex, owner);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRelease.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setStage(final String id, final String stage, final String snapshot,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetStage.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, stage);
        _argIndex = 2;
        _stmt.bindString(_argIndex, snapshot);
        _argIndex = 3;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetStage.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object retry(final String id, final String snapshot,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRetry.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, snapshot);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRetry.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object failBeforeClaim(final String id, final String snapshot,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfFailBeforeClaim.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, snapshot);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfFailBeforeClaim.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object source(final String id, final Continuation<? super SourceEntity> $completion) {
    final String _sql = "SELECT * FROM sources WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SourceEntity>() {
      @Override
      @Nullable
      public SourceEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfReadable = CursorUtil.getColumnIndexOrThrow(_cursor, "readable");
          final int _cursorIndexOfWritable = CursorUtil.getColumnIndexOrThrow(_cursor, "writable");
          final int _cursorIndexOfPersistedReadGrant = CursorUtil.getColumnIndexOrThrow(_cursor, "persistedReadGrant");
          final int _cursorIndexOfModifiedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAtEpochMs");
          final int _cursorIndexOfCachedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "cachedPath");
          final SourceEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final Long _tmpSizeBytes;
            if (_cursor.isNull(_cursorIndexOfSizeBytes)) {
              _tmpSizeBytes = null;
            } else {
              _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            }
            final boolean _tmpReadable;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfReadable);
            _tmpReadable = _tmp != 0;
            final boolean _tmpWritable;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfWritable);
            _tmpWritable = _tmp_1 != 0;
            final boolean _tmpPersistedReadGrant;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfPersistedReadGrant);
            _tmpPersistedReadGrant = _tmp_2 != 0;
            final Long _tmpModifiedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfModifiedAtEpochMs)) {
              _tmpModifiedAtEpochMs = null;
            } else {
              _tmpModifiedAtEpochMs = _cursor.getLong(_cursorIndexOfModifiedAtEpochMs);
            }
            final String _tmpCachedPath;
            if (_cursor.isNull(_cursorIndexOfCachedPath)) {
              _tmpCachedPath = null;
            } else {
              _tmpCachedPath = _cursor.getString(_cursorIndexOfCachedPath);
            }
            _result = new SourceEntity(_tmpId,_tmpUri,_tmpDisplayName,_tmpMimeType,_tmpSizeBytes,_tmpReadable,_tmpWritable,_tmpPersistedReadGrant,_tmpModifiedAtEpochMs,_tmpCachedPath);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object sourceByUri(final String uri,
      final Continuation<? super SourceEntity> $completion) {
    final String _sql = "SELECT * FROM sources WHERE uri=? ORDER BY rowid DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, uri);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SourceEntity>() {
      @Override
      @Nullable
      public SourceEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfReadable = CursorUtil.getColumnIndexOrThrow(_cursor, "readable");
          final int _cursorIndexOfWritable = CursorUtil.getColumnIndexOrThrow(_cursor, "writable");
          final int _cursorIndexOfPersistedReadGrant = CursorUtil.getColumnIndexOrThrow(_cursor, "persistedReadGrant");
          final int _cursorIndexOfModifiedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAtEpochMs");
          final int _cursorIndexOfCachedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "cachedPath");
          final SourceEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final Long _tmpSizeBytes;
            if (_cursor.isNull(_cursorIndexOfSizeBytes)) {
              _tmpSizeBytes = null;
            } else {
              _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            }
            final boolean _tmpReadable;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfReadable);
            _tmpReadable = _tmp != 0;
            final boolean _tmpWritable;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfWritable);
            _tmpWritable = _tmp_1 != 0;
            final boolean _tmpPersistedReadGrant;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfPersistedReadGrant);
            _tmpPersistedReadGrant = _tmp_2 != 0;
            final Long _tmpModifiedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfModifiedAtEpochMs)) {
              _tmpModifiedAtEpochMs = null;
            } else {
              _tmpModifiedAtEpochMs = _cursor.getLong(_cursorIndexOfModifiedAtEpochMs);
            }
            final String _tmpCachedPath;
            if (_cursor.isNull(_cursorIndexOfCachedPath)) {
              _tmpCachedPath = null;
            } else {
              _tmpCachedPath = _cursor.getString(_cursorIndexOfCachedPath);
            }
            _result = new SourceEntity(_tmpId,_tmpUri,_tmpDisplayName,_tmpMimeType,_tmpSizeBytes,_tmpReadable,_tmpWritable,_tmpPersistedReadGrant,_tmpModifiedAtEpochMs,_tmpCachedPath);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SourceEntity>> sources() {
    final String _sql = "SELECT * FROM sources ORDER BY rowid DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sources"}, new Callable<List<SourceEntity>>() {
      @Override
      @NonNull
      public List<SourceEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfReadable = CursorUtil.getColumnIndexOrThrow(_cursor, "readable");
          final int _cursorIndexOfWritable = CursorUtil.getColumnIndexOrThrow(_cursor, "writable");
          final int _cursorIndexOfPersistedReadGrant = CursorUtil.getColumnIndexOrThrow(_cursor, "persistedReadGrant");
          final int _cursorIndexOfModifiedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedAtEpochMs");
          final int _cursorIndexOfCachedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "cachedPath");
          final List<SourceEntity> _result = new ArrayList<SourceEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SourceEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final Long _tmpSizeBytes;
            if (_cursor.isNull(_cursorIndexOfSizeBytes)) {
              _tmpSizeBytes = null;
            } else {
              _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            }
            final boolean _tmpReadable;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfReadable);
            _tmpReadable = _tmp != 0;
            final boolean _tmpWritable;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfWritable);
            _tmpWritable = _tmp_1 != 0;
            final boolean _tmpPersistedReadGrant;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfPersistedReadGrant);
            _tmpPersistedReadGrant = _tmp_2 != 0;
            final Long _tmpModifiedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfModifiedAtEpochMs)) {
              _tmpModifiedAtEpochMs = null;
            } else {
              _tmpModifiedAtEpochMs = _cursor.getLong(_cursorIndexOfModifiedAtEpochMs);
            }
            final String _tmpCachedPath;
            if (_cursor.isNull(_cursorIndexOfCachedPath)) {
              _tmpCachedPath = null;
            } else {
              _tmpCachedPath = _cursor.getString(_cursorIndexOfCachedPath);
            }
            _item = new SourceEntity(_tmpId,_tmpUri,_tmpDisplayName,_tmpMimeType,_tmpSizeBytes,_tmpReadable,_tmpWritable,_tmpPersistedReadGrant,_tmpModifiedAtEpochMs,_tmpCachedPath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object job(final String id, final Continuation<? super JobEntity> $completion) {
    final String _sql = "SELECT * FROM jobs WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<JobEntity>() {
      @Override
      @Nullable
      public JobEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestJson = CursorUtil.getColumnIndexOrThrow(_cursor, "requestJson");
          final int _cursorIndexOfSnapshotJson = CursorUtil.getColumnIndexOrThrow(_cursor, "snapshotJson");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfLeaseOwner = CursorUtil.getColumnIndexOrThrow(_cursor, "leaseOwner");
          final int _cursorIndexOfLeaseUntil = CursorUtil.getColumnIndexOrThrow(_cursor, "leaseUntil");
          final int _cursorIndexOfGeneration = CursorUtil.getColumnIndexOrThrow(_cursor, "generation");
          final JobEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpRequestJson;
            _tmpRequestJson = _cursor.getString(_cursorIndexOfRequestJson);
            final String _tmpSnapshotJson;
            _tmpSnapshotJson = _cursor.getString(_cursorIndexOfSnapshotJson);
            final String _tmpStage;
            _tmpStage = _cursor.getString(_cursorIndexOfStage);
            final String _tmpLeaseOwner;
            if (_cursor.isNull(_cursorIndexOfLeaseOwner)) {
              _tmpLeaseOwner = null;
            } else {
              _tmpLeaseOwner = _cursor.getString(_cursorIndexOfLeaseOwner);
            }
            final long _tmpLeaseUntil;
            _tmpLeaseUntil = _cursor.getLong(_cursorIndexOfLeaseUntil);
            final long _tmpGeneration;
            _tmpGeneration = _cursor.getLong(_cursorIndexOfGeneration);
            _result = new JobEntity(_tmpId,_tmpRequestJson,_tmpSnapshotJson,_tmpStage,_tmpLeaseOwner,_tmpLeaseUntil,_tmpGeneration);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<JobEntity> observeJob(final String id) {
    final String _sql = "SELECT * FROM jobs WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"jobs"}, new Callable<JobEntity>() {
      @Override
      @Nullable
      public JobEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestJson = CursorUtil.getColumnIndexOrThrow(_cursor, "requestJson");
          final int _cursorIndexOfSnapshotJson = CursorUtil.getColumnIndexOrThrow(_cursor, "snapshotJson");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfLeaseOwner = CursorUtil.getColumnIndexOrThrow(_cursor, "leaseOwner");
          final int _cursorIndexOfLeaseUntil = CursorUtil.getColumnIndexOrThrow(_cursor, "leaseUntil");
          final int _cursorIndexOfGeneration = CursorUtil.getColumnIndexOrThrow(_cursor, "generation");
          final JobEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpRequestJson;
            _tmpRequestJson = _cursor.getString(_cursorIndexOfRequestJson);
            final String _tmpSnapshotJson;
            _tmpSnapshotJson = _cursor.getString(_cursorIndexOfSnapshotJson);
            final String _tmpStage;
            _tmpStage = _cursor.getString(_cursorIndexOfStage);
            final String _tmpLeaseOwner;
            if (_cursor.isNull(_cursorIndexOfLeaseOwner)) {
              _tmpLeaseOwner = null;
            } else {
              _tmpLeaseOwner = _cursor.getString(_cursorIndexOfLeaseOwner);
            }
            final long _tmpLeaseUntil;
            _tmpLeaseUntil = _cursor.getLong(_cursorIndexOfLeaseUntil);
            final long _tmpGeneration;
            _tmpGeneration = _cursor.getLong(_cursorIndexOfGeneration);
            _result = new JobEntity(_tmpId,_tmpRequestJson,_tmpSnapshotJson,_tmpStage,_tmpLeaseOwner,_tmpLeaseUntil,_tmpGeneration);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<JobEntity>> jobs() {
    final String _sql = "SELECT * FROM jobs ORDER BY rowid DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"jobs"}, new Callable<List<JobEntity>>() {
      @Override
      @NonNull
      public List<JobEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestJson = CursorUtil.getColumnIndexOrThrow(_cursor, "requestJson");
          final int _cursorIndexOfSnapshotJson = CursorUtil.getColumnIndexOrThrow(_cursor, "snapshotJson");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfLeaseOwner = CursorUtil.getColumnIndexOrThrow(_cursor, "leaseOwner");
          final int _cursorIndexOfLeaseUntil = CursorUtil.getColumnIndexOrThrow(_cursor, "leaseUntil");
          final int _cursorIndexOfGeneration = CursorUtil.getColumnIndexOrThrow(_cursor, "generation");
          final List<JobEntity> _result = new ArrayList<JobEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final JobEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpRequestJson;
            _tmpRequestJson = _cursor.getString(_cursorIndexOfRequestJson);
            final String _tmpSnapshotJson;
            _tmpSnapshotJson = _cursor.getString(_cursorIndexOfSnapshotJson);
            final String _tmpStage;
            _tmpStage = _cursor.getString(_cursorIndexOfStage);
            final String _tmpLeaseOwner;
            if (_cursor.isNull(_cursorIndexOfLeaseOwner)) {
              _tmpLeaseOwner = null;
            } else {
              _tmpLeaseOwner = _cursor.getString(_cursorIndexOfLeaseOwner);
            }
            final long _tmpLeaseUntil;
            _tmpLeaseUntil = _cursor.getLong(_cursorIndexOfLeaseUntil);
            final long _tmpGeneration;
            _tmpGeneration = _cursor.getLong(_cursorIndexOfGeneration);
            _item = new JobEntity(_tmpId,_tmpRequestJson,_tmpSnapshotJson,_tmpStage,_tmpLeaseOwner,_tmpLeaseUntil,_tmpGeneration);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object output(final String job, final String key,
      final Continuation<? super OutputEntity> $completion) {
    final String _sql = "SELECT * FROM outputs WHERE jobId=? AND itemKey=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, job);
    _argIndex = 2;
    _statement.bindString(_argIndex, key);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<OutputEntity>() {
      @Override
      @Nullable
      public OutputEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfJobId = CursorUtil.getColumnIndexOrThrow(_cursor, "jobId");
          final int _cursorIndexOfItemKey = CursorUtil.getColumnIndexOrThrow(_cursor, "itemKey");
          final int _cursorIndexOfRecordJson = CursorUtil.getColumnIndexOrThrow(_cursor, "recordJson");
          final OutputEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpJobId;
            _tmpJobId = _cursor.getString(_cursorIndexOfJobId);
            final String _tmpItemKey;
            _tmpItemKey = _cursor.getString(_cursorIndexOfItemKey);
            final String _tmpRecordJson;
            _tmpRecordJson = _cursor.getString(_cursorIndexOfRecordJson);
            _result = new OutputEntity(_tmpId,_tmpJobId,_tmpItemKey,_tmpRecordJson);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<OutputEntity>> outputs() {
    final String _sql = "SELECT * FROM outputs ORDER BY rowid DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"outputs"}, new Callable<List<OutputEntity>>() {
      @Override
      @NonNull
      public List<OutputEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfJobId = CursorUtil.getColumnIndexOrThrow(_cursor, "jobId");
          final int _cursorIndexOfItemKey = CursorUtil.getColumnIndexOrThrow(_cursor, "itemKey");
          final int _cursorIndexOfRecordJson = CursorUtil.getColumnIndexOrThrow(_cursor, "recordJson");
          final List<OutputEntity> _result = new ArrayList<OutputEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OutputEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpJobId;
            _tmpJobId = _cursor.getString(_cursorIndexOfJobId);
            final String _tmpItemKey;
            _tmpItemKey = _cursor.getString(_cursorIndexOfItemKey);
            final String _tmpRecordJson;
            _tmpRecordJson = _cursor.getString(_cursorIndexOfRecordJson);
            _item = new OutputEntity(_tmpId,_tmpJobId,_tmpItemKey,_tmpRecordJson);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object draft(final String id, final Continuation<? super DraftEntity> $completion) {
    final String _sql = "SELECT * FROM drafts WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DraftEntity>() {
      @Override
      @Nullable
      public DraftEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestJson = CursorUtil.getColumnIndexOrThrow(_cursor, "requestJson");
          final DraftEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpRequestJson;
            _tmpRequestJson = _cursor.getString(_cursorIndexOfRequestJson);
            _result = new DraftEntity(_tmpId,_tmpRequestJson);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
