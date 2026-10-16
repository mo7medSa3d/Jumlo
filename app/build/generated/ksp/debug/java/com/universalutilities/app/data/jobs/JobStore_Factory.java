package com.universalutilities.app.data.jobs;

import android.content.Context;
import com.universalutilities.app.data.local.AppDatabase;
import com.universalutilities.app.data.output.OutputStore;
import com.universalutilities.app.data.source.SourceStore;
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
public final class JobStore_Factory implements Factory<JobStore> {
  private final Provider<Context> appProvider;

  private final Provider<AppDatabase> dbProvider;

  private final Provider<SourceStore> sourcesProvider;

  private final Provider<OutputStore> outputsProvider;

  private final Provider<WorkBudget> budgetProvider;

  public JobStore_Factory(Provider<Context> appProvider, Provider<AppDatabase> dbProvider,
      Provider<SourceStore> sourcesProvider, Provider<OutputStore> outputsProvider,
      Provider<WorkBudget> budgetProvider) {
    this.appProvider = appProvider;
    this.dbProvider = dbProvider;
    this.sourcesProvider = sourcesProvider;
    this.outputsProvider = outputsProvider;
    this.budgetProvider = budgetProvider;
  }

  @Override
  public JobStore get() {
    return newInstance(appProvider.get(), dbProvider.get(), sourcesProvider.get(), outputsProvider.get(), budgetProvider.get());
  }

  public static JobStore_Factory create(Provider<Context> appProvider,
      Provider<AppDatabase> dbProvider, Provider<SourceStore> sourcesProvider,
      Provider<OutputStore> outputsProvider, Provider<WorkBudget> budgetProvider) {
    return new JobStore_Factory(appProvider, dbProvider, sourcesProvider, outputsProvider, budgetProvider);
  }

  public static JobStore newInstance(Context app, AppDatabase db, SourceStore sources,
      OutputStore outputs, WorkBudget budget) {
    return new JobStore(app, db, sources, outputs, budget);
  }
}
