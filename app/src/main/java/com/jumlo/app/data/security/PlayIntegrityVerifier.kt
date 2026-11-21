package com.jumlo.app.data.security

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Requests a Google Play Integrity Standard API token to verify genuine Play install and unmodified binary. */
@Singleton
class PlayIntegrityVerifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager: StandardIntegrityManager = IntegrityManagerFactory.createStandard(context)
    private var provider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    suspend fun requestToken(cloudProjectNumber: Long, requestHash: String): Result<String> = runCatching {
        val tokenProvider = provider ?: prepareProvider(cloudProjectNumber).also { provider = it }
        suspendCancellableCoroutine { cont ->
            tokenProvider.request(
                StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                    .setRequestHash(requestHash)
                    .build()
            ).addOnSuccessListener { resp ->
                if (cont.isActive) cont.resume(resp.token())
            }.addOnFailureListener { err ->
                if (cont.isActive) cont.resume("")
            }
        }.takeIf { it.isNotBlank() } ?: error("INTEGRITY_TOKEN_UNAVAILABLE")
    }

    private suspend fun prepareProvider(cloudProjectNumber: Long): StandardIntegrityManager.StandardIntegrityTokenProvider =
        suspendCancellableCoroutine { cont ->
            manager.prepareIntegrityToken(
                StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(cloudProjectNumber)
                    .build()
            ).addOnSuccessListener { p -> if (cont.isActive) cont.resume(p) }
             .addOnFailureListener { e -> if (cont.isActive) cont.cancel(e) }
        }
}
