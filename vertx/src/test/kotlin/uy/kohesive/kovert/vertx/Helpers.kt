package uy.kohesive.kovert.vertx.test

import io.vertx.core.MultiMap
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import uy.klutter.core.common.mustStartWith
import uy.klutter.core.uri.UrlEncoding
import kotlin.test.assertEquals

data class HttpClientResult(val statusCode: Int, val statusMessage: String, val body: String?, val headers: MultiMap)

fun HttpClientRequest.promise(): Promise<HttpClientResult, Throwable> {
    val deferred = deferred<HttpClientResult, Throwable>()
    return promise({}, deferred)
}

fun HttpClientRequest.promise(init: HttpClientRequest.() -> Unit): Promise<HttpClientResult, Throwable> {
    val deferred = deferred<HttpClientResult, Throwable>()
    return promise(init, deferred)
}

fun HttpClientRequest.promise(
    init: HttpClientRequest.() -> Unit,
    deferred: Deferred<HttpClientResult, Throwable>
): Promise<HttpClientResult, Throwable> {
    try {
        handler { response ->
            response.bodyHandler { buff ->
                deferred.resolve(
                    HttpClientResult(
                        response.statusCode(),
                        response.statusMessage(),
                        if (buff.length() == 0) null else String(buff.getBytes()),
                        response.headers()
                    )
                )
            }
        }
        exceptionHandler { ex -> deferred.reject(ex) }
        with(this) { init() }
        end()
    } catch (ex: Exception) {
        deferred.reject(ex)
    }
    return deferred.promise
}

fun HttpClient.promiseRequest(
    verb: HttpMethod,
    requestUri: String,
    init: HttpClientRequest.() -> Unit
): Promise<HttpClientResult, Throwable> {
    val deferred = deferred<HttpClientResult, Throwable>()
    try {
        return this.request(verb, requestUri).promise(init, deferred)
    } catch (ex: Throwable) {
        deferred.reject(ex)
    }
    return deferred.promise
}

fun HttpClient.promiseRequestAbs(
    verb: HttpMethod,
    requestUri: String,
    init: HttpClientRequest.() -> Unit
): Promise<HttpClientResult, Throwable> {
    val deferred = deferred<HttpClientResult, Throwable>()
    try {
        return this.requestAbs(verb, requestUri).promise(init, deferred)
    } catch (ex: Throwable) {
        deferred.reject(ex)
    }
    return deferred.promise
}

fun HttpClient.testServer(
    verb: HttpMethod,
    path: String,
    assertStatus: Int = 200,
    assertResponse: String? = null,
    assertContentType: String? = null,
    writeJson: String? = null,
    cookie: String? = null
): String? {
    val result = promiseRequest(verb, "${path.mustStartWith('/')}", {
        if (cookie != null) {
            putHeader(HttpHeaders.COOKIE, cookie)
        }
        if (writeJson != null) {
            putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            setChunked(true)
            write(writeJson)
        }
    }).get()

    assertEquals(
        assertStatus,
        result.statusCode,
        "Error with ${verb} at ${path}, wrong status code.  expected $assertStatus, but received ${result.statusCode}"
    )
    if (assertResponse != null) {
        assertEquals(
            assertResponse,
            result.body,
            "Error with ${verb} at ${path}, wrong body response.  expected: \n$assertResponse\nbut received:\n${result.body}"
        )
    }
    if (assertContentType != null) {
        assertEquals(assertContentType, result.headers.get(HttpHeaders.CONTENT_TYPE))
    }
    return buildCookieHeader(cookie, result.headers.getAll(HttpHeaders.SET_COOKIE))
}

fun buildCookieHeader(oldCookie: String?, cookieSetters: List<String>): String? {
    val newCookieMap = hashMapOf<String, String>()
    val oldCookies = oldCookie?.split(';')?.map { it.trim() } ?: emptyList()
    val newCookies = cookieSetters.map { it.substringBefore(';') }
    newCookieMap.putAll(oldCookies.map { it.substringBefore('=') to UrlEncoding.decode(it.substringAfter('=')) })
    newCookieMap.putAll(newCookies.map { it.substringBefore('=') to UrlEncoding.decode(it.substringAfter('=')) })
    return if (newCookieMap.isEmpty()) null else newCookieMap.entries.map {
        it.key + "=" + UrlEncoding.encodeQueryNameOrValue(
            it.value
        )
    }.joinToString("; ")
}

fun HttpClient.testServerAltContentType(
    verb: HttpMethod,
    path: String,
    assertStatus: Int = 200,
    assertResponse: String? = null,
    writeJson: String? = null,
    cookie: String? = null
): String? {
    val result = promiseRequest(verb, "${path.mustStartWith('/')}", {
        if (cookie != null) {
            putHeader(HttpHeaders.COOKIE, cookie)
        }
        if (writeJson != null) {
            putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
            setChunked(true)
            write(writeJson)
        }
    }).get()

    assertEquals(assertStatus, result.statusCode, "Eror with ${verb} at ${path}")
    assertEquals(assertResponse, result.body, "Eror with ${verb} at ${path}")
    return buildCookieHeader(cookie, result.headers.getAll(HttpHeaders.SET_COOKIE))
}