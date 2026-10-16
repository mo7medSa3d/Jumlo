package com.universalutilities.app.data.source;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class EphemeralSecretStore_Factory implements Factory<EphemeralSecretStore> {
  @Override
  public EphemeralSecretStore get() {
    return newInstance();
  }

  public static EphemeralSecretStore_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static EphemeralSecretStore newInstance() {
    return new EphemeralSecretStore();
  }

  private static final class InstanceHolder {
    private static final EphemeralSecretStore_Factory INSTANCE = new EphemeralSecretStore_Factory();
  }
}
