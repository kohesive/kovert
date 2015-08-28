package uy.kohesive.kovert.vertx.sample

import io.vertx.ext.auth.User
import io.vertx.ext.web.RoutingContext
import kotlin.properties.Delegates

// we have a simple RestContext object that wraps routingContext but does little with it
class RestContext(private val routingContext: RoutingContext) {
    public val user: User by Delegates.lazy { routingContext.user() }
}

