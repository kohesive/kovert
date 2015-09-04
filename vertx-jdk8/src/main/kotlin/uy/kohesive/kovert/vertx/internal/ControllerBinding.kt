package uy.kohesive.kovert.vertx.internal

import io.vertx.core.http.HttpMethod
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import jet.runtime.typeinfo.JetValueParameter
import uy.klutter.core.jdk.mustEndWith
import uy.klutter.core.jdk.mustNotEndWith
import uy.klutter.core.jdk.mustStartWith
import uy.klutter.core.jdk.nullIfBlank
import uy.klutter.vertx.externalizeUrl
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.vertx.*
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlin.reflect.jvm.java
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.kotlin


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
        }
        else {
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

    val controllerAnnotatedVerbAliases = (controller.javaClass.getAnnotation(kotlin.javaClass<VerbAliases>())?.value?.toList() ?: emptyList()) +
                                          listOf(controller.javaClass.getAnnotation(kotlin.javaClass<VerbAlias>())).filterNotNull()

    val prefixToVerbMap = (KovertConfig.defaultVerbAliases.values().toList() +
                          controllerAnnotatedVerbAliases.map { PrefixAsVerbWithSuccessStatus(it.prefix, it.verb, it.successStatusCode) } +
                          verbAliases).toMap({it.prefix}, {it})

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
            match.groups.get(2)!!.value + match.groups.get(3)!!.value+"/:" + match.groups.get(3)!!.value
        })
        if (memberVerb == null) {
            logger.error("Member $name is invalid, no HTTP Verb and the prefix ${parts.first()} does not match an HTTP verb alias, ignoring this member")
        }
        return kotlin.Pair(memberVerb, memberPath)
    }

    // find extension methods that are also members
    controller.javaClass.getMethods().forEach { method ->
        val paramAnnotations = method.getParameterAnnotations()
        if (paramAnnotations != null && paramAnnotations.size() >= 1 && paramAnnotations.size() == method.getParameterCount()) {
            // TODO: M13 change coming, no more JetValueParameter
            val possibleReceiverParameter: JetValueParameter? = paramAnnotations[0].firstOrNull { it.annotationType() == kotlin.javaClass<jet.runtime.typeinfo.JetValueParameter>() } as JetValueParameter?
            if (possibleReceiverParameter != null && possibleReceiverParameter.name == "\$receiver") {
                val verbAnnotation = method.getAnnotation(kotlin.javaClass<Verb>())
                val locationAnnotation = method.getAnnotation(kotlin.javaClass<Location>())

                // looking for extension functions
                val dispatchInstance = controller
                val dispatchFunction = method
                dispatchFunction.setAccessible(true)
                val (verbAndStatus, subPath) = memberNameToPath(method.getName(), verbAnnotation?.toVerbStatus(), locationAnnotation?.path, verbAnnotation?.skipPrefix ?: false)
                if (verbAndStatus != null) {
                    setupContextAndRouteForMethod(router, logger, controller, path, verbAndStatus, subPath, method, method.getName(), dispatchInstance, dispatchFunction, paramAnnotations)
                }
            }
        }
    }

    // find properties that are function references
    controller.javaClass.kotlin.properties.forEach { prop ->
        val propJava = prop.javaField!!
        val typeNameOfField = propJava.getType().getName()
        // TODO: M13 change coming, Function classes change!
        if (typeNameOfField.startsWith((kotlin.jvm.functions.Function0::class.java).getName().mustNotEndWith('0'))) {
            val dispatchInstance = prop.get(controller)
            if (dispatchInstance != null) {
                val dispatchInstanceMethods = dispatchInstance.javaClass.getMethods()
                val dispatchFunction = dispatchInstance.javaClass.getMethods().filter { method -> method.getName() == "invoke" }.filter { it.getParameterAnnotations().all { it.any { it.annotationType() == kotlin.javaClass<jet.runtime.typeinfo.JetValueParameter>() } } }.firstOrNull()
                if (dispatchFunction != null) {
                    val verbAnnotation = propJava.getAnnotation(kotlin.javaClass<Verb>())
                    val locationAnnotation = propJava.getAnnotation(kotlin.javaClass<Location>())

                    val paramAnnotations = dispatchFunction.getParameterAnnotations()
                    val receiverName = paramAnnotations[0].first { it.annotationType() == kotlin.javaClass<jet.runtime.typeinfo.JetValueParameter>() } as JetValueParameter
                    if (receiverName.name != "\$receiver") {
                        logger.debug("Ignoring property ${prop.name}, is of type Function, has instance, has invoke, but is not an extension method or is missing parameter names")
                    }

                    val (verbAndStatus, subPath) = memberNameToPath(prop.name, verbAnnotation?.toVerbStatus(), locationAnnotation?.path, verbAnnotation?.skipPrefix ?: false)
                    if (verbAndStatus != null) {
                        dispatchFunction.setAccessible(true)
                        setupContextAndRouteForMethod(router, logger, controller, path, verbAndStatus, subPath, prop, prop.name, dispatchInstance, dispatchFunction, paramAnnotations)
                    }
                    else {

                    }
                } else {
                    logger.debug("Ignoring property ${prop.name}, is of type Function, has instance, but no invoke method we can recognize with parameter names")
                }
            } else {
                logger.debug("Ignoring property ${prop.name}, is of type Function but has no obvious instance")
            }
        } else {
            logger.debug("Ignoring property ${prop.name}, is not a reference to a Function")
        }
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

@suppress("UNCHECKED_CAST")
private fun setupContextAndRouteForMethod(router: Router, logger: Logger, controller: Any, rootPath: String, verbAndStatus: VerbWithSuccessStatus, subPath: String, member: Any, memberName: String, dispatchInstance: Any, dispatchFunction: Method, paramAnnotations: Array<Array<Annotation>>) {
    val receiverType = dispatchFunction.getParameterTypes().first()

    val contextFactory = if (controller is ContextFactory<*>) {
        val declaredFactoryType = controller.javaClass.getGenericInterfaces().filter { it is ParameterizedType }.map { it as ParameterizedType }.filter { it.getRawType() == javaClass<ContextFactory<*>>() }.first()
        val factoryType = declaredFactoryType.getActualTypeArguments().first() as Class<Any>
        if (receiverType.isAssignableFrom(factoryType)) {
            controller
        }
        else {
            val contextConstructor = receiverType.getConstructor(javaClass<RoutingContext>())
            if (contextConstructor != null) {
                TypedContextFactory(contextConstructor as Constructor<Any>)
            }  else {
                logger.error("Ignoring member ${memberName} since it has a context that isn't constructable with a simple ctor(RoutingContext)")
                return
            }
        }
    } else {
        if (javaClass<RoutingContext>() == receiverType) {
            EmptyContextFactory
        }
        else {
            val contextConstructor = receiverType.getConstructor(javaClass<RoutingContext>())
            if (contextConstructor != null) {
                TypedContextFactory(contextConstructor as Constructor<Any>)
            }  else {
                logger.error("Ignoring member ${memberName} since it has a context that isn't constructable with a simple ctor(RoutingContext)")
                return
            }
        }
    }

    val returnType = dispatchFunction.getReturnType()
    val paramTypes = dispatchFunction.getParameterTypes().drop(1)
    val paramNames = paramAnnotations.drop(1).map { it.filterIsInstance(javaClass<JetValueParameter>()).first().name }

    val paramDefs = paramNames.zip(paramTypes).map { ParamDef(it.first, it.second) }
    val paramContainsComplex = paramDefs.any { !isSimpleDataType(it.type) }

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

    logger.info("Binding ${memberName} to HTTP ${verbAndStatus.verb}:${verbAndStatus.successStatusCode} ${finalRoutePath} w/context ${receiverType.getSimpleName()}")

    setHandlerDispatchWithDataBinding(route, logger, controller, member, dispatchInstance, dispatchFunction, returnType, paramDefs, contextFactory, disallowVoid, verbAndStatus.successStatusCode)
}

private fun unwrapInvokeException(rawEx: Throwable): Throwable {
    return if (rawEx is InvocationTargetException) rawEx.getCause()!! else rawEx
}

private fun handleExceptionResponse(controller: Any, context: RoutingContext, rawEx: Throwable) {
    val logger = LoggerFactory.getLogger(controller.javaClass)
    val ex = unwrapInvokeException(rawEx)
    when (ex) {
        is HttpRedirect -> {
            val redirectTo = context.externalizeUrl(ex.path)
            logger.debug("HTTP CODE 302 - Redirect to: '$redirectTo'")
            context.response().putHeader("location", ex.path).setStatusCode(ex.code).end()
        }
        is IllegalArgumentException -> {
            logger.error("HTTP CODE 400 - ${context.normalisedPath()} - ${ex.getMessage()}")
            context.response().setStatusCode(400).setStatusMessage("Invalid parameters").end()
        }
        is HttpErrorCode -> {
            logger.error("HTTP CODE ${ex.code} - ${context.normalisedPath()} - ${ex.getMessage()}", if (ex.code == 500) ex else null)
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


