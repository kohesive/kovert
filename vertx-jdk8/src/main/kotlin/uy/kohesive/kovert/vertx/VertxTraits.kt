package uy.kohesive.kovert.vertx

import io.vertx.ext.web.RoutingContext


/**
 * Optionally intercept a raw vert-x request, before the dispatch is known.  Must call nextHandler() if not blocking
 * this request with either an exception, or calling vert-x to end the handler.
 */
public interface InterceptRequest : uy.kohesive.kovert.core.CoreInterceptRequest<RoutingContext> {
    override public fun interceptRequest(rawContext: RoutingContext, nextHandler: () -> Unit)
}

/**
 * Optionally intercept a dispatch to a handling method.  This method is expected to return either the value from
 * a call to nextHandler() unchanged or modified, or an alternative return value.  Blocking the call can occur by
 * throwing exceptions. And exceptions can be caught and handled from the actual dispatch call by wrapping the call
 * to nextHandler() in a try...catch.
 */
public interface InterceptDispatch<T : Any> : uy.kohesive.kovert.core.CoreInterceptDispatch<T> {
    override public fun T.interceptDispatch(member: Any, dispatcher: () -> Any?): Any?

    fun _internal(receiver: T, targetMember: Any, dispatcher: () -> Any?): Any? {
        return receiver.interceptDispatch(targetMember, dispatcher)
    }
}

/**
 * Optionally intercept a raw vert-x failure which can occur at any time an error happens since the start of the handler
 * chain.  This method should call nextHandler() when done if other handlers should execute, or it should tell vert-x
 * to end the route.
 */
public interface InterceptRequestFailure : uy.kohesive.kovert.core.CoreInterceptRequestFailure<RoutingContext> {
    override public fun interceptFailure(rawContext: RoutingContext, nextHandler: () -> Unit)
}

/**
 * Optionally construct the context object used by methods within a controller.  This is typed to the class being extended
 * by a function in the class.  Otherwise contexts are automatically found and constructed by their type + having a single
 * parameter constructor expecting a RoutingContext.
 */
public interface ContextFactory<T : Any> : uy.kohesive.kovert.core.CoreContextFactory<RoutingContext, T> {
    override fun createContext(routingContext: RoutingContext): T
}
