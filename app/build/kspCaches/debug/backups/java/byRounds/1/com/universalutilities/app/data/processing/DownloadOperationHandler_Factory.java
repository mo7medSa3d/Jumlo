package com.universalutilities.app.data.processing;

import android.content.Context;
import com.universalutilities.app.data.source.EphemeralSecretStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class DownloadOperationHandler_Factory implements Factory<DownloadOperationHandler> {
  private final Provider<Context> appProvider;

  private final Provider<OkHttpClient> clientProvider;

  private final Provider<EphemeralSecretStore> secretsProvider;

  public DownloadOperationHandler_Factory(Provider<Context> appProvider,
      Provider<OkHttpClient> clientProvider, Provider<EphemeralSecretStore> secretsProvider) {
    this.appProvider = appProvider;
    this.clientProvider = clientProvider;
    this.secretsProvider = secretsProvider;
  }

  @Override
  public DownloadOperationHandler get() {
    return newInstance(appProvider.get(), clientProvider.get(), secretsProvider.get());
  }

  public static DownloadOperationHandler_Factory create(Provider<Context> appProvider,
      Provider<OkHttpClient> clientProvider, Provider<EphemeralSecretStore> secretsProvider) {
    return new DownloadOperationHandler_Factory(appProvider, clientProvider, secretsProvider);
  }

  public static DownloadOperationHandler newInstance(Context app, OkHttpClient client,
      EphemeralSecretStore secrets) {
    return new DownloadOperationHandler(app, client, secrets);
  }
}
