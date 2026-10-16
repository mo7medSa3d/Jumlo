package com.universalutilities.app.ui.downloads;

import com.universalutilities.app.data.jobs.JobStore;
import com.universalutilities.app.data.media.MediaRuntime;
import com.universalutilities.app.data.source.EphemeralSecretStore;
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
public final class MediaViewModel_Factory implements Factory<MediaViewModel> {
  private final Provider<MediaRuntime> runtimeProvider;

  private final Provider<JobStore> jobsProvider;

  private final Provider<EphemeralSecretStore> secretsProvider;

  public MediaViewModel_Factory(Provider<MediaRuntime> runtimeProvider,
      Provider<JobStore> jobsProvider, Provider<EphemeralSecretStore> secretsProvider) {
    this.runtimeProvider = runtimeProvider;
    this.jobsProvider = jobsProvider;
    this.secretsProvider = secretsProvider;
  }

  @Override
  public MediaViewModel get() {
    return newInstance(runtimeProvider.get(), jobsProvider.get(), secretsProvider.get());
  }

  public static MediaViewModel_Factory create(Provider<MediaRuntime> runtimeProvider,
      Provider<JobStore> jobsProvider, Provider<EphemeralSecretStore> secretsProvider) {
    return new MediaViewModel_Factory(runtimeProvider, jobsProvider, secretsProvider);
  }

  public static MediaViewModel newInstance(MediaRuntime runtime, JobStore jobs,
      EphemeralSecretStore secrets) {
    return new MediaViewModel(runtime, jobs, secrets);
  }
}
