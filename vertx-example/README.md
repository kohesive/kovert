# Kovert Sample

This sample application uses:

* [Injekt](https://github.com/kohesive/injekt) for dependency injection of configuration and services
* [Kovenant](http://kovenant.komponents.nl) for promises
* [Klutter](https://github.com/klutter/klutter) for a mix of extension methods, and Typesafe Config loading

To test the application using Curl, you must provide an `Authorization` header for an imaginary API Key `apiKey12345` ... For example:

```
$ curl -i -X GET 'http://localhost:8080/api/companies/search?country=uruguay&name=collokia' -H "Authorization: apiKey12345"
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 80
Set-Cookie: vertx-web.session=5e98a12f-c019-4c36-8600-9b05bd578db5; Path=/

[{"name":"Collokia","country":"Uruguay"},{"name":"Bremeld","country":"Uruguay"}]
```


