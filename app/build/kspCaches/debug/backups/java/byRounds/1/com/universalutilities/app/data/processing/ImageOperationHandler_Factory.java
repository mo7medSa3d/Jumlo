package com.universalutilities.app.data.processing;

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
public final class ImageOperationHandler_Factory implements Factory<ImageOperationHandler> {
  @Override
  public ImageOperationHandler get() {
    return newInstance();
  }

  public static ImageOperationHandler_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ImageOperationHandler newInstance() {
    return new ImageOperationHandler();
  }

  private static final class InstanceHolder {
    private static final ImageOperationHandler_Factory INSTANCE = new ImageOperationHandler_Factory();
  }
}
