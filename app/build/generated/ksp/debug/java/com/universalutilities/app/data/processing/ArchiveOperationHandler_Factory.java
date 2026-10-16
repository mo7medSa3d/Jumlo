package com.universalutilities.app.data.processing;

import android.content.Context;
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
public final class ArchiveOperationHandler_Factory implements Factory<ArchiveOperationHandler> {
  private final Provider<Context> appProvider;

  public ArchiveOperationHandler_Factory(Provider<Context> appProvider) {
    this.appProvider = appProvider;
  }

  @Override
  public ArchiveOperationHandler get() {
    return newInstance(appProvider.get());
  }

  public static ArchiveOperationHandler_Factory create(Provider<Context> appProvider) {
    return new ArchiveOperationHandler_Factory(appProvider);
  }

  public static ArchiveOperationHandler newInstance(Context app) {
    return new ArchiveOperationHandler(app);
  }
}
