package uy.kohesive.kovert.vertx.sample.services

import uy.kohesive.injekt.api.*

interface PeopleService {
    fun findPersonById(id: Int): Person?
    fun findPersonsByName(name: String): List<Person>
    fun findPeopleByCompany(company: String): List<Person>
    fun upsertPerson(newPerson: Person): Unit
}

data class Person(val id: Int, val name: String, val age: Int, val company: Company? = null)

class MockPeopleService: PeopleService {
    companion object Injektables: InjektModule {
        override fun InjektRegistrar.registerInjectables() {
            addSingletonFactory<PeopleService> { MockPeopleService() }
        }
    }

    override fun findPersonById(id: Int): Person? = mockData_peopleById.get(id)
    override fun findPersonsByName(name: String): List<Person> = mockData_peopleById.values.filter { it.name.equals(name, ignoreCase = true) }
    override fun findPeopleByCompany(company: String): List<Person> = mockData_peopleById.values.filter { it.company?.name?.equals(company, ignoreCase = true) ?: false }
    override fun upsertPerson(newPerson: Person): Unit {
        // ignoring the company part, this is a silly demo afterall
        mockData_peopleById.put(newPerson.id, newPerson)
    }
}
