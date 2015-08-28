package uy.kohesive.kovert.vertx.sample

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.config.typesafe.KonfigModule
import uy.kohesive.injekt.config.typesafe.KonfigRegistrar

class PeopleService {
    companion object : InjektModule {
        override fun InjektRegistrar.registerInjectables() {
            addSingletonFactory { PeopleService() }
        }
    }

    public fun findPersonById(id: Int): Person? = mockData_peopleById.get(id)
    public fun findPersonsByName(name: String): List<Person> = mockData_peopleById.values().filter { it.name.equals(name, ignoreCase = true) }
    public fun findPeopleByCompany(company: String): List<Person> = mockData_peopleById.values().filter { it.company?.name?.equals(company, ignoreCase = true) ?: false }
    public fun upsertPerson(newPerson: Person): Unit {
        // ignoring the company part, this is a silly demo afterall
        mockData_peopleById.put(newPerson.id, newPerson)
    }
}

class CompanyService(val peopleService: PeopleService = Injekt.get()) {
    companion object : InjektModule {
        override fun InjektRegistrar.registerInjectables() {
            addSingletonFactory { CompanyService() }
        }
    }

    public fun findCompanyByName(name: String): Company? = mockData_companyByName.get(name.toLowerCase())
    public fun findCompaniesByCountry(country: String): List<Company> = mockData_companyByName.values().filter { it.country.equals(country, ignoreCase = true) }
    public fun upsertCompany(newCompany: Company): Unit {
        mockData_companyByName.put(newCompany.name.toLowerCase(), newCompany)
    }

    public fun listEmployeesOfCompany(name: String): List<Person>? {
        val company = findCompanyByName(name)
        if (company == null) return null
        return peopleService.findPeopleByCompany(company.name)
    }
}

data class Company(val name: String, val country: String)
data class Person(val id: Int, val name: String, val age: Int, val company: Company? = null)

