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
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
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
public final class ManagedFileDao_Impl implements ManagedFileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ManagedFileEntity> __insertionAdapterOfManagedFileEntity;

  private final SharedSQLiteStatement __preparedStmtOfClearDrafts;

  private final SharedSQLiteStatement __preparedStmtOfClearAllOutputs;

  public ManagedFileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfManagedFileEntity = new EntityInsertionAdapter<ManagedFileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `managed_files` (`id`,`name`,`path`,`folder`,`extension`,`mimeType`,`sizeBytes`,`formattedSize`,`pageCount`,`dimensions`,`itemCount`,`modifiedLabelEn`,`modifiedLabelAr`,`isDraft`,`isArchive`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ManagedFileEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getPath());
        statement.bindString(4, entity.getFolder());
        statement.bindString(5, entity.getExtension());
        statement.bindString(6, entity.getMimeType());
        statement.bindLong(7, entity.getSizeBytes());
        statement.bindString(8, entity.getFormattedSize());
        if (entity.getPageCount() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getPageCount());
        }
        if (entity.getDimensions() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getDimensions());
        }
        if (entity.getItemCount() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getItemCount());
        }
        statement.bindString(12, entity.getModifiedLabelEn());
        statement.bindString(13, entity.getModifiedLabelAr());
        final int _tmp = entity.isDraft() ? 1 : 0;
        statement.bindLong(14, _tmp);
        final int _tmp_1 = entity.isArchive() ? 1 : 0;
        statement.bindLong(15, _tmp_1);
      }
    };
    this.__preparedStmtOfClearDrafts = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM managed_files WHERE isDraft = 1";
        return _query;
      }
    };
    this.__preparedStmtOfClearAllOutputs = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM managed_files WHERE isDraft = 0";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<ManagedFileEntity> files,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfManagedFileEntity.insert(files);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insert(final ManagedFileEntity file, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfManagedFileEntity.insert(file);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearDrafts(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearDrafts.acquire();
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
          __preparedStmtOfClearDrafts.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAllOutputs(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAllOutputs.acquire();
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
          __preparedStmtOfClearAllOutputs.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ManagedFileEntity>> observeAllFiles() {
    final String _sql = "SELECT * FROM managed_files ORDER BY rowid DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"managed_files"}, new Callable<List<ManagedFileEntity>>() {
      @Override
      @NonNull
      public List<ManagedFileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfFolder = CursorUtil.getColumnIndexOrThrow(_cursor, "folder");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFormattedSize = CursorUtil.getColumnIndexOrThrow(_cursor, "formattedSize");
          final int _cursorIndexOfPageCount = CursorUtil.getColumnIndexOrThrow(_cursor, "pageCount");
          final int _cursorIndexOfDimensions = CursorUtil.getColumnIndexOrThrow(_cursor, "dimensions");
          final int _cursorIndexOfItemCount = CursorUtil.getColumnIndexOrThrow(_cursor, "itemCount");
          final int _cursorIndexOfModifiedLabelEn = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedLabelEn");
          final int _cursorIndexOfModifiedLabelAr = CursorUtil.getColumnIndexOrThrow(_cursor, "modifiedLabelAr");
          final int _cursorIndexOfIsDraft = CursorUtil.getColumnIndexOrThrow(_cursor, "isDraft");
          final int _cursorIndexOfIsArchive = CursorUtil.getColumnIndexOrThrow(_cursor, "isArchive");
          final List<ManagedFileEntity> _result = new ArrayList<ManagedFileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ManagedFileEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpFolder;
            _tmpFolder = _cursor.getString(_cursorIndexOfFolder);
            final String _tmpExtension;
            _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpFormattedSize;
            _tmpFormattedSize = _cursor.getString(_cursorIndexOfFormattedSize);
            final Integer _tmpPageCount;
            if (_cursor.isNull(_cursorIndexOfPageCount)) {
              _tmpPageCount = null;
            } else {
              _tmpPageCount = _cursor.getInt(_cursorIndexOfPageCount);
            }
            final String _tmpDimensions;
            if (_cursor.isNull(_cursorIndexOfDimensions)) {
              _tmpDimensions = null;
            } else {
              _tmpDimensions = _cursor.getString(_cursorIndexOfDimensions);
            }
            final Integer _tmpItemCount;
            if (_cursor.isNull(_cursorIndexOfItemCount)) {
              _tmpItemCount = null;
            } else {
              _tmpItemCount = _cursor.getInt(_cursorIndexOfItemCount);
            }
            final String _tmpModifiedLabelEn;
            _tmpModifiedLabelEn = _cursor.getString(_cursorIndexOfModifiedLabelEn);
            final String _tmpModifiedLabelAr;
            _tmpModifiedLabelAr = _cursor.getString(_cursorIndexOfModifiedLabelAr);
            final boolean _tmpIsDraft;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDraft);
            _tmpIsDraft = _tmp != 0;
            final boolean _tmpIsArchive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsArchive);
            _tmpIsArchive = _tmp_1 != 0;
            _item = new ManagedFileEntity(_tmpId,_tmpName,_tmpPath,_tmpFolder,_tmpExtension,_tmpMimeType,_tmpSizeBytes,_tmpFormattedSize,_tmpPageCount,_tmpDimensions,_tmpItemCount,_tmpModifiedLabelEn,_tmpModifiedLabelAr,_tmpIsDraft,_tmpIsArchive);
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
  public Object deleteByIds(final List<String> ids, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM managed_files WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : ids) {
          _stmt.bindString(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
