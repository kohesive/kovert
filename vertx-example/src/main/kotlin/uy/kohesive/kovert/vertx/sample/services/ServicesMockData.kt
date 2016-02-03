package uy.kohesive.kovert.vertx.sample.services

import java.util.*

internal val mockData_companyByName = listOf(
        Company("Collokia", "Uruguay"),
        Company("Bremeld", "Uruguay"),
        Company("Jetbrains", "Czech Republic"))
        .associateByTo(HashMap()) { it.name.toLowerCase() }

internal val mockData_peopleById = listOf(
        Person(1, "Frank", 30, mockData_companyByName.get("collokia")),
        Person(2, "Domingo", 19),
        Person(3, "Mariana", 22, mockData_companyByName.get("collokia")),
        Person(4, "Lucia", 31, mockData_companyByName.get("bremeld")))
        .associateByTo(HashMap()) { it.id }

internal val mockData_validUsers = listOf(User("franky"), User("jdavidson"))
internal val mockData_validApiKeys = mapOf("apiKey12345" to mockData_validUsers[0], "apiKey54321" to mockData_validUsers[1])

