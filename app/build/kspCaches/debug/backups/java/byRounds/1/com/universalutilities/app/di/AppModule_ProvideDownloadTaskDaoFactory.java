package com.universalutilities.app.di;

import com.universalutilities.app.data.local.AppDatabase;
import com.universalutilities.app.data.local.DownloadTaskDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AppModule_ProvideDownloadTaskDaoFactory implements Factory<DownloadTaskDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideDownloadTaskDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DownloadTaskDao get() {
    return provideDownloadTaskDao(dbProvider.get());
  }

  public static AppModule_ProvideDownloadTaskDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideDownloadTaskDaoFactory(dbProvider);
  }

  public static DownloadTaskDao provideDownloadTaskDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDownloadTaskDao(db));
  }
}
