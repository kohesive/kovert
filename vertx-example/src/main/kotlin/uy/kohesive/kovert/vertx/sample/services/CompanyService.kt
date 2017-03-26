package uy.kohesive.kovert.vertx.sample.services

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.conf.global
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton

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

class MockCompanyService(val peopleService: PeopleService = Kodein.global.instance()): CompanyService {
    override fun findCompanyByName(name: String): Company? = mockData_companyByName.get(name.toLowerCase())
    override fun findCompaniesByCountry(country: String): List<Company> = mockData_companyByName.values.filter { it.country.equals(country, ignoreCase = true) }
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

