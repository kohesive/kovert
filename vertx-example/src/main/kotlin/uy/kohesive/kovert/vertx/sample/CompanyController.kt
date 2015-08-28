package uy.kohesive.kovert.vertx.sample

import uy.klutter.core.common.whenNotNull
import uy.kohesive.injekt.Injekt
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
 * GET api/companies/query?name=xyz&country=abc
 *
 */
class CompanyController(val companyService: CompanyService = Injekt.get()) {
    public fun RestContext.getCompanyByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun RestContext.putCompanyByName(name: String, company: Company): Company {
        if (!name.equals(company.name, ignoreCase = true)) {
            throw HttpErrorBadRequest()
        }
        companyService.upsertCompany(company)
        return company
    }

    public fun RestContext.listCompanyByNameEmployees(name: String): List<Person> {
        return companyService.listEmployeesOfCompany(name) ?: throw HttpErrorNotFound()
    }

    public fun RestContext.findCompaniesNamedByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun RestContext.findCompaniesLocatedInCountry(country: String): List<Company> {
        val found = companyService.findCompaniesByCountry(country)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }

    public fun RestContext.getCompaniesQuery(name: String?, country: String?): Set<Company> {
        val byName: List<Company> = name.whenNotNull { companyService.findCompanyByName(name!!) }.whenNotNull { listOf(it) } ?: emptyList()
        val byCountry: List<Company> = country.whenNotNull { companyService.findCompaniesByCountry(country!!) } ?: emptyList()
        return (byName + byCountry).toSet()
    }
}


