package com.universalutilities.app.di;

import android.content.Context;
import com.universalutilities.app.data.media.MediaRuntime;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideMediaRuntimeFactory implements Factory<MediaRuntime> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideMediaRuntimeFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MediaRuntime get() {
    return provideMediaRuntime(contextProvider.get());
  }

  public static AppModule_ProvideMediaRuntimeFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideMediaRuntimeFactory(contextProvider);
  }

  public static MediaRuntime provideMediaRuntime(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideMediaRuntime(context));
  }
}
