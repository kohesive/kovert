package uy.kohesive.kovert.vertx.internal

import io.vertx.core.http.HttpMethod
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import uy.klutter.core.common.initializedBy
import uy.klutter.core.jdk.mustEndWith
import uy.klutter.core.jdk.mustNotEndWith
import uy.klutter.core.jdk.mustStartWith
import uy.klutter.core.jdk.nullIfBlank
import uy.klutter.vertx.externalizeUrl
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.core.reflect.erasedType
import uy.kohesive.kovert.core.reflect.isAssignableFrom
import uy.kohesive.kovert.vertx.ContextFactory
import uy.kohesive.kovert.vertx.InterceptRequest
import uy.kohesive.kovert.vertx.InterceptRequestFailure
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.reflect.*
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.reflect


/**
 * Bind a Kotlin object (or class instance) to a route as a controller.  Binds all members that are either extension methods on a
 * context class (context class may be any class that has a single parameter constructor accepting RoutingContex) or a proeprty
 * that is a reference to such a method.  Method prefix determines the HTTP verb (see defaultVerbAliases).  Or you can override
 * the alises by add @VerbAliases annotation or passing the verbAliases parameter to this method.
 *
 * Optionally the controller can implement interfaces that allow further functionality, see ControllerTraits.kt for more information.
 */
internal fun bindControllerController(router: Router, kotlinClassAsController: Any, atPath: String, verbAliases: List<PrefixAsVerbWithSuccessStatus> = emptyList()) {
    val path = atPath.mustNotEndWith('/').mustStartWith('/')
    val wildPath = path.mustNotEndWith('/').mustEndWith("/*")

    val controller: Any = kotlinClassAsController
    val logger = LoggerFactory.getLogger(controller.javaClass)

    // handle exceptions including a redirect
    router.route(wildPath).failureHandler { context ->
        if (controller is InterceptRequestFailure) {
            try {
                if (context.failed() && context.failure() !is HttpRedirect) {
                    controller.interceptFailure(context, { handleExceptionResponse(controller, context, context.failure()) })
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

    val controllerAnnotatedVerbAliases = (controller.javaClass.getAnnotation(VerbAliases::class.java)?.value?.toList() ?: emptyList()) +
            listOf(controller.javaClass.getAnnotation(VerbAlias::class.java)).filterNotNull()

    val prefixToVerbMap = (KovertConfig.defaultVerbAliases.values().toList() +
            controllerAnnotatedVerbAliases.map { PrefixAsVerbWithSuccessStatus(it.prefix, it.verb, it.successStatusCode) } +
            verbAliases).toMap({ it.prefix }, { it })

    fun memberNameToPath(name: String, knownVerb: VerbWithSuccessStatus?, knownLocation: String?, skipPrefix: Boolean): Pair<VerbWithSuccessStatus?, String> {
        // split camel cases, with underscores also acting as split point then ignored
        // Convert things that are proceeded by "by" to a variable ":variable"  (ByAge = /:age/)
        // Convert things that are proceeded by "with" to a path element + following variable (WithName = /name/:name/)
        //
        // thisIsATestOfSplitting = this is a test of splitting
        // AndWhatAboutThis = and what about this
        // aURIIsPresent = a uri is present
        // SomethingBySomething = something :something
        // something20BySomething30 = something20 :something30
        // 20ThisAndThat = 20 this and that
        // 20thisAndThat = 20this and that
        // What_about_underscores = what about underscores
        // 20_ThisAndThat_And_What = 20 this and that and what
        // 20________thisAndThat__What = 20 this and that what
        //
        val parts = name.split("""(?<=[a-z]|[0-9])(?=[A-Z])|(?<=[A-Z]|[0-9])(?=[A-Z][a-z])|(?<=[^\_])(?=[\_]+)|(?<=[\_])(?=[^\_])""".toRegex())
                .filterNot { it.isNullOrBlank() }
                .map { it.trim().toLowerCase() }
                .filterNot { it.trim().all { it == '_' } }
        val memberVerb = knownVerb ?: prefixToVerbMap.get(parts.first().toLowerCase())?.toVerbStatus()
        val skipCount = if (knownVerb == null || skipPrefix) 1 else 0
        val memberPath = knownLocation ?: parts.drop(skipCount).joinToString("/").replace("""((^|[\/])(?:by|in)\/)((?:[\w])+)""".toRegex(), { match ->
            // by/something = :something
            match.groups.get(2)!!.value + ":" + match.groups.get(3)!!.value
        }).replace("""((^|[\/])with\/)((?:[\w])+)""".toRegex(), { match ->
            // with/something = something/:something
            match.groups.get(2)!!.value + match.groups.get(3)!!.value + "/:" + match.groups.get(3)!!.value
        })
        if (memberVerb == null) {
            logger.error("Member $name is invalid, no HTTP Verb and the prefix ${parts.first()} does not match an HTTP verb alias, ignoring this member")
        }
        return kotlin.Pair(memberVerb, memberPath)
    }

    // find extension methods that are also members
    controller.javaClass.kotlin.memberExtensionFunctions.forEach { member ->
        if (member.parameters.firstOrNull()?.kind == KParameter.Kind.INSTANCE || member.parameters.drop(1).firstOrNull()?.kind == KParameter.Kind.EXTENSION_RECEIVER) {
            val verbAnnotation = member.annotations.firstOrNull { it is Verb } as Verb?
            val locationAnnotation = member.annotations.firstOrNull { it is Location } as Location?

            // looking for extension functions
            val dispatchInstance = controller
            val (verbAndStatus, subPath) = memberNameToPath(member.name, verbAnnotation?.toVerbStatus(), locationAnnotation?.path, verbAnnotation?.skipPrefix ?: false)
            if (verbAndStatus != null) {
                setupContextAndRouteForMethod(router, logger, controller, path, verbAndStatus, subPath, member, member.name, dispatchInstance, member)
            }
        }
    }

    // find properties that are function references
    controller.javaClass.kotlin.memberProperties.forEach { prop ->
        if (Function::class.isAssignableFrom(prop.returnType)) {
            val dispatchInstance = prop.get(controller)
            if (dispatchInstance != null && dispatchInstance is Function<*>) {
                try {
                    val callable = KFunctionKt9005WorkAround(prop, dispatchInstance)
                    if (callable.parameters.firstOrNull()?.kind == KParameter.Kind.EXTENSION_RECEIVER) {
                        val verbAnnotation = callable.annotations.firstOrNull { it is Verb } as Verb?
                        val locationAnnotation = callable.annotations.firstOrNull { it is Location } as Location?
                        val (verbAndStatus, subPath) = memberNameToPath(prop.name, verbAnnotation?.toVerbStatus(), locationAnnotation?.path, verbAnnotation?.skipPrefix ?: false)
                        if (verbAndStatus != null) {
                            setupContextAndRouteForMethod(router, logger, controller, path, verbAndStatus, subPath, prop, prop.name, dispatchInstance, callable)
                        }
                    }
                } catch (ex: IllegalStateException) {
                    logger.debug("Ignoring property ${prop.name}, is of type Function but doesn't appear to have KFunction meta-data (Internal Kotlin thing)")
                }
            } else {
                logger.debug("Ignoring property ${prop.name}, is of type Function but has null instance")
            }
        }

    }
}

class KFunctionKt9005WorkAround<out R : Any?>(private val _member: KProperty<R>, private val _functionInstance: Function<R>) : KCallable<R> {
    private val _reflectedFunction: KFunction<R> = _functionInstance.reflect() ?: throw IllegalStateException("The function instance isn't reflect-able")
    private val _invokeMethod: Method = _functionInstance.javaClass.getMethods().filter { method ->
        method.getName() == "invoke" &&
                !method.isBridge &&
                method.parameterCount == _reflectedFunction.parameters.size()
    }.first() initializedBy { it.isAccessible = true }

    private val _parameters: List<KParameter> = run {
        _invokeMethod.parameters.withIndex().zip(_reflectedFunction.parameters).map {
            object : KParameter {
                override val index: Int = it.first.index
                override val isOptional: Boolean = it.second.isOptional
                override val kind: KParameter.Kind = it.second.kind
                override val name: String? = it.second.name
                override val type: KType = it.first.value.getType().kotlin.defaultType
                override val annotations: List<Annotation> = _member.annotations
            }
        }
    }

    override val name: String = _member.name
    override val parameters: List<KParameter> = _parameters
    override val returnType: KType = _invokeMethod.returnType.kotlin.defaultType
    override val annotations: List<Annotation> = _member.annotations

    @Suppress("UNCHECKED_CAST")
    override fun call(vararg args: Any?): R {
        return _invokeMethod.invoke(_functionInstance, *args) as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun callBy(args: Map<KParameter, Any?>): R {
        throw UnsupportedOperationException()
    }
}

internal data class VerbWithSuccessStatus(val verb: uy.kohesive.kovert.core.HttpVerb, val successStatusCode: kotlin.Int)

internal fun PrefixAsVerbWithSuccessStatus.toVerbStatus() = VerbWithSuccessStatus(this.verb, this.successStatusCode)
internal fun Verb.toVerbStatus() = VerbWithSuccessStatus(this.verb, this.successStatusCode)


internal val verbToVertx: Map<HttpVerb, HttpMethod> = mapOf(HttpVerb.GET to HttpMethod.GET,
        HttpVerb.PUT to HttpMethod.PUT,
        HttpVerb.POST to HttpMethod.POST,
        HttpVerb.DELETE to HttpMethod.DELETE,
        HttpVerb.HEAD to HttpMethod.HEAD,
        HttpVerb.PATCH to HttpMethod.PATCH)


@Suppress("UNCHECKED_CAST")
private fun setupContextAndRouteForMethod(router: Router, logger: Logger, controller: Any, rootPath: String, verbAndStatus: VerbWithSuccessStatus, subPath: String, member: Any, memberName: String, dispatchInstance: Any, dispatchFunction: KCallable<*>) {
    val receiverType = dispatchFunction.parameters.first { it.kind == KParameter.Kind.EXTENSION_RECEIVER }.type

    val contextFactory = if (controller is ContextFactory<*>) {
        val factoryFunction = controller.javaClass.kotlin.memberFunctions.first { it.name == "createContext" }
        val factoryType = factoryFunction.returnType
        if (receiverType.isAssignableFrom(factoryType)) {
            controller
        } else {
            val contextConstructor = receiverType.erasedType().kotlin.constructors.firstOrNull { it.parameters.size() == 1 && it.parameters.first().type == RoutingContext::class.defaultType }
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
        if (RoutingContext::class.defaultType.javaType == receiverType.javaType) {
            EmptyContextFactory
        } else {
            val contextConstructor = receiverType.erasedType().kotlin.constructors.firstOrNull { it.parameters.size() == 1 && it.parameters.first().type == RoutingContext::class.defaultType }
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

    if (verbAndStatus.verb == HttpVerb.POST || verbAndStatus.verb == HttpVerb.PUT || verbAndStatus.verb == HttpVerb.PATCH) {
        // TODO: configure body max size elsewhere
        router.route(finalRoutePath).method(verbToVertx.get(verbAndStatus.verb)!!).handler(BodyHandler.create().setBodyLimit(8 * 1024))
    }

    val route = router.route(finalRoutePath).method(vertxVerb)

    val disallowVoid = verbAndStatus.verb == HttpVerb.GET

    logger.info("Binding ${memberName} to HTTP ${verbAndStatus.verb}:${verbAndStatus.successStatusCode} ${finalRoutePath} w/context ${receiverType.erasedType().simpleName}")

    setHandlerDispatchWithDataBinding(route, logger, controller, member, dispatchInstance, dispatchFunction, contextFactory, disallowVoid, verbAndStatus.successStatusCode)
}

internal fun unwrapInvokeException(rawEx: Throwable): Throwable {
    return if (rawEx is InvocationTargetException) rawEx.getCause()!! else rawEx
}

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
            logger.error("HTTP CODE 400 - ${context.normalisedPath()} - ${ex.getMessage()}", exReport)
            context.response().setStatusCode(400).setStatusMessage("Invalid parameters").end()
        }
        is HttpErrorCode -> {
            logger.error("HTTP CODE ${ex.code} - ${context.normalisedPath()} - ${ex.getMessage()}", if (ex.code == 500) ex else exReport)
            context.response().setStatusCode(ex.code).setStatusMessage("Error ${ex.code}").end()
        }
        else -> {
            logger.error("HTTP CODE 500 - ${context.normalisedPath()} - ${ex.getMessage()}", ex)
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


