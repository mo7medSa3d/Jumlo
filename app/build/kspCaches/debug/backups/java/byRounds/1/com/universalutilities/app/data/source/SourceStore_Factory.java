package com.universalutilities.app.data.source;

import android.content.Context;
import com.universalutilities.app.data.jobs.WorkBudget;
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
public final class SourceStore_Factory implements Factory<SourceStore> {
  private final Provider<Context> contextProvider;

  private final Provider<AppDatabase> dbProvider;

  private final Provider<WorkBudget> budgetProvider;

  public SourceStore_Factory(Provider<Context> contextProvider, Provider<AppDatabase> dbProvider,
      Provider<WorkBudget> budgetProvider) {
    this.contextProvider = contextProvider;
    this.dbProvider = dbProvider;
    this.budgetProvider = budgetProvider;
  }

  @Override
  public SourceStore get() {
    return newInstance(contextProvider.get(), dbProvider.get(), budgetProvider.get());
  }

  public static SourceStore_Factory create(Provider<Context> contextProvider,
      Provider<AppDatabase> dbProvider, Provider<WorkBudget> budgetProvider) {
    return new SourceStore_Factory(contextProvider, dbProvider, budgetProvider);
  }

  public static SourceStore newInstance(Context context, AppDatabase db, WorkBudget budget) {
    return new SourceStore(context, db, budget);
  }
}
