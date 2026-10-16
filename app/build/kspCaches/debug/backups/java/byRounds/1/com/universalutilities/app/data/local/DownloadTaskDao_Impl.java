package com.universalutilities.app.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
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
public final class DownloadTaskDao_Impl implements DownloadTaskDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DownloadTaskEntity> __insertionAdapterOfDownloadTaskEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public DownloadTaskDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDownloadTaskEntity = new EntityInsertionAdapter<DownloadTaskEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `download_tasks` (`id`,`fileName`,`extension`,`domain`,`fullUrl`,`mimeType`,`downloadedBytes`,`totalBytes`,`speedMbps`,`etaLabel`,`statusName`,`destinationPath`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DownloadTaskEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getFileName());
        statement.bindString(3, entity.getExtension());
        statement.bindString(4, entity.getDomain());
        statement.bindString(5, entity.getFullUrl());
        statement.bindString(6, entity.getMimeType());
        statement.bindLong(7, entity.getDownloadedBytes());
        statement.bindLong(8, entity.getTotalBytes());
        statement.bindDouble(9, entity.getSpeedMbps());
        statement.bindString(10, entity.getEtaLabel());
        statement.bindString(11, entity.getStatusName());
        statement.bindString(12, entity.getDestinationPath());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM download_tasks WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<DownloadTaskEntity> tasks,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDownloadTaskEntity.insert(tasks);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insert(final DownloadTaskEntity task,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDownloadTaskEntity.insert(task);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DownloadTaskEntity>> observeAllDownloads() {
    final String _sql = "SELECT * FROM download_tasks";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"download_tasks"}, new Callable<List<DownloadTaskEntity>>() {
      @Override
      @NonNull
      public List<DownloadTaskEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfDomain = CursorUtil.getColumnIndexOrThrow(_cursor, "domain");
          final int _cursorIndexOfFullUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "fullUrl");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfDownloadedBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedBytes");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfSpeedMbps = CursorUtil.getColumnIndexOrThrow(_cursor, "speedMbps");
          final int _cursorIndexOfEtaLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "etaLabel");
          final int _cursorIndexOfStatusName = CursorUtil.getColumnIndexOrThrow(_cursor, "statusName");
          final int _cursorIndexOfDestinationPath = CursorUtil.getColumnIndexOrThrow(_cursor, "destinationPath");
          final List<DownloadTaskEntity> _result = new ArrayList<DownloadTaskEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadTaskEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            final String _tmpDomain;
            _tmpDomain = _cursor.getString(_cursorIndexOfDomain);
            final String _tmpFullUrl;
            _tmpFullUrl = _cursor.getString(_cursorIndexOfFullUrl);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpDownloadedBytes;
            _tmpDownloadedBytes = _cursor.getLong(_cursorIndexOfDownloadedBytes);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final float _tmpSpeedMbps;
            _tmpSpeedMbps = _cursor.getFloat(_cursorIndexOfSpeedMbps);
            final String _tmpEtaLabel;
            _tmpEtaLabel = _cursor.getString(_cursorIndexOfEtaLabel);
            final String _tmpStatusName;
            _tmpStatusName = _cursor.getString(_cursorIndexOfStatusName);
            final String _tmpDestinationPath;
            _tmpDestinationPath = _cursor.getString(_cursorIndexOfDestinationPath);
            _item = new DownloadTaskEntity(_tmpId,_tmpFileName,_tmpExtension,_tmpDomain,_tmpFullUrl,_tmpMimeType,_tmpDownloadedBytes,_tmpTotalBytes,_tmpSpeedMbps,_tmpEtaLabel,_tmpStatusName,_tmpDestinationPath);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
