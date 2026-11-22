package com.jumlo.app.data.security

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Requests a Google Play Integrity Standard API token to verify genuine Play install and unmodified binary. */
@Singleton
class PlayIntegrityVerifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager: StandardIntegrityManager = IntegrityManagerFactory.createStandard(context)
    @Volatile private var provider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    suspend fun requestToken(cloudProjectNumber: Long, requestHash: String): Result<String> = try {
        val tokenProvider = provider ?: prepareProvider(cloudProjectNumber).also { provider = it }
        val token = suspendCancellableCoroutine { cont ->
            tokenProvider.request(
                StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                    .setRequestHash(requestHash)
                    .build()
            ).addOnSuccessListener { resp ->
                if (cont.isActive) cont.resume(resp.token())
            }.addOnFailureListener { err ->
                if (cont.isActive) cont.resumeWithException(err)
            }
        }.takeIf { it.isNotBlank() } ?: error("INTEGRITY_TOKEN_UNAVAILABLE")
        Result.success(token)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun prepareProvider(cloudProjectNumber: Long): StandardIntegrityManager.StandardIntegrityTokenProvider =
        suspendCancellableCoroutine { cont ->
            manager.prepareIntegrityToken(
                StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(cloudProjectNumber)
                    .build()
            ).addOnSuccessListener { p -> if (cont.isActive) cont.resume(p) }
             .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
}
