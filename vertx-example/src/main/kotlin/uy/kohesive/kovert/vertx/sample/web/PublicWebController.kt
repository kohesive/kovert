package uy.kohesive.kovert.vertx.sample.web

import uy.kohesive.kovert.core.HttpRedirect
import uy.kohesive.kovert.core.Rendered

class PublicWebController {
    @Rendered("login.html.ftl")
    val viewLogin = fun PublicAccess.(): EmptyModel {
        if (user != null) {
            throw HttpRedirect("/app") // TODO: replace with looking up HREF from a controller
        }
        return EmptyModel()
    }

    @Rendered("login.html.ftl")
    val index = viewLogin

    // TODO: problem here with return type of Unit, should be allowed.  ALso for rendered as EmptyModel
    // TODO: move EmptyModel into Kovert core
    val doLogout = fun PublicAccess.(): String {
        try {
            if (user != null) upgradeToSecured().logout()
        } catch (ex: Throwable) {
            // eat exceptions during logout, why let it fail?!?
        } finally {
            throw HttpRedirect("/login") // TODO: change to use a reference lookup to the public web controller login page
        }
    }
}

