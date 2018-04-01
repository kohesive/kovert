package uy.kohesive.kovert.vertx.sample.api

import org.kodein.di.Kodein
import org.kodein.di.conf.global
import org.kodein.di.direct
import org.kodein.di.generic.instance
import uy.kohesive.kovert.core.HttpErrorBadRequest
import uy.kohesive.kovert.core.HttpErrorNotFound
import uy.kohesive.kovert.vertx.sample.services.CompanyService
import uy.kohesive.kovert.vertx.sample.services.PeopleService
import uy.kohesive.kovert.vertx.sample.services.Person

/**
 * This will create the follow routes (when bound at "api"):
 *
 * GET api/person/:id
 * PUT api/person/:id
 *
 * GET api/people/named/:name
 * GET api/people/employeed/:company
 *
 */
class PeopleRestController(
    val peopleService: PeopleService = Kodein.global.direct.instance(),
    val companyService: CompanyService = Kodein.global.direct.instance()
) {
    fun ApiKeySecured.getPersonById(id: Int): Person = peopleService.findPersonById(id) ?: throw HttpErrorNotFound()

    fun ApiKeySecured.putPersonById(id: Int, person: Person): Person {
        if (id != person.id) {
            throw HttpErrorBadRequest()
        }
        peopleService.upsertPerson(person);
        return person
    }

    fun ApiKeySecured.findPeopleNamedByName(name: String): List<Person> {
        return peopleService.findPersonsByName(name)
    }

    fun ApiKeySecured.listPeopleEmployeedByCompany(company: String): List<Person> {
        return companyService.listEmployeesOfCompany(company) ?: throw HttpErrorNotFound()
    }
}





