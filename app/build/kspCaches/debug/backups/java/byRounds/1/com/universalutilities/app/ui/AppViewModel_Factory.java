package com.universalutilities.app.ui;

import android.app.Application;
import com.universalutilities.app.data.files.DocumentTreeStore;
import com.universalutilities.app.data.jobs.DraftStore;
import com.universalutilities.app.data.jobs.JobStore;
import com.universalutilities.app.data.jobs.WorkBudget;
import com.universalutilities.app.data.output.OutputStore;
import com.universalutilities.app.data.preferences.UserPreferencesRepository;
import com.universalutilities.app.data.source.EphemeralSecretStore;
import com.universalutilities.app.data.source.SourceStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AppViewModel_Factory implements Factory<AppViewModel> {
  private final Provider<Application> appProvider;

  private final Provider<UserPreferencesRepository> preferencesProvider;

  private final Provider<SourceStore> sourcesProvider;

  private final Provider<OutputStore> outputStoreProvider;

  private final Provider<JobStore> jobStoreProvider;

  private final Provider<DraftStore> draftsProvider;

  private final Provider<EphemeralSecretStore> secretsProvider;

  private final Provider<DocumentTreeStore> treeStoreProvider;

  private final Provider<WorkBudget> budgetProvider;

  public AppViewModel_Factory(Provider<Application> appProvider,
      Provider<UserPreferencesRepository> preferencesProvider,
      Provider<SourceStore> sourcesProvider, Provider<OutputStore> outputStoreProvider,
      Provider<JobStore> jobStoreProvider, Provider<DraftStore> draftsProvider,
      Provider<EphemeralSecretStore> secretsProvider, Provider<DocumentTreeStore> treeStoreProvider,
      Provider<WorkBudget> budgetProvider) {
    this.appProvider = appProvider;
    this.preferencesProvider = preferencesProvider;
    this.sourcesProvider = sourcesProvider;
    this.outputStoreProvider = outputStoreProvider;
    this.jobStoreProvider = jobStoreProvider;
    this.draftsProvider = draftsProvider;
    this.secretsProvider = secretsProvider;
    this.treeStoreProvider = treeStoreProvider;
    this.budgetProvider = budgetProvider;
  }

  @Override
  public AppViewModel get() {
    return newInstance(appProvider.get(), preferencesProvider.get(), sourcesProvider.get(), outputStoreProvider.get(), jobStoreProvider.get(), draftsProvider.get(), secretsProvider.get(), treeStoreProvider.get(), budgetProvider.get());
  }

  public static AppViewModel_Factory create(Provider<Application> appProvider,
      Provider<UserPreferencesRepository> preferencesProvider,
      Provider<SourceStore> sourcesProvider, Provider<OutputStore> outputStoreProvider,
      Provider<JobStore> jobStoreProvider, Provider<DraftStore> draftsProvider,
      Provider<EphemeralSecretStore> secretsProvider, Provider<DocumentTreeStore> treeStoreProvider,
      Provider<WorkBudget> budgetProvider) {
    return new AppViewModel_Factory(appProvider, preferencesProvider, sourcesProvider, outputStoreProvider, jobStoreProvider, draftsProvider, secretsProvider, treeStoreProvider, budgetProvider);
  }

  public static AppViewModel newInstance(Application app, UserPreferencesRepository preferences,
      SourceStore sources, OutputStore outputStore, JobStore jobStore, DraftStore drafts,
      EphemeralSecretStore secrets, DocumentTreeStore treeStore, WorkBudget budget) {
    return new AppViewModel(app, preferences, sources, outputStore, jobStore, drafts, secrets, treeStore, budget);
  }
}
