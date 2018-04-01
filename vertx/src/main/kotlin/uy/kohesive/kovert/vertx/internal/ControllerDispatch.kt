package uy.kohesive.kovert.vertx.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import io.netty.handler.codec.http.HttpHeaderNames
import io.vertx.core.json.Json
import io.vertx.core.logging.Logger
import io.vertx.ext.web.Route
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import uy.klutter.conversion.TypeConversionConfig
import uy.klutter.core.common.mustNotStartWith
import uy.klutter.reflect.isAssignableFrom
import uy.klutter.reflect.unwrapInvokeException
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.vertx.ContextFactory
import uy.kohesive.kovert.vertx.InterceptDispatch
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure


@Suppress("UNCHECKED_CAST")
internal fun setHandlerDispatchWithDataBinding(
    route: Route, logger: Logger,
    controller: Any, member: Any, dispatchInstance: Any,
    dispatchFunction: KCallable<*>,
    contextFactory: ContextFactory<*>,
    disallowVoid: Boolean, defaultSuccessStatus: Int,
    rendererInfo: RendererInfo
) {

    val JSON: ObjectMapper = Json.mapper

    route.handler { routeContext ->
        val requestContext = contextFactory.createContext(routeContext)

        val request = routeContext.request()
        val useValuesInOrder = mutableListOf<Any?>()
        val useValuesByName = hashMapOf<KParameter, Any?>()

        var usedBodyJsonAlready = false

        fun useValueForParm(param: KParameter, value: Any?) {
            useValuesInOrder.add(value)
            useValuesByName.put(param, value)
        }

        try {
            for (param in dispatchFunction.parameters) {
                if (param.kind == KParameter.Kind.INSTANCE) {
                    useValueForParm(param, dispatchInstance)
                } else if (param.kind == KParameter.Kind.EXTENSION_RECEIVER) {
                    useValueForParm(param, requestContext)
                } else if (isSimpleDataType(param.type) || isEnum(param.type)) {
                    val missing = request.params().contains(param.name).not()
                    if (missing && param.isOptional) {
                        // this is ok, optional parameter not resolved will have default value
                    } else {
                        val parmVal = request.getParam(param.name)
                        if ((missing || parmVal == null) && !param.isOptional && !param.type.isMarkedNullable) {
                            throw HttpErrorCode(
                                "Expected parameter ${param.name} for non optional and non nullable parameter, but parameter is missing for [$dispatchFunction]",
                                400
                            )
                        }

                        if (parmVal != null) {
                            try {
                                val temp: Any = TypeConversionConfig.defaultConverter.convertValue<Any, Any>(
                                    parmVal.javaClass,
                                    param.type.jvmErasure.java,
                                    parmVal
                                )

                                //val temp: Any = JSON.convertValue<Any>(parmVal, TypeFactory.defaultInstance().constructType(param.type.javaType))
                                useValueForParm(param, temp)
                            } catch (ex: Exception) {
                                throw RuntimeException("Data binding failed due to: ${ex.message} for [$dispatchFunction]")
                            }
                        } else {
                            useValueForParm(param, null)
                        }
                    }

                } else {
                    // see if parameter has prefixed values in the input parameters that match
                    val parmPrefix = param.name + "."
                    val tempMap = request.params().entries().filter { it.key.startsWith(parmPrefix) }
                        .map { it.key.mustNotStartWith(parmPrefix) to it.value }.toMap()

                    val bodyJson = routeContext.getBodyAsString()
                    val hasBodyJson = bodyJson.isNullOrBlank().not()
                    val hasPrefixParams = tempMap.isNotEmpty()
                    val hasMultiPartForm = request.isExpectMultipart()

                    if (!hasPrefixParams && (!hasBodyJson || usedBodyJsonAlready) && param.isOptional) {
                        // this is ok, optional parameter not resolved will have default value
                    } else if (hasPrefixParams) {
                        //  TODO: later but for each property: val temp: Any = TypeConversionConfig.defaultConverter.convertValue<Any, Any>(parmVal.javaClass, param.type.jvmErasure.java, parmVal)

                        val temp: Any = JSON.convertValue<Any>(
                            tempMap,
                            TypeFactory.defaultInstance().constructType(param.type.jvmErasure.java)
                        )
                        useValueForParm(param, temp)
                    } else if (usedBodyJsonAlready) {
                        throw HttpErrorCode("Already consumed JSON Body, and cannot bind parameter ${param.name} from incoming path, query or multipart form parameters for [$dispatchFunction]")
                    } else if (routeContext.request().getHeader(HttpHeaderNames.CONTENT_TYPE) != "application/json" &&
                        !routeContext.request().getHeader(HttpHeaderNames.CONTENT_TYPE).startsWith("application/json;")
                    ) {
                        throw HttpErrorCode("No JSON Body obviously present (Content-Type header application/json missing), cannot bind parameter ${param.name} from incoming path, query or multipart form parameters for [$dispatchFunction]")
                    } else {
                        try {
                            usedBodyJsonAlready = true
                            val temp: Any = JSON.readValue(
                                routeContext.getBodyAsString(),
                                TypeFactory.defaultInstance().constructType(param.type.jvmErasure.java)
                            )
                            useValueForParm(param, temp)
                        } catch (ex: Throwable) {
                            throw HttpErrorCode(
                                "cannot bind parameter ${param.name} from incoming data, expected valid JSON.  Failed due to ${ex.message}  for [$dispatchFunction]",
                                causedBy = ex
                            )
                        }
                    }
                }

            }
        } catch (rawEx: Throwable) {
            val ex = unwrapInvokeException(rawEx)
            routeContext.fail(ex)
            return@handler
        }

        fun sendStringResponse(result: String, contentType: String) {
            if (routeContext.response().getStatusCode() == 200) {
                routeContext.response().setStatusCode(defaultSuccessStatus)
            }
            routeContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, contentType).end(result)
        }

        fun sendResponse(result: Any?) {
            if (disallowVoid
                && (dispatchFunction.returnType.jvmErasure.simpleName == "void"
                        || dispatchFunction.returnType.jvmErasure.simpleName == "Unit")
            ) {
                routeContext.fail(RuntimeException("Failure after invocation of route function:  A route without a return type must redirect. for [$dispatchFunction]"))
                return
            } else if (dispatchFunction.returnType.jvmErasure.isAssignableFrom(String::class) || result is String) {
                val contentType = routeContext.response().headers().get(HttpHeaderNames.CONTENT_TYPE)
                        ?: routeContext.getAcceptableContentType()
                        //  ?: producesContentType.nullIfBlank()
                        ?: "text/html"

                if (result == null) {
                    routeContext.fail(RuntimeException("Handler did not return any content, only a null which for HTML doesn't really make sense. for [$dispatchFunction]"))
                    return
                }
                sendStringResponse(result as String, contentType)
            } else {
                // at this point we really just need to make a JSON object because we have data not text

                // TODO: should we check if getAcceptableContentType() conflicts with application/json
                // TODO: should we check if the produces content type conflicts with application/json
                // TODO: we now return what they want as content type, but we are really creating JSON
                if (routeContext.response().getStatusCode() == 200) {
                    routeContext.response().setStatusCode(defaultSuccessStatus)
                }
                val contentType = routeContext.getAcceptableContentType()
                        ?: /* producesContentType.nullIfBlank() ?: */ "application/json"
                if (result is Void || result is Unit || result is Nothing) {
                    routeContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, contentType).end()
                } else {
                    routeContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, contentType)
                        .end(JSON.writeValueAsString(result))
                }
            }
        }

        fun prepareResponse(result: Any?) {
            if (rendererInfo.enabled) {
                val (engine, template, model) = if (rendererInfo.dynamic) {
                    if (result is ModelAndTemplateRendering<*>) {
                        Triple(KovertConfig.engineForTemplate(result.template), result.template, result.model)
                    } else {
                        throw Exception("The method with dynamic rendering did not return a ModelAndRenderTemplate for [$dispatchFunction]")
                    }
                } else {
                    // TODO: maybe a better exception here if one of these !! assertions could really fail, think only result!! could fail
                    Triple(rendererInfo.engine!!, rendererInfo.template!!, result!!)
                }
                task { engine.templateEngine.render(template, model) }.success { output ->
                    sendStringResponse(output, rendererInfo.overrideContentType ?: engine.contentType)
                }.fail { rawEx ->
                    val ex = unwrapInvokeException(rawEx)
                    routeContext.fail(ex)
                }
            } else {
                sendResponse(result)
            }
        }

        fun invokeDispatchFunction(): Any? {
            return if (useValuesInOrder.size == dispatchFunction.parameters.size) {
                dispatchFunction.call(*useValuesInOrder.toTypedArray())
            } else {
                dispatchFunction.callBy(useValuesByName)
            }
        }

        try {
            // dispatch via intercept, or directly depending on the controller
            val result: Any? = if (controller is InterceptDispatch<*>) {
                (controller as InterceptDispatch<Any>)._internal(requestContext, member, {
                    invokeDispatchFunction()
                })
            } else {
                invokeDispatchFunction()
            }

            // if a promise, need to wait for it to succeed or fail
            if (result != null && result is Promise<*, *>) {
                (result as Promise<Any, Throwable>).success { promisedResult ->
                    prepareResponse(promisedResult)
                }.fail { rawEx ->
                    val ex = unwrapInvokeException(rawEx)
                    routeContext.fail(ex)
                }
                return@handler
            } else {
                prepareResponse(result)
                return@handler
            }

        } catch (rawEx: Throwable) {
            val ex = unwrapInvokeException(rawEx)
            routeContext.fail(ex)
            return@handler
        }

    }
}





