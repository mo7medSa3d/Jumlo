package com.universalutilities.app.data.jobs;

import com.universalutilities.app.data.local.AppDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class DraftStore_Factory implements Factory<DraftStore> {
  private final Provider<AppDatabase> dbProvider;

  public DraftStore_Factory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DraftStore get() {
    return newInstance(dbProvider.get());
  }

  public static DraftStore_Factory create(Provider<AppDatabase> dbProvider) {
    return new DraftStore_Factory(dbProvider);
  }

  public static DraftStore newInstance(AppDatabase db) {
    return new DraftStore(db);
  }
}
