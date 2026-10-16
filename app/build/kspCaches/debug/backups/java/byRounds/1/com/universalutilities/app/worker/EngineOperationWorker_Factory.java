package com.universalutilities.app.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.universalutilities.app.data.jobs.JobStore;
import com.universalutilities.app.data.jobs.OperationDispatcher;
import dagger.internal.DaggerGenerated;
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
public final class EngineOperationWorker_Factory {
  private final Provider<JobStore> jobsProvider;

  private final Provider<OperationDispatcher> dispatcherProvider;

  public EngineOperationWorker_Factory(Provider<JobStore> jobsProvider,
      Provider<OperationDispatcher> dispatcherProvider) {
    this.jobsProvider = jobsProvider;
    this.dispatcherProvider = dispatcherProvider;
  }

  public EngineOperationWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, jobsProvider.get(), dispatcherProvider.get());
  }

  public static EngineOperationWorker_Factory create(Provider<JobStore> jobsProvider,
      Provider<OperationDispatcher> dispatcherProvider) {
    return new EngineOperationWorker_Factory(jobsProvider, dispatcherProvider);
  }

  public static EngineOperationWorker newInstance(Context context, WorkerParameters params,
      JobStore jobs, OperationDispatcher dispatcher) {
    return new EngineOperationWorker(context, params, jobs, dispatcher);
  }
}
