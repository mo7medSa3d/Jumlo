package com.universalutilities.app;

import androidx.hilt.work.HiltWorkerFactory;
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
public final class UniversalUtilitiesApplication_MembersInjector implements MembersInjector<UniversalUtilitiesApplication> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public UniversalUtilitiesApplication_MembersInjector(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<UniversalUtilitiesApplication> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new UniversalUtilitiesApplication_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(UniversalUtilitiesApplication instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.universalutilities.app.UniversalUtilitiesApplication.workerFactory")
  public static void injectWorkerFactory(UniversalUtilitiesApplication instance,
      HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
