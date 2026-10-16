package com.universalutilities.app.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class EngineOperationWorker_AssistedFactory_Impl implements EngineOperationWorker_AssistedFactory {
  private final EngineOperationWorker_Factory delegateFactory;

  EngineOperationWorker_AssistedFactory_Impl(EngineOperationWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public EngineOperationWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<EngineOperationWorker_AssistedFactory> create(
      EngineOperationWorker_Factory delegateFactory) {
    return InstanceFactory.create(new EngineOperationWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<EngineOperationWorker_AssistedFactory> createFactoryProvider(
      EngineOperationWorker_Factory delegateFactory) {
    return InstanceFactory.create(new EngineOperationWorker_AssistedFactory_Impl(delegateFactory));
  }
}
