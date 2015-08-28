package uy.kohesive.kovert.vertx.sample

import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.kovert.core.HttpErrorUnauthorized
import kotlin.properties.Delegates

// we have a simple RestContext object that wraps routingContext and just gets us the current user from the apiKey, or rejects the request if invalid apiKey
class ApiKeySecured(private val routingContext: RoutingContext) {
    public val user: User =  Injekt.get<AuthService>().apiKeyToUser(routingContext.request().getHeader(HttpHeaders.AUTHORIZATION.toString()) ?: "") ?: throw HttpErrorUnauthorized()
}

