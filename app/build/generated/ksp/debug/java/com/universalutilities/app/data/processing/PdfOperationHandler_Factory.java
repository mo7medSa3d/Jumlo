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
public final class PdfOperationHandler_Factory implements Factory<PdfOperationHandler> {
  @Override
  public PdfOperationHandler get() {
    return newInstance();
  }

  public static PdfOperationHandler_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PdfOperationHandler newInstance() {
    return new PdfOperationHandler();
  }

  private static final class InstanceHolder {
    private static final PdfOperationHandler_Factory INSTANCE = new PdfOperationHandler_Factory();
  }
}
