package uy.kohesive.kovert.vertx.sample.web

import io.vertx.ext.web.RoutingContext
import uy.kohesive.kovert.core.HttpErrorUnauthorized
import uy.kohesive.kovert.vertx.sample.services.User


class PublicAccess(private val routingContext: RoutingContext) {
    // maybe logged in, is ok if not
    val user: User? = routingContext.user() as? uy.kohesive.kovert.vertx.sample.services.User

    // allow access to functions for logged in user on public pages, if logged in, otherwise this will blow up with exception
    fun upgradeToSecured(): UserSessionSecured = UserSessionSecured(routingContext)
}

class UserSessionSecured(private val routingContext: RoutingContext) {
    // must be logged in, if not, bad!! (the AuthHandler should already prevent getting this far)
    val user: User =
        routingContext.user() as? uy.kohesive.kovert.vertx.sample.services.User ?: throw HttpErrorUnauthorized()

    fun logout() {
        routingContext.clearUser()
        routingContext.session().destroy()
    }
}

class EmptyModel()


