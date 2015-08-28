package uy.kohesive.kovert.vertx.sample

import uy.kohesive.injekt.Injekt
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.vertx.InterceptDispatch

/**
 * This will create the follow routes (when bound at "api"):
 *
 * GET api/company/:name
 * PUT api/company/:name
 *
 * GET api/company/:name/employees
 *
 * GET api/companies/named/:name
 * GET api/companies/located/:country
 *
 */
class CompanyController(val companyService: CompanyService = Injekt.get()) {
    public fun RestContext.getByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun RestContext.putByName(name: String, company: Company): Company {
        if (!name.equals(company.name, ignoreCase = true)) {
            throw HttpErrorBadRequest()
        }
        companyService.upsertCompany(company)
        return company
    }

    public fun RestContext.listByNameEmployees(name: String): List<Person> {
        return companyService.listEmployeesOfCompany(name) ?: throw HttpErrorNotFound()
    }

    public fun RestContext.findNamedByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun RestContext.findLocatedInCountry(country: String): List<Company> {
        val found = companyService.findCompaniesByCountry(country)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }
}


