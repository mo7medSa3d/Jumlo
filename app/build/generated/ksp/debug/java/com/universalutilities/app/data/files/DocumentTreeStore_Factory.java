package com.universalutilities.app.data.files;

import android.content.Context;
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
public final class DocumentTreeStore_Factory implements Factory<DocumentTreeStore> {
  private final Provider<Context> appProvider;

  public DocumentTreeStore_Factory(Provider<Context> appProvider) {
    this.appProvider = appProvider;
  }

  @Override
  public DocumentTreeStore get() {
    return newInstance(appProvider.get());
  }

  public static DocumentTreeStore_Factory create(Provider<Context> appProvider) {
    return new DocumentTreeStore_Factory(appProvider);
  }

  public static DocumentTreeStore newInstance(Context app) {
    return new DocumentTreeStore(app);
  }
}
