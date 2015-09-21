package uy.kohesive.kovert.vertx.sample

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.async
import uy.klutter.core.common.whenNotNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.*
import uy.kohesive.kovert.core.HttpErrorBadRequest
import uy.kohesive.kovert.core.HttpErrorNotFound

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
 * GET api/companies/search?name=xyz&country=abc
 *
 */
class CompanyController(val companyService: CompanyService = Injekt.get()) {
    public fun ApiKeySecured.getCompanyByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun ApiKeySecured.putCompanyByName(name: String, company: Company): Company {
        if (!name.equals(company.name, ignoreCase = true)) {
            throw HttpErrorBadRequest()
        }
        companyService.upsertCompany(company)
        return company
    }

    public fun ApiKeySecured.listCompanyByNameEmployees(name: String): List<Person> {
        return companyService.listEmployeesOfCompany(name) ?: throw HttpErrorNotFound()
    }

    public fun ApiKeySecured.findCompaniesNamedByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun ApiKeySecured.findCompaniesLocatedInCountry(country: String): List<Company> {
        val found = companyService.findCompaniesByCountry(country)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }

    public fun ApiKeySecured.getCompaniesSearch(name: String?, country: String?): Promise<Set<Company>, Exception> {
        return async {
            val byName: List<Company> = name.whenNotNull { companyService.findCompanyByName(name!!) }.whenNotNull { listOf(it) } ?: emptyList()
            val byCountry: List<Company> = country.whenNotNull { companyService.findCompaniesByCountry(country!!) } ?: emptyList()
            (byName + byCountry).toSet()
        }
    }
}


