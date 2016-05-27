package uy.kohesive.kovert.core

open class HttpRedirect(val path: String, val code: Int = 302) : Exception()
open class HttpErrorUnauthorized() : HttpErrorCode("unauthorized", 401)
open class HttpErrorForbidden() : HttpErrorCode("forbidden", 403)
open class HttpErrorBadRequest() : HttpErrorCode("bad request", 400)
open class HttpErrorNotFound() : HttpErrorCode("not found", 404)

open class HttpErrorCode(message: String, val code: Int = 500, causedBy: Throwable? = null) : Exception(message, causedBy)
open class HttpErrorCodeWithBody(message: String, code: Int = 500, val body: Any, causedBy: Throwable? = null) : HttpErrorCode(message, code, causedBy)
