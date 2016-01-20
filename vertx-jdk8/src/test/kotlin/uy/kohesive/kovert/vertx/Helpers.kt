package uy.kohesive.kovert.vertx.test

import io.vertx.core.MultiMap
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import uy.klutter.core.jdk.*
import kotlin.test.assertEquals

data class HttpClientResult(val statusCode: Int, val statusMessage: String, val body: String?, val headers: MultiMap)

public fun HttpClientRequest.promise(): Promise<HttpClientResult, Throwable> {
    val deferred = deferred<HttpClientResult, Throwable>()
    return promise({}, deferred)
}

public fun HttpClientRequest.promise(init: HttpClientRequest.()->Unit): Promise<HttpClientResult, Throwable> {
    val deferred = deferred<HttpClientResult, Throwable>()
    return promise(init, deferred)
}

public fun HttpClientRequest.promise(init: HttpClientRequest.()->Unit, deferred: Deferred<HttpClientResult, Throwable>): Promise<HttpClientResult, Throwable> {
    try {
        handler { response ->
            response.bodyHandler { buff ->
                deferred.resolve(HttpClientResult(response.statusCode(), response.statusMessage(), if (buff.length() == 0) null else String(buff.getBytes()), response.headers()))
            }
        }
        exceptionHandler { ex -> deferred.reject(ex) }
        with (this) { init() }
        end()
    }
    catch (ex: Exception) {
        deferred.reject(ex)
    }
    return deferred.promise
}

public fun HttpClient.promiseRequest(verb: HttpMethod, requestUri: String, init: HttpClientRequest.()->Unit): Promise<HttpClientResult, Throwable> {
    val deferred = deferred<HttpClientResult, Throwable>()
    try {
        return this.request(verb, requestUri).promise(init, deferred)
    }
    catch (ex: Throwable) {
        deferred.reject(ex)
    }
    return deferred.promise
}

public fun HttpClient.promiseRequestAbs(verb: HttpMethod, requestUri: String, init: HttpClientRequest.()->Unit): Promise<HttpClientResult, Throwable> {
    val deferred = deferred<HttpClientResult, Throwable>()
    try {
        return this.requestAbs(verb, requestUri).promise(init, deferred)
    }
    catch (ex: Throwable) {
        deferred.reject(ex)
    }
    return deferred.promise
}

public fun HttpClient.testServer(verb: HttpMethod, path: String, assertStatus: Int = 200, assertResponse: String? = null, assertContentType: String? = null, writeJson: String? = null) {
    val result = promiseRequest(verb, "${path.mustStartWith('/')}", {
        if (writeJson != null) {
            putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            setChunked(true)
            write(writeJson)
        }
    }).get()

    assertEquals(assertStatus, result.statusCode, "Eror with ${verb} at ${path}")
    if (result.statusCode < 300) {
        // only care about the body on success codes (unless we are testing that they are blank for other reasons)
        assertEquals(assertResponse, result.body, "Eror with ${verb} at ${path}")
    }
    if (assertContentType != null) {
        assertEquals(assertContentType, result.headers.get(HttpHeaders.CONTENT_TYPE))
    }
}

public fun HttpClient.testServerAltContentType(verb: HttpMethod, path: String, assertStatus: Int = 200, assertResponse: String? = null, writeJson: String? = null) {
    val result = promiseRequest(verb, "${path.mustStartWith('/')}", {
        if (writeJson != null) {
            putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
            setChunked(true)
            write(writeJson)
        }
    }).get()

    assertEquals(assertStatus, result.statusCode, "Eror with ${verb} at ${path}")
    assertEquals(assertResponse, result.body, "Eror with ${verb} at ${path}")
}