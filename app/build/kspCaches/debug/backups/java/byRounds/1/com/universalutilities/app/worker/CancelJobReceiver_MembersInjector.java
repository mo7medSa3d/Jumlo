package com.universalutilities.app.worker;

import com.universalutilities.app.data.jobs.JobStore;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class CancelJobReceiver_MembersInjector implements MembersInjector<CancelJobReceiver> {
  private final Provider<JobStore> jobsProvider;

  public CancelJobReceiver_MembersInjector(Provider<JobStore> jobsProvider) {
    this.jobsProvider = jobsProvider;
  }

  public static MembersInjector<CancelJobReceiver> create(Provider<JobStore> jobsProvider) {
    return new CancelJobReceiver_MembersInjector(jobsProvider);
  }

  @Override
  public void injectMembers(CancelJobReceiver instance) {
    injectJobs(instance, jobsProvider.get());
  }

  @InjectedFieldSignature("com.universalutilities.app.worker.CancelJobReceiver.jobs")
  public static void injectJobs(CancelJobReceiver instance, JobStore jobs) {
    instance.jobs = jobs;
  }
}
