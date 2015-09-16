package uy.kohesive.kovert.core

public open class HttpRedirect(val path: String, val code: Int = 302) : Exception()
public open class HttpErrorUnauthorized() : HttpErrorCode("unauthorized", 401)
public open class HttpErrorForbidden() : HttpErrorCode("forbidden", 403)
public open class HttpErrorBadRequest() : HttpErrorCode("bad request", 400)
public open class HttpErrorNotFound() : HttpErrorCode("not found", 404)

public open class HttpErrorCode(message: String, val code: Int = 500, causedBy: Throwable? = null) : Exception(message, causedBy)

