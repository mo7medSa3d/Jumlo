package com.universalutilities.app.di;

import com.universalutilities.app.data.jobs.WorkBudget;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideWorkBudgetFactory implements Factory<WorkBudget> {
  @Override
  public WorkBudget get() {
    return provideWorkBudget();
  }

  public static AppModule_ProvideWorkBudgetFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static WorkBudget provideWorkBudget() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideWorkBudget());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideWorkBudgetFactory INSTANCE = new AppModule_ProvideWorkBudgetFactory();
  }
}
