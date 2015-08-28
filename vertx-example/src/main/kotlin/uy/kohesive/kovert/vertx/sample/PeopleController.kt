package uy.kohesive.kovert.vertx.sample

import uy.kohesive.injekt.Injekt
import uy.kohesive.kovert.core.HttpErrorBadRequest
import uy.kohesive.kovert.core.HttpErrorNotFound

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
class PeopleController(val peopleService: PeopleService = Injekt.get(), val companyService: CompanyService = Injekt.get()) {
    public fun ApiKeySecured.getPersonById(id: Int): Person = peopleService.findPersonById(id) ?: throw HttpErrorNotFound()

    public fun ApiKeySecured.putPersonById(id: Int, person: Person): Person {
        if (id != person.id) {
            throw HttpErrorBadRequest()
        }
        peopleService.upsertPerson(person);
        return person
    }

    public fun ApiKeySecured.findPeopleNamedByName(name: String): List<Person> {
        return peopleService.findPersonsByName(name)
    }

    public fun ApiKeySecured.listPeopleEmployeedByCompany(company: String): List<Person> {
        return companyService.listEmployeesOfCompany(company) ?: throw HttpErrorNotFound()
    }
}





