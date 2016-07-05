package uy.kohesive.kovert.core


/**
 * Optionally intercept a raw request, before the dispatch is known.  Must call nextHandler() if not blocking
 * this request with either an exception, or ending the handler chain (platform specific).
 */
interface CoreInterceptRequest<T> {
    fun interceptRequest(rawContext: T, nextHandler: ()->Unit)
}

/**
 * Optionally intercept a dispatch to a handling method.  This method is expected to return either the value from
 * a call to nextHandler() unchanged or modified, or an alternative return value.  Blocking the call can occur by
 * throwing exceptions. And exceptions can be caught and handled from the actual dispatch call by wrapping the call
 * to nextHandler() in a try...catch.
 */
interface CoreInterceptDispatch<T: Any> {
    fun T.interceptDispatch(member: Any, dispatcher: ()->Any?): Any?
}

/**
 * Optionally intercept a raw failure which can occur at any time an error happens since the start of the handler
 * chain.  This method should call nextHandler() when done if other handlers should execute, or it should tell the
 * system (platform specific) to end the route.
 */
interface CoreInterceptRequestFailure<T> {
    fun interceptFailure(rawContext: T, nextHandler: ()->Unit)
}

/**
 * Optionally construct the context object used by methods within a controller.  This is typed to the class being extended
 * by a function in the class.  Otherwise contexts are automatically found and constructed by their type + having a single
 * parameter constructor expecting a platform specific context.
 */
interface CoreContextFactory<T, V : Any> {
    fun createContext(routingContext: T): V
}


