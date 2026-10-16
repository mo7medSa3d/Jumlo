package com.universalutilities.app.data.output;

import android.content.Context;
import com.universalutilities.app.data.local.AppDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class OutputStore_Factory implements Factory<OutputStore> {
  private final Provider<Context> contextProvider;

  private final Provider<AppDatabase> dbProvider;

  public OutputStore_Factory(Provider<Context> contextProvider, Provider<AppDatabase> dbProvider) {
    this.contextProvider = contextProvider;
    this.dbProvider = dbProvider;
  }

  @Override
  public OutputStore get() {
    return newInstance(contextProvider.get(), dbProvider.get());
  }

  public static OutputStore_Factory create(Provider<Context> contextProvider,
      Provider<AppDatabase> dbProvider) {
    return new OutputStore_Factory(contextProvider, dbProvider);
  }

  public static OutputStore newInstance(Context context, AppDatabase db) {
    return new OutputStore(context, db);
  }
}
