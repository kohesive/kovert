package uy.kohesive.kovert.vertx.sample

internal val mockData_companyByName = hashMapOf(*listOf(
        Company("Collokia", "Uruguay"),
        Company("Bremeld", "Uruguay"),
        Company("Jetbrains", "Czech Republic")).map { it.name.toLowerCase() to it}.toTypedArray())

internal val mockData_peopleById = hashMapOf(*listOf(
        Person(1, "Frank", 30, mockData_companyByName.get("collokia")),
        Person(2, "Domingo", 19),
        Person(3, "Mariana", 22, mockData_companyByName.get("collokia")),
        Person(4, "Lucia", 31, mockData_companyByName.get("bremeld"))).map { it.id to it }.toTypedArray())


internal val mockData_validApiKeys = mapOf("apiKey12345" to User(1, "franky"), "apiKey54321" to User(2, "jdavidson"))