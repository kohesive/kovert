package uy.kohesive.kovert.vertx.sample.api

import com.github.salomonbrys.kodein.global.KodeinGlobalAware
import com.github.salomonbrys.kodein.instance
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import uy.kohesive.kovert.core.HttpErrorUnauthorized
import uy.kohesive.kovert.vertx.sample.services.AuthService
import uy.kohesive.kovert.vertx.sample.services.User

// we have a simple RestContext object that wraps routingContext and just gets us the current user from the apiKey, or rejects the request if invalid apiKey
class ApiKeySecured(private val routingContext: RoutingContext): KodeinGlobalAware {
    val user: User = instance<AuthService>().apiKeyToUser(routingContext.request().getHeader(HttpHeaders.AUTHORIZATION.toString()) ?: "") ?: throw HttpErrorUnauthorized()
}

