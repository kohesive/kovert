package uy.kohesive.kovert.vertx.sample.web

import uy.kohesive.kovert.core.HttpRedirect
import uy.kohesive.kovert.core.Rendered
import uy.kohesive.kovert.vertx.sample.services.User

public class PublicWebController {
    @Rendered("login.html.ftl")
    val viewLogin = fun PublicAccess.(): EmptyModel {
        if (user != null) {
            throw HttpRedirect("/app") // TODO: replace with looking up HREF from a controller
        }
        return EmptyModel()
    }
}

