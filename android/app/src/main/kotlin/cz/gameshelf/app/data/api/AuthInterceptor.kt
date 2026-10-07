package cz.gameshelf.app.data.api

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal const val AUTHORIZATION = "Authorization"
private const val BEARER_PREFIX = "Bearer "

internal fun Request.bearerToken(): String? = header(AUTHORIZATION)?.removePrefix(BEARER_PREFIX)

internal fun Request.withBearer(token: String): Request =
    newBuilder().header(AUTHORIZATION, BEARER_PREFIX + token).build()

/** Adds `Authorization: Bearer <access token>` to every request while a session exists. */
class AuthInterceptor(private val accessToken: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = accessToken()
        return if (token == null || request.header(AUTHORIZATION) != null) {
            chain.proceed(request)
        } else {
            chain.proceed(request.withBearer(token))
        }
    }
}
