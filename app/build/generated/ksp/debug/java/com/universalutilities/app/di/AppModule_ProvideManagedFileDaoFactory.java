package com.universalutilities.app.di;

import com.universalutilities.app.data.local.AppDatabase;
import com.universalutilities.app.data.local.ManagedFileDao;
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
public final class AppModule_ProvideManagedFileDaoFactory implements Factory<ManagedFileDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideManagedFileDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ManagedFileDao get() {
    return provideManagedFileDao(dbProvider.get());
  }

  public static AppModule_ProvideManagedFileDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideManagedFileDaoFactory(dbProvider);
  }

  public static ManagedFileDao provideManagedFileDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideManagedFileDao(db));
  }
}
