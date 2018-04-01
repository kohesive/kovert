package uy.kohesive.kovert.vertx.sample.services

import org.kodein.di.Kodein
import org.kodein.di.conf.global
import org.kodein.di.direct
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton

interface CompanyService {
    fun findCompanyByName(name: String): Company?
    fun findCompaniesByCountry(country: String): List<Company>
    fun upsertCompany(newCompany: Company): Unit
    fun listEmployeesOfCompany(name: String): List<Person>?
}

object KodeinCompanyService {
    val module = Kodein.Module {
        bind<CompanyService>() with singleton { MockCompanyService() }
    }
}

class MockCompanyService(val peopleService: PeopleService = Kodein.global.direct.instance()) : CompanyService {
    override fun findCompanyByName(name: String): Company? = mockData_companyByName.get(name.toLowerCase())
    override fun findCompaniesByCountry(country: String): List<Company> =
        mockData_companyByName.values.filter { it.country.equals(country, ignoreCase = true) }

    override fun upsertCompany(newCompany: Company): Unit {
        mockData_companyByName.put(newCompany.name.toLowerCase(), newCompany)
    }

    override fun listEmployeesOfCompany(name: String): List<Person>? {
        val company = findCompanyByName(name)
        if (company == null) return null
        return peopleService.findPeopleByCompany(company.name)
    }
}

data class Company(val name: String, val country: String)

