package uy.kohesive.kovert.vertx.sample.api

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.global.global
import com.github.salomonbrys.kodein.instance
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import uy.klutter.core.common.whenNotNull
import uy.kohesive.kovert.core.HttpErrorBadRequest
import uy.kohesive.kovert.core.HttpErrorNotFound
import uy.kohesive.kovert.vertx.sample.services.Company
import uy.kohesive.kovert.vertx.sample.services.CompanyService
import uy.kohesive.kovert.vertx.sample.services.Person

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
class CompanyRestController(val companyService: CompanyService = Kodein.global.instance()) {
    fun ApiKeySecured.getCompanyByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    fun ApiKeySecured.putCompanyByName(name: String, company: Company): Company {
        if (!name.equals(company.name, ignoreCase = true)) {
            throw HttpErrorBadRequest()
        }
        companyService.upsertCompany(company)
        return company
    }

    fun ApiKeySecured.listCompanyByNameEmployees(name: String): List<Person> {
        return companyService.listEmployeesOfCompany(name) ?: throw HttpErrorNotFound()
    }

    fun ApiKeySecured.findCompaniesNamedByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    fun ApiKeySecured.findCompaniesLocatedInCountry(country: String): List<Company> {
        val found = companyService.findCompaniesByCountry(country)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }

    fun ApiKeySecured.getCompaniesSearch(name: String?, country: String?): Promise<Set<Company>, Exception> {
        return task {
            val byName: List<Company> = name.whenNotNull { companyService.findCompanyByName(name!!) }.whenNotNull { listOf(it) } ?: emptyList()
            val byCountry: List<Company> = country.whenNotNull { companyService.findCompaniesByCountry(country!!) } ?: emptyList()
            (byName + byCountry).toSet()
        }
    }
}


