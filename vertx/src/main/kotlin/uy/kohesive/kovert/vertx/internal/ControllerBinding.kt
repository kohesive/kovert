package uy.kohesive.kovert.vertx.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpHeaderNames
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.auth.User
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import nl.komponents.kovenant.all
import nl.komponents.kovenant.deferred
import uy.klutter.core.common.mustEndWith
import uy.klutter.core.common.mustNotEndWith
import uy.klutter.core.common.mustStartWith
import uy.klutter.core.common.nullIfBlank
import uy.klutter.core.parsing.splitOnCamelCase
import uy.klutter.reflect.KCallableFuncRefOrLambda
import uy.klutter.reflect.erasedType
import uy.klutter.reflect.isAssignableFrom
import uy.klutter.reflect.unwrapInvokeException
import uy.klutter.vertx.externalizeUrl
import uy.klutter.vertx.promiseResult
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.vertx.ContextFactory
import uy.kohesive.kovert.vertx.InterceptRequest
import uy.kohesive.kovert.vertx.InterceptRequestFailure
import java.lang.reflect.Constructor
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.full.defaultType
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.reflect


/**
 * Bind a Kotlin object (or class instance) to a route as a controller.  Binds all members that are either extension methods on a
 * context class (context class may be any class that has a single parameter constructor accepting RoutingContex) or a proeprty
 * that is a reference to such a method.  Method prefix determines the HTTP verb (see defaultVerbAliases).  Or you can override
 * the alises by add @VerbAliases annotation or passing the verbAliases parameter to this method.
 *
 * Optionally the controller can implement interfaces that allow further functionality, see ControllerTraits.kt for more information.
 */
internal fun bindControllerController(
    router: Router,
    kotlinClassAsController: Any,
    atPath: String,
    verbAliases: List<PrefixAsVerbWithSuccessStatus> = emptyList()
) {
    val path = atPath.mustNotEndWith('/').mustStartWith('/')
    val wildPath = path.mustNotEndWith('/').mustEndWith("/*")

    val controller: Any = kotlinClassAsController
    val logger = LoggerFactory.getLogger(controller.javaClass)

    // handle exceptions including a redirect
    router.route(wildPath).failureHandler { context ->
        if (controller is InterceptRequestFailure) {
            try {
                if (context.failed() && context.failure() !is HttpRedirect) {
                    controller.interceptFailure(
                        context,
                        { handleExceptionResponse(controller, context, context.failure()) })
                } else {
                    handleExceptionResponse(controller, context, context.failure())
                }
            } catch (ex: Throwable) {
                handleExceptionResponse(controller, context, ex)
            }
        } else {
            if (context.failure() != null) {
                handleExceptionResponse(controller, context, context.failure())
            } else {
                context.next()
            }
        }
    }

    if (controller is InterceptRequest) {
        router.route(wildPath).handler { context ->
            try {
                controller.interceptRequest(context, { context.next() })
            } catch (ex: Throwable) {
                context.fail(ex)
            }
        }
    }

    val controllerAnnotatedVerbAliases =
        (controller.javaClass.getAnnotation(VerbAliases::class.java)?.value?.toList() ?: emptyList()) +
                listOf(controller.javaClass.getAnnotation(VerbAlias::class.java)).filterNotNull()

    val prefixToVerbMap = (KovertConfig.defaultVerbAliases.values.toList() +
            controllerAnnotatedVerbAliases.map {
                PrefixAsVerbWithSuccessStatus(
                    it.prefix,
                    it.verb,
                    it.successStatusCode
                )
            } +
            verbAliases).associateBy({ it.prefix }, { it })

    fun memberNameToPath(
        name: String,
        knownVerb: VerbWithSuccessStatus?,
        knownLocation: String?,
        skipPrefix: Boolean
    ): Triple<VerbWithSuccessStatus?, String, Set<String>> {
        // split camel case with everything lowered case in the end, unless there are underscores then split literally with no case changing
        // Convert things that are proceeded by "by" to a variable ":variable"  (ByAge = /:age/)
        // Convert things that are proceeded by "with" to a path element + following variable (WithName = /name/:name/)
        val parts = if (name.contains('_')) {
            name.split('_')
        } else {
            name.splitOnCamelCase()
        }.filter { it.isNotEmpty() }

        val pathParms = hashSetOf<String>()
        val memberVerb = knownVerb ?: prefixToVerbMap.get(parts.first().toLowerCase())?.toVerbStatus()
        val skipCount = if (knownVerb == null || skipPrefix) 1 else 0

        // TODO: change this to process by iterating the segments instead of regex replace

        val memberPath = knownLocation ?: parts.drop(skipCount).joinToString("/").replace(
            """((^|[\/])(?:by|in)\/)((?:[\w])+)""".toRegex(),
            { match ->
                // by/something = :something
                val parmName = match.groups.get(3)!!.value
                pathParms.add(parmName)
                match.groups.get(2)!!.value + ":" + parmName
            }).replace("""((^|[\/])with\/)((?:[\w])+)""".toRegex(), { match ->
            // with/something = something/:something
            val parmName = match.groups.get(3)!!.value
            pathParms.add(parmName)
            match.groups.get(2)!!.value + match.groups.get(3)!!.value + "/:" + parmName
        })
        if (memberVerb == null) {
            logger.error("Member $name is invalid, no HTTP Verb and the prefix ${parts.first()} does not match an HTTP verb alias, ignoring this member")
        }
        return kotlin.Triple(memberVerb, memberPath, pathParms)
    }

    fun acceptCallable(
        controller: Any,
        member: Any,
        memberName: String,
        dispatchInstance: Any,
        callable: KCallable<*>
    ) {
        val renderAnnotation = callable.annotations.firstOrNull { it is Rendered } as Rendered?

        val rendererInfo = if (renderAnnotation != null) {
            val engine = if (renderAnnotation.template.isNullOrBlank()) {
                null
            } else {
                try {
                    KovertConfig.engineForTemplate(renderAnnotation.template)
                } catch (ex: Exception) {
                    logger.error("Ignoring member ${memberName} since it has a render template that cannot be associated with a registered template engine (see KovertConfig.registerTemplateEngine)")
                    return
                }
            }
            RendererInfo(
                true,
                renderAnnotation.template.nullIfBlank(),
                renderAnnotation.contentType.nullIfBlank(),
                engine
            )
        } else {
            RendererInfo(false)
        }

        if (callable.parameters.firstOrNull()?.kind == KParameter.Kind.INSTANCE && callable.parameters.drop(1).firstOrNull()?.kind == KParameter.Kind.EXTENSION_RECEIVER) {
            val verbAnnotation = callable.annotations.firstOrNull { it is Verb } as Verb?
            val locationAnnotation = callable.annotations.firstOrNull { it is Location } as Location?
            val (verbAndStatus, subPath) = memberNameToPath(
                memberName,
                verbAnnotation?.toVerbStatus(),
                locationAnnotation?.path,
                verbAnnotation?.skipPrefix ?: false
            )
            if (verbAndStatus != null) {
                setupContextAndRouteForMethod(
                    router,
                    logger,
                    controller,
                    path,
                    verbAndStatus,
                    subPath,
                    member,
                    memberName,
                    dispatchInstance,
                    callable,
                    rendererInfo
                )
            }
        }
    }

    // find extension methods that are also members
    controller.javaClass.kotlin.memberExtensionFunctions.forEach { member ->
        acceptCallable(controller, member, member.name, controller, member)
    }

    // find properties that are function references
    controller.javaClass.kotlin.memberProperties.forEach { member ->
        if (Function::class.isAssignableFrom(member.returnType)) {
            val dispatchInstance = member.get(controller)
            if (dispatchInstance != null && dispatchInstance is Function<*>) {
                try {
                    (dispatchInstance as Function<*>).reflect()
                    val callable =
                        KCallableFuncRefOrLambda.fromInstance(dispatchInstance, member.name, member.annotations)
                    acceptCallable(controller, member, member.name, dispatchInstance, callable)
                } catch (ex: IllegalStateException) {
                    logger.debug("Ignoring property ${member.name}, is of type Function but doesn't appear to have KFunction meta-data (Internal Kotlin thing)")
                }
            } else {
                logger.debug("Ignoring property ${member.name}, is of type Function but has null instance")
            }
        }

    }
}

internal data class VerbWithSuccessStatus(val verb: uy.kohesive.kovert.core.HttpVerb, val successStatusCode: kotlin.Int)

internal fun PrefixAsVerbWithSuccessStatus.toVerbStatus() = VerbWithSuccessStatus(this.verb, this.successStatusCode)
internal fun Verb.toVerbStatus() = VerbWithSuccessStatus(this.verb, this.successStatusCode)


internal val verbToVertx: Map<HttpVerb, HttpMethod> = mapOf(
    HttpVerb.GET to HttpMethod.GET,
    HttpVerb.PUT to HttpMethod.PUT,
    HttpVerb.POST to HttpMethod.POST,
    HttpVerb.DELETE to HttpMethod.DELETE,
    HttpVerb.HEAD to HttpMethod.HEAD,
    HttpVerb.PATCH to HttpMethod.PATCH
)


@Suppress("UNCHECKED_CAST")
private fun setupContextAndRouteForMethod(
    router: Router, logger: Logger, controller: Any, rootPath: String,
    verbAndStatus: VerbWithSuccessStatus, subPath: String,
    member: Any, memberName: String,
    dispatchInstance: Any, dispatchFunction: KCallable<*>,
    rendererInfo: RendererInfo
) {
    val receiverType = dispatchFunction.parameters.first { it.kind == KParameter.Kind.EXTENSION_RECEIVER }.type

    val contextFactory = if (controller is ContextFactory<*>) {
        val factoryFunction = controller.javaClass.kotlin.memberFunctions.first { it.name == "createContext" }
        val factoryType = factoryFunction.returnType
        if (receiverType.isAssignableFrom(factoryType)) {
            controller
        } else {
            val contextConstructor = receiverType.erasedType()
                .kotlin.constructors.firstOrNull { it.parameters.size == 1 && it.parameters.first().type == RoutingContext::class.defaultType }
            if (RoutingContext::class.defaultType.javaType == receiverType.javaType) {
                EmptyContextFactory
            } else if (contextConstructor != null) {
                // TODO: M13 keep Kotlin constructor because we can support default values, and constructors that have other things OTHER than the RoutingContext but are all injected or defaulted or nullable
                TypedContextFactory(contextConstructor.javaConstructor!!)
            } else {
                logger.error("Ignoring member ${memberName} since it has a context that isn't constructable with a simple ctor(RoutingContext)")
                return
            }
        }
    } else {
        if (RoutingContext::class.defaultType == receiverType) {
            EmptyContextFactory
        } else {
            val contextConstructor =
                receiverType.jvmErasure.constructors.firstOrNull { it.parameters.size == 1 && it.parameters.first().type == RoutingContext::class.defaultType }
            if (contextConstructor != null) {
                // TODO: M13 keep Kotlin constructor because we can support default values, and constructors that have other things OTHER than the RoutingContext but are all injected or defaulted or nullable
                TypedContextFactory(contextConstructor.javaConstructor!!)
            } else {
                logger.error("Ignoring member ${memberName} since it has a context that isn't constructable with a simple ctor(RoutingContext)")
                return
            }
        }
    }

    val suffixPath = subPath.mustStartWith('/').mustNotEndWith('/')
    val fullPath = (rootPath.mustNotEndWith('/') + suffixPath).mustNotEndWith('/')

    val finalRoutePath = fullPath.nullIfBlank() ?: "/"
    val vertxVerb = verbToVertx.get(verbAndStatus.verb)!!

    @Suppress("DEPRECATION")
    if (KovertConfig.autoAddBodyHandlersOnPutPostPatch && (verbAndStatus.verb == HttpVerb.POST || verbAndStatus.verb == HttpVerb.PUT || verbAndStatus.verb == HttpVerb.PATCH)) {
        // TODO: configure body max size elsewhere
        router.route(finalRoutePath).method(vertxVerb).handler(BodyHandler.create().setBodyLimit(8 * 1024))
    }

    val authForController = controller.javaClass.annotations.firstOrNull { it is Authority } as Authority?
    val authForContext = receiverType.jvmErasure.java.annotations.firstOrNull { it is Authority } as Authority?
    val authForDispatch = dispatchFunction.annotations.firstOrNull { it is Authority } as Authority?

    listOf(authForController, authForContext, authForDispatch).map { oneAuth ->
        if (oneAuth != null) {
            AuthorityInfo(true, oneAuth.roles.toList(), oneAuth.mode == AuthorityMode.ALL)
        } else {
            AuthorityInfo(false, emptyList(), false)
        }
    }.filter { authInfo -> authInfo.requiresLogin || authInfo.roles.isNotEmpty() }
        .forEach { authInfo ->
            val authRoute = router.route(finalRoutePath).method(vertxVerb)
            authRoute.handler { routeContext ->
                try {
                    val user: User? = routeContext.user()
                    // we code all cases so that fall through unexpectedly is a failure case,
                    // we don't want accidental success here
                    if (user == null && !authInfo.requiresLogin && authInfo.roles.isEmpty()) {
                        // we don't have a user, but no one cares, continue
                        routeContext.next()
                        return@handler
                    } else if (user == null) {
                        // all other cases need a user
                        routeContext.fail(HttpErrorUnauthorized())
                        return@handler
                    } else if (authInfo.requiresLogin && authInfo.roles.isEmpty()) {
                        // we have a user and only require a login to continue
                        routeContext.next()
                        return@handler
                    } else if (authInfo.roles.isNotEmpty()) {
                        val promises = authInfo.roles.map {
                            val deferred = deferred<Boolean, Exception>()
                            user.isAuthorised(it, promiseResult(deferred))
                            deferred.promise
                        }
                        all(promises).success { results ->
                            if (authInfo.requireAll && results.all { it == true }) {
                                routeContext.next()
                            } else if (!authInfo.requireAll && results.any { it == true }) {
                                routeContext.next()
                            } else {
                                // unknown case, fail!
                                routeContext.fail(HttpErrorForbidden())
                            }
                        }.fail { ex ->
                            // unknown case, fail!
                            routeContext.fail(HttpErrorCode("unknown", 500, ex))
                        }
                        return@handler
                    } else {
                        // unknown case, fail!
                        routeContext.fail(HttpErrorCode("unknown", 500))
                        return@handler
                    }
                } catch (rawEx: Throwable) {
                    val ex = unwrapInvokeException(rawEx)
                    routeContext.fail(ex)
                }
            }
        }

    val dispatchRoute = router.route(finalRoutePath).method(vertxVerb)
    val disallowVoid = verbAndStatus.verb == HttpVerb.GET

    val rendererMsg = if (rendererInfo.enabled) {
        if (rendererInfo.dynamic) {
            "-- w/rendering dynamic"
        } else {
            "-- w/rendering template '${rendererInfo.template}' [content-type: ${rendererInfo.overrideContentType
                    ?: "default"}] via engine ${rendererInfo.engine!!.javaClass.name}"
        }
    } else {
        ""
    }

    logger.info("Binding ${memberName} to HTTP ${verbAndStatus.verb}:${verbAndStatus.successStatusCode} ${finalRoutePath} w/context ${receiverType.jvmErasure.java.simpleName} $rendererMsg")

    setHandlerDispatchWithDataBinding(
        dispatchRoute, logger, controller, member,
        dispatchInstance, dispatchFunction,
        contextFactory, disallowVoid,
        verbAndStatus.successStatusCode, rendererInfo
    )
}

internal data class RendererInfo(
    val enabled: Boolean = false,
    val template: String? = null,
    val overrideContentType: String? = null,
    val engine: KovertConfig.RegisteredTemplateEngine? = null,
    val dynamic: Boolean = template.isNullOrBlank()
)

internal data class AuthorityInfo(
    val requiresLogin: Boolean = false,
    val roles: List<String>,
    val requireAll: Boolean = false
)

internal fun handleExceptionResponse(controller: Any, context: RoutingContext, rawEx: Throwable) {
    val logger = LoggerFactory.getLogger(controller.javaClass)
    val ex = unwrapInvokeException(rawEx)
    val exReport = if (KovertConfig.reportStackTracesOnExceptions) ex else null
    when (ex) {
        is HttpRedirect -> {
            val redirectTo = context.externalizeUrl(ex.path)
            logger.debug("HTTP CODE 302 - Redirect to: '$redirectTo'")
            context.response().putHeader("location", ex.path).setStatusCode(ex.code).end()
        }
        is IllegalArgumentException -> {
            logger.error("HTTP CODE 400 - ${context.normalisedPath()} - ${ex.message}", exReport)
            context.response().setStatusCode(400).setStatusMessage("Invalid parameters").end()
        }
        is HttpErrorCodeWithBody -> {
            logger.error(
                "HTTP CODE ${ex.code} - ${context.normalisedPath()} - ${ex.message}",
                if (ex.code == 500) ex else exReport
            )
            if (ex.body is String) {
                context.response()
                    .setStatusCode(ex.code)
                    .setStatusMessage("Error ${ex.code}")
                    .putHeader(HttpHeaderNames.CONTENT_TYPE, "text/html")
                    .end(ex.body as String)
            } else {
                val contentType = "application/json"
                if (ex.body is Void || ex.body is Unit || ex.body is Nothing) {
                    context.response()
                        .setStatusCode(ex.code)
                        .setStatusMessage("Error ${ex.code}")
                        .putHeader(HttpHeaderNames.CONTENT_TYPE, contentType)
                        .end()
                } else {
                    val JSON: ObjectMapper = Json.mapper
                    context.response()
                        .setStatusCode(ex.code)
                        .setStatusMessage("Error ${ex.code}")
                        .putHeader(HttpHeaderNames.CONTENT_TYPE, contentType)
                        .end(JSON.writeValueAsString(ex.body))
                }
            }

        }
        is HttpErrorCode -> {
            logger.error(
                "HTTP CODE ${ex.code} - ${context.normalisedPath()} - ${ex.message}",
                if (ex.code == 500) ex else exReport
            )
            context.response().setStatusCode(ex.code).setStatusMessage("Error ${ex.code}").end()
        }
        else -> {
            logger.error("HTTP CODE 500 - ${context.normalisedPath()} - ${ex.message}", ex)
            context.response().setStatusCode(500).setStatusMessage("Unhandled error 500").end()
        }
    }
}

private object EmptyContextFactory : ContextFactory<RoutingContext> {
    override fun createContext(routingContext: RoutingContext): RoutingContext {
        return routingContext
    }
}

private class TypedContextFactory(val constructor: Constructor<Any>) : ContextFactory<Any> {
    override fun createContext(routingContext: RoutingContext): Any {
        return constructor.newInstance(routingContext)
    }
}

private data class ParamDef(val name: String, val type: Class<*>)


