package com.universalutilities.app.data.jobs;

import com.universalutilities.app.data.media.MediaOperationHandler;
import com.universalutilities.app.data.processing.ArchiveOperationHandler;
import com.universalutilities.app.data.processing.DownloadOperationHandler;
import com.universalutilities.app.data.processing.ImageOperationHandler;
import com.universalutilities.app.data.processing.PdfOperationHandler;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class OperationDispatcher_Factory implements Factory<OperationDispatcher> {
  private final Provider<ImageOperationHandler> imagesProvider;

  private final Provider<PdfOperationHandler> pdfProvider;

  private final Provider<ArchiveOperationHandler> archivesProvider;

  private final Provider<DownloadOperationHandler> downloadsProvider;

  private final Provider<MediaOperationHandler> mediaProvider;

  public OperationDispatcher_Factory(Provider<ImageOperationHandler> imagesProvider,
      Provider<PdfOperationHandler> pdfProvider, Provider<ArchiveOperationHandler> archivesProvider,
      Provider<DownloadOperationHandler> downloadsProvider,
      Provider<MediaOperationHandler> mediaProvider) {
    this.imagesProvider = imagesProvider;
    this.pdfProvider = pdfProvider;
    this.archivesProvider = archivesProvider;
    this.downloadsProvider = downloadsProvider;
    this.mediaProvider = mediaProvider;
  }

  @Override
  public OperationDispatcher get() {
    return newInstance(imagesProvider.get(), pdfProvider.get(), archivesProvider.get(), downloadsProvider.get(), mediaProvider.get());
  }

  public static OperationDispatcher_Factory create(Provider<ImageOperationHandler> imagesProvider,
      Provider<PdfOperationHandler> pdfProvider, Provider<ArchiveOperationHandler> archivesProvider,
      Provider<DownloadOperationHandler> downloadsProvider,
      Provider<MediaOperationHandler> mediaProvider) {
    return new OperationDispatcher_Factory(imagesProvider, pdfProvider, archivesProvider, downloadsProvider, mediaProvider);
  }

  public static OperationDispatcher newInstance(ImageOperationHandler images,
      PdfOperationHandler pdf, ArchiveOperationHandler archives, DownloadOperationHandler downloads,
      MediaOperationHandler media) {
    return new OperationDispatcher(images, pdf, archives, downloads, media);
  }
}
