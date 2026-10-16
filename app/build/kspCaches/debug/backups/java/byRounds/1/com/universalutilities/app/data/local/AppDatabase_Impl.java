package com.universalutilities.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile ProcessingDao _processingDao;

  private volatile ManagedFileDao _managedFileDao;

  private volatile DownloadTaskDao _downloadTaskDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `managed_files` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `path` TEXT NOT NULL, `folder` TEXT NOT NULL, `extension` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `formattedSize` TEXT NOT NULL, `pageCount` INTEGER, `dimensions` TEXT, `itemCount` INTEGER, `modifiedLabelEn` TEXT NOT NULL, `modifiedLabelAr` TEXT NOT NULL, `isDraft` INTEGER NOT NULL, `isArchive` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `download_tasks` (`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `extension` TEXT NOT NULL, `domain` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `speedMbps` REAL NOT NULL, `etaLabel` TEXT NOT NULL, `statusName` TEXT NOT NULL, `destinationPath` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `sources` (`id` TEXT NOT NULL, `uri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT, `sizeBytes` INTEGER, `readable` INTEGER NOT NULL, `writable` INTEGER NOT NULL, `persistedReadGrant` INTEGER NOT NULL, `modifiedAtEpochMs` INTEGER, `cachedPath` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sources_uri` ON `sources` (`uri`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `jobs` (`id` TEXT NOT NULL, `requestJson` TEXT NOT NULL, `snapshotJson` TEXT NOT NULL, `stage` TEXT NOT NULL, `leaseOwner` TEXT, `leaseUntil` INTEGER NOT NULL, `generation` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `outputs` (`id` TEXT NOT NULL, `jobId` TEXT NOT NULL, `itemKey` TEXT NOT NULL, `recordJson` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outputs_jobId_itemKey` ON `outputs` (`jobId`, `itemKey`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `drafts` (`id` TEXT NOT NULL, `requestJson` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1c7241abd5b8ab41f5c26f14eef8d64f')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `managed_files`");
        db.execSQL("DROP TABLE IF EXISTS `download_tasks`");
        db.execSQL("DROP TABLE IF EXISTS `sources`");
        db.execSQL("DROP TABLE IF EXISTS `jobs`");
        db.execSQL("DROP TABLE IF EXISTS `outputs`");
        db.execSQL("DROP TABLE IF EXISTS `drafts`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsManagedFiles = new HashMap<String, TableInfo.Column>(15);
        _columnsManagedFiles.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("path", new TableInfo.Column("path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("folder", new TableInfo.Column("folder", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("extension", new TableInfo.Column("extension", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("mimeType", new TableInfo.Column("mimeType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("formattedSize", new TableInfo.Column("formattedSize", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("pageCount", new TableInfo.Column("pageCount", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("dimensions", new TableInfo.Column("dimensions", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("itemCount", new TableInfo.Column("itemCount", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("modifiedLabelEn", new TableInfo.Column("modifiedLabelEn", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("modifiedLabelAr", new TableInfo.Column("modifiedLabelAr", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("isDraft", new TableInfo.Column("isDraft", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsManagedFiles.put("isArchive", new TableInfo.Column("isArchive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysManagedFiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesManagedFiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoManagedFiles = new TableInfo("managed_files", _columnsManagedFiles, _foreignKeysManagedFiles, _indicesManagedFiles);
        final TableInfo _existingManagedFiles = TableInfo.read(db, "managed_files");
        if (!_infoManagedFiles.equals(_existingManagedFiles)) {
          return new RoomOpenHelper.ValidationResult(false, "managed_files(com.universalutilities.app.data.local.ManagedFileEntity).\n"
                  + " Expected:\n" + _infoManagedFiles + "\n"
                  + " Found:\n" + _existingManagedFiles);
        }
        final HashMap<String, TableInfo.Column> _columnsDownloadTasks = new HashMap<String, TableInfo.Column>(12);
        _columnsDownloadTasks.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("fileName", new TableInfo.Column("fileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("extension", new TableInfo.Column("extension", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("domain", new TableInfo.Column("domain", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("fullUrl", new TableInfo.Column("fullUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("mimeType", new TableInfo.Column("mimeType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("downloadedBytes", new TableInfo.Column("downloadedBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("totalBytes", new TableInfo.Column("totalBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("speedMbps", new TableInfo.Column("speedMbps", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("etaLabel", new TableInfo.Column("etaLabel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("statusName", new TableInfo.Column("statusName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadTasks.put("destinationPath", new TableInfo.Column("destinationPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDownloadTasks = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDownloadTasks = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDownloadTasks = new TableInfo("download_tasks", _columnsDownloadTasks, _foreignKeysDownloadTasks, _indicesDownloadTasks);
        final TableInfo _existingDownloadTasks = TableInfo.read(db, "download_tasks");
        if (!_infoDownloadTasks.equals(_existingDownloadTasks)) {
          return new RoomOpenHelper.ValidationResult(false, "download_tasks(com.universalutilities.app.data.local.DownloadTaskEntity).\n"
                  + " Expected:\n" + _infoDownloadTasks + "\n"
                  + " Found:\n" + _existingDownloadTasks);
        }
        final HashMap<String, TableInfo.Column> _columnsSources = new HashMap<String, TableInfo.Column>(10);
        _columnsSources.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("uri", new TableInfo.Column("uri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("mimeType", new TableInfo.Column("mimeType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("readable", new TableInfo.Column("readable", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("writable", new TableInfo.Column("writable", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("persistedReadGrant", new TableInfo.Column("persistedReadGrant", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("modifiedAtEpochMs", new TableInfo.Column("modifiedAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSources.put("cachedPath", new TableInfo.Column("cachedPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSources = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSources = new HashSet<TableInfo.Index>(1);
        _indicesSources.add(new TableInfo.Index("index_sources_uri", false, Arrays.asList("uri"), Arrays.asList("ASC")));
        final TableInfo _infoSources = new TableInfo("sources", _columnsSources, _foreignKeysSources, _indicesSources);
        final TableInfo _existingSources = TableInfo.read(db, "sources");
        if (!_infoSources.equals(_existingSources)) {
          return new RoomOpenHelper.ValidationResult(false, "sources(com.universalutilities.app.data.local.SourceEntity).\n"
                  + " Expected:\n" + _infoSources + "\n"
                  + " Found:\n" + _existingSources);
        }
        final HashMap<String, TableInfo.Column> _columnsJobs = new HashMap<String, TableInfo.Column>(7);
        _columnsJobs.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("requestJson", new TableInfo.Column("requestJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("snapshotJson", new TableInfo.Column("snapshotJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("stage", new TableInfo.Column("stage", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("leaseOwner", new TableInfo.Column("leaseOwner", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("leaseUntil", new TableInfo.Column("leaseUntil", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("generation", new TableInfo.Column("generation", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysJobs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesJobs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoJobs = new TableInfo("jobs", _columnsJobs, _foreignKeysJobs, _indicesJobs);
        final TableInfo _existingJobs = TableInfo.read(db, "jobs");
        if (!_infoJobs.equals(_existingJobs)) {
          return new RoomOpenHelper.ValidationResult(false, "jobs(com.universalutilities.app.data.local.JobEntity).\n"
                  + " Expected:\n" + _infoJobs + "\n"
                  + " Found:\n" + _existingJobs);
        }
        final HashMap<String, TableInfo.Column> _columnsOutputs = new HashMap<String, TableInfo.Column>(4);
        _columnsOutputs.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutputs.put("jobId", new TableInfo.Column("jobId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutputs.put("itemKey", new TableInfo.Column("itemKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutputs.put("recordJson", new TableInfo.Column("recordJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysOutputs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesOutputs = new HashSet<TableInfo.Index>(1);
        _indicesOutputs.add(new TableInfo.Index("index_outputs_jobId_itemKey", true, Arrays.asList("jobId", "itemKey"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoOutputs = new TableInfo("outputs", _columnsOutputs, _foreignKeysOutputs, _indicesOutputs);
        final TableInfo _existingOutputs = TableInfo.read(db, "outputs");
        if (!_infoOutputs.equals(_existingOutputs)) {
          return new RoomOpenHelper.ValidationResult(false, "outputs(com.universalutilities.app.data.local.OutputEntity).\n"
                  + " Expected:\n" + _infoOutputs + "\n"
                  + " Found:\n" + _existingOutputs);
        }
        final HashMap<String, TableInfo.Column> _columnsDrafts = new HashMap<String, TableInfo.Column>(2);
        _columnsDrafts.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDrafts.put("requestJson", new TableInfo.Column("requestJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDrafts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDrafts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDrafts = new TableInfo("drafts", _columnsDrafts, _foreignKeysDrafts, _indicesDrafts);
        final TableInfo _existingDrafts = TableInfo.read(db, "drafts");
        if (!_infoDrafts.equals(_existingDrafts)) {
          return new RoomOpenHelper.ValidationResult(false, "drafts(com.universalutilities.app.data.local.DraftEntity).\n"
                  + " Expected:\n" + _infoDrafts + "\n"
                  + " Found:\n" + _existingDrafts);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "1c7241abd5b8ab41f5c26f14eef8d64f", "e1de08326eb970186e920e6d1019fe1c");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "managed_files","download_tasks","sources","jobs","outputs","drafts");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `managed_files`");
      _db.execSQL("DELETE FROM `download_tasks`");
      _db.execSQL("DELETE FROM `sources`");
      _db.execSQL("DELETE FROM `jobs`");
      _db.execSQL("DELETE FROM `outputs`");
      _db.execSQL("DELETE FROM `drafts`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ProcessingDao.class, ProcessingDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ManagedFileDao.class, ManagedFileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(DownloadTaskDao.class, DownloadTaskDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ProcessingDao processingDao() {
    if (_processingDao != null) {
      return _processingDao;
    } else {
      synchronized(this) {
        if(_processingDao == null) {
          _processingDao = new ProcessingDao_Impl(this);
        }
        return _processingDao;
      }
    }
  }

  @Override
  public ManagedFileDao managedFileDao() {
    if (_managedFileDao != null) {
      return _managedFileDao;
    } else {
      synchronized(this) {
        if(_managedFileDao == null) {
          _managedFileDao = new ManagedFileDao_Impl(this);
        }
        return _managedFileDao;
      }
    }
  }

  @Override
  public DownloadTaskDao downloadTaskDao() {
    if (_downloadTaskDao != null) {
      return _downloadTaskDao;
    } else {
      synchronized(this) {
        if(_downloadTaskDao == null) {
          _downloadTaskDao = new DownloadTaskDao_Impl(this);
        }
        return _downloadTaskDao;
      }
    }
  }
}
