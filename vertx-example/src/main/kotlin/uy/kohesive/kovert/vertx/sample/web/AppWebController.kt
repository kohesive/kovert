package uy.kohesive.kovert.vertx.sample.web

import uy.kohesive.kovert.core.Rendered
import uy.kohesive.kovert.vertx.sample.services.User


class AppWebController {
    @Rendered("app/home.html.ftl")
    val index = fun UserSessionSecured.(): BaseAppModel {
        return BaseAppModel(user, BaseLinks())
    }
}

open class BaseAppModel(val user: User, val commonLinks: BaseLinks)
open class BaseLinks(
        val logout: String = "/logout"  // TODO: use a reference to get the logout link
)