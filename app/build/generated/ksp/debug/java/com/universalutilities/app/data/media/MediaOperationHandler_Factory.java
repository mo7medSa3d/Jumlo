package com.universalutilities.app.data.media;

import android.content.Context;
import com.universalutilities.app.data.source.EphemeralSecretStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class MediaOperationHandler_Factory implements Factory<MediaOperationHandler> {
  private final Provider<Context> appProvider;

  private final Provider<MediaRuntime> runtimeProvider;

  private final Provider<EphemeralSecretStore> secretsProvider;

  public MediaOperationHandler_Factory(Provider<Context> appProvider,
      Provider<MediaRuntime> runtimeProvider, Provider<EphemeralSecretStore> secretsProvider) {
    this.appProvider = appProvider;
    this.runtimeProvider = runtimeProvider;
    this.secretsProvider = secretsProvider;
  }

  @Override
  public MediaOperationHandler get() {
    return newInstance(appProvider.get(), runtimeProvider.get(), secretsProvider.get());
  }

  public static MediaOperationHandler_Factory create(Provider<Context> appProvider,
      Provider<MediaRuntime> runtimeProvider, Provider<EphemeralSecretStore> secretsProvider) {
    return new MediaOperationHandler_Factory(appProvider, runtimeProvider, secretsProvider);
  }

  public static MediaOperationHandler newInstance(Context app, MediaRuntime runtime,
      EphemeralSecretStore secrets) {
    return new MediaOperationHandler(app, runtime, secrets);
  }
}
